package com.example.track

import android.util.Log
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun BarcodeCameraPreview(modifier: Modifier, onBarcode: (String) -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val barcodeCallback = rememberUpdatedState(onBarcode)
    val errorCallback = rememberUpdatedState(onError)
    val previewView = remember(context) {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
    DisposableEffect(owner, previewView) {
        val main = ContextCompat.getMainExecutor(context)
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var analyzer: RetailBarcodeAnalyzer? = null
        providerFuture.addListener({
            if (!disposed) {
                try {
                    val cameraProvider = providerFuture.get()
                    provider = cameraProvider
                    val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E).build())
                    analyzer = RetailBarcodeAnalyzer(scanner, main,
                        { barcodeCallback.value(it) }, { errorCallback.value() })
                    analysis.setAnalyzer(executor, requireNotNull(analyzer))
                    cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (error: Exception) {
                    Log.e("TrackCamera", "Could not start barcode camera", error)
                    errorCallback.value()
                }
            }
        }, main)
        onDispose {
            disposed = true
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            analyzer?.close()
            executor.shutdown()
        }
    }
}

/** One outstanding frame; the ML Kit task owns that frame until completion. */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
private class RetailBarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val main: Executor,
    private val onBarcode: (String) -> Unit,
    private val onError: () -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val stopped = AtomicBoolean(false)
    private val detected = AtomicBoolean(false)
    private val lock = Any()
    private var processing = false
    private val direct = Executor { it.run() }
    private val stability = BarcodeStabilityTracker()

    override fun analyze(image: ImageProxy) {
        synchronized(lock) {
            if (stopped.get() || detected.get() || processing) {
                image.close()
                return
            }
            processing = true
        }
        try {
            val mediaImage = image.image
            if (mediaImage == null) {
                finish(image)
                return
            }
            scanner.process(InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees))
                .addOnCompleteListener(direct) { task ->
                    try {
                        if (!stopped.get()) {
                            if (task.isSuccessful) {
                                val code = task.result.firstNotNullOfOrNull { barcode ->
                                    normalizeRetailBarcode(barcode.rawValue)
                                }
                                val accepted = stability.observe(code, SystemClock.elapsedRealtime())
                                if (accepted != null && detected.compareAndSet(false, true)) {
                                    main.execute { if (!stopped.get()) onBarcode(accepted) }
                                }
                            } else {
                                fail(task.exception)
                            }
                        }
                    } finally {
                        finish(image)
                    }
                }
        } catch (error: Exception) {
            fail(error)
            finish(image)
        }
    }

    private fun fail(error: Exception?) {
        if (!stopped.get() && detected.compareAndSet(false, true)) {
            Log.e("TrackCamera", "Barcode analyzer unavailable", error)
            main.execute { if (!stopped.get()) onError() }
        }
    }

    private fun finish(image: ImageProxy) {
        image.close()
        synchronized(lock) {
            processing = false
            if (stopped.get()) scanner.close()
        }
    }

    override fun close() {
        synchronized(lock) {
            if (stopped.compareAndSet(false, true) && !processing) scanner.close()
        }
    }
}
