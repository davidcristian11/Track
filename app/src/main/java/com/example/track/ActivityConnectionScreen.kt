package com.example.track

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.track.ui.theme.TrackTheme
import kotlinx.coroutines.launch

@Composable
fun ActivityConnectionScreen(onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Stitch specifies only the disconnected invitation. Never simulate authorization
    // or alter Activity's demo metrics, goals, or manually logged workouts.
    val showMockNotice: () -> Unit = {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar("Mock only — no health data is connected or accessed.")
        }
    }

    // TrackApp already supplies system-bar insets.
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ConnectionTopBar(onBack = onBack, onSettings = showMockNotice)
            BoxWithConstraints(Modifier.weight(1f)) {
                val illustrationTopSpace = (maxHeight * 0.26f).coerceIn(48.dp, 156.dp)
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(illustrationTopSpace))
                    Box(
                        modifier = Modifier.size(96.dp)
                            .shadow(4.dp, CircleShape, ambientColor = Color(0x0D5F7A61), spotColor = Color(0x0D5F7A61))
                            .background(Color(0xFFEFEEEB), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, Modifier.size(48.dp), tint = Color(0xFF737971))
                    }
                    Spacer(Modifier.height(32.dp))
                    Text("Steps", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No step data available", style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Connect health data to automatically track steps from your phone or compatible fitness apps.",
                        modifier = Modifier.widthIn(max = 320.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = showMockNotice,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = CircleShape,
                    ) {
                        Text("Connect health data", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF0F0F0), contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text("Not now", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(32.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Shield, null, Modifier.size(16.dp), tint = Color(0xFF737971))
                        Text(
                            "You choose which health data the app can access.",
                            style = MaterialTheme.typography.bodyMedium, color = Color(0xFF737971),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun ConnectionTopBar(onBack: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
        }
        Column(Modifier.weight(1f)) {
            Text("Activity", style = MaterialTheme.typography.titleLarge)
            Text(
                "Wednesday, September 2", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, "Connection settings", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 835)
@Composable
private fun ActivityConnectionScreenPreview() {
    TrackTheme { ActivityConnectionScreen(onBack = {}) }
}
