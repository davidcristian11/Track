package com.example.track

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.track.ui.theme.TrackTheme

private val ScannerNeutral = Color(0xFFF0F0F0)
private val ScannerMuted = Color(0xFF59605C)
private val ScannerCharcoal = Color(0xFF2D2D2D)

@Composable
fun BarcodeScannerScreen(
    initialMeal: MealContext,
    onBack: () -> Unit,
    onAddToMeal: (MealContext, FoodDefinition, Int) -> Unit,
    state: ScannerState = ScannerState.Scanning,
    onBarcode: (String) -> Unit = {},
    onScanAgain: () -> Unit = {},
    onRetryLookup: () -> Unit = {},
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = context.hasCameraPermission()
        resumed = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumed = false }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        permanentlyDenied = !allowed && context.findActivity()?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true
    }
    fun requestCamera() {
        requested = true
        permission.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) { if (state == ScannerState.Scanning && !granted && !requested) requestCamera() }
    val food = (state as? ScannerState.Found)?.food
    var amountText by rememberSaveable(food?.id) { mutableStateOf(food?.defaultAmount?.toString().orEmpty()) }
    var mealName by rememberSaveable(initialMeal) { mutableStateOf(initialMeal.name) }
    val meal = MealContext.fromRoute(mealName)
    val amount = amountText.toIntOrNull()?.takeIf { it in 1..MaxFoodAmount }
    val nutrition = food?.takeIf { it.isLoggable }?.nutritionFor(amount ?: 0)

    // Removing Preview on pause/detection also releases analysis and ML Kit resources.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().imePadding().background(ScannerCharcoal)) {
        if (granted && resumed && state == ScannerState.Scanning && !cameraError) {
            BarcodeCameraPreview(Modifier.fillMaxSize(), onBarcode, { cameraError = true })
        }
        ScannerTopBar(onBack)
        if (state == ScannerState.Scanning && granted && !cameraError) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(180.dp).border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp)))
                Text("Point the camera at a food barcode", color = Color.White,
                    modifier = Modifier.padding(top = 24.dp))
            }
        }
        if (food == null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Color.White).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state == ScannerState.Scanning && !granted -> {
                        Text("Camera access is needed to scan barcodes")
                        if (permanentlyDenied) {
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")))
                            }) { Text("Open Settings") }
                        } else {
                            Button(onClick = { requestCamera() }) { Text("Allow camera") }
                        }
                    }
                    state == ScannerState.Scanning && cameraError -> {
                        Text("Camera unavailable")
                        TextButton(onClick = { cameraError = false }) { Text("Try camera again") }
                    }
                    state is ScannerState.LookingUp -> {
                        CircularProgressIndicator(Modifier.size(28.dp))
                        Text("Looking up product…")
                    }
                    state is ScannerState.NotFound -> Text("Product not found")
                    state is ScannerState.Unavailable -> {
                        Text("Couldn't look up this product")
                        Text("Online lookup unavailable", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onRetryLookup) { Text("Retry lookup") }
                    }
                }
                if (state != ScannerState.Scanning && state !is ScannerState.LookingUp) {
                    TextButton(onClick = onScanAgain) { Text("Try scanning again") }
                }
                TextButton(onClick = onBack) { Text("Search by name") }
            }
        }
        if (food != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = (maxHeight - 88.dp).coerceAtLeast(0.dp))
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.offset(y = (-16).dp).width(48.dp).height(6.dp).background(Color(0xFFE4E2DF), CircleShape))
                }
                Spacer(Modifier.height(16.dp))
                ScannedProductHeader(food)
                Spacer(Modifier.height(32.dp))
                if (nutrition == null) {
                    Text("Nutrition data incomplete", color = ScannerMuted)
                } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ScannerNutrient("Calories", nutrition.calories.toString(), "kcal", Modifier.weight(1f))
                        ScannerNutrient("Protein", formatNutrient(nutrition.proteinGrams), "g", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ScannerNutrient("Carbs", formatNutrient(nutrition.carbsGrams), "g", Modifier.weight(1f))
                        ScannerNutrient("Fat", formatNutrient(nutrition.fatGrams), "g", Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(32.dp))
                ScannerAmountField(
                    unit = food.unit,
                    value = amountText,
                    onValueChange = { value ->
                        if (value.length <= 5 && value.all { it in '0'..'9' }) amountText = value
                    },
                )
                if (amount == null) {
                    Text(
                        text = "Enter 1–$MaxFoodAmount ${food.unit.symbol}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ScannerMealField(meal, { mealName = it.name }, Modifier.weight(1f))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScannerFieldLabel("Time")
                        Row(
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                                .background(ScannerNeutral, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("13:20", style = MaterialTheme.typography.bodyLarge, color = ScannerCharcoal)
                            Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = ScannerCharcoal)
                        }
                    }
                }
                Spacer(Modifier.height(48.dp))
                Button(
                    onClick = { amount?.let { onAddToMeal(meal, food, it) } },
                    enabled = amount != null && food.isLoggable,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                ) {
                    Text("Add to ${meal.label}", style = MaterialTheme.typography.titleLarge)
                }
                TextButton(onClick = onScanAgain) { Text("Try scanning again") }
                TextButton(onClick = onBack) { Text("Search by name") }
            }
        }
    }
}

@Composable
private fun ScannerTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f)).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            // A 40-dp visual disc inside a full 48-dp touch target.
            modifier = Modifier.size(48.dp).drawBehind { drawCircle(Color.White, radius = 20.dp.toPx()) },
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = ScannerCharcoal)
        }
        Spacer(Modifier.width(8.dp))
        Text("Scanner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = ScannerCharcoal)
    }
}

@Composable
private fun ScannedProductHeader(food: FoodDefinition) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "PRODUCT SCANNED", style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(food.name, style = MaterialTheme.typography.headlineMedium, color = ScannerCharcoal)
            Spacer(Modifier.height(4.dp))
            Text(listOfNotNull(food.brand, food.servingLabel, "Open Food Facts").joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = ScannerMuted)
        }
        Box(
            modifier = Modifier.size(64.dp).background(ScannerNeutral, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // Intentional local product placeholder; no image loader or remote photograph.
            Icon(Icons.Outlined.LocalDrink, null, Modifier.size(32.dp), tint = ScannerMuted)
        }
    }
}

@Composable
private fun ScannerNutrient(label: String, value: String, unit: String, modifier: Modifier) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background, RoundedCornerShape(24.dp)).padding(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ScannerMuted)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, Modifier.alignByBaseline(), style = MaterialTheme.typography.headlineMedium, color = ScannerCharcoal)
            Text(unit, Modifier.alignByBaseline(), style = MaterialTheme.typography.bodyMedium, color = ScannerMuted)
        }
    }
}

@Composable
private fun ScannerFieldLabel(label: String) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ScannerAmountField(value: String, unit: FoodUnit, onValueChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScannerFieldLabel("Amount")
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(56.dp)
                .background(ScannerNeutral, RoundedCornerShape(16.dp))
                .semantics { contentDescription = "Scanned amount, ${unit.symbol}" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = ScannerCharcoal),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { innerTextField() }
                    Text(unit.symbol, style = MaterialTheme.typography.bodyMedium, color = ScannerMuted)
                }
            },
        )
    }
}

@Composable
private fun ScannerMealField(meal: MealContext, onMealChange: (MealContext) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScannerFieldLabel("Meal")
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
                    .background(ScannerNeutral).clickable(role = Role.Button) { expanded = true }
                    .semantics { contentDescription = "Choose scanner meal" }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(meal.label, style = MaterialTheme.typography.bodyLarge, color = ScannerCharcoal)
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = ScannerMuted)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MealContext.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onMealChange(option); expanded = false },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 835)
@Composable
private fun BarcodeScannerScreenPreview() {
    TrackTheme { BarcodeScannerScreen(MealContext.LUNCH, {}, { _, _, _ -> }) }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
