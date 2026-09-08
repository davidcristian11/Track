package com.example.track

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import java.time.LocalDate
import java.time.LocalTime

private val WorkoutNeutral = Color(0xFFF0F0F0)

internal val WorkoutType.icon: ImageVector
    get() = when (this) {
        WorkoutType.Running -> Icons.AutoMirrored.Filled.DirectionsRun
        WorkoutType.Cycling -> Icons.AutoMirrored.Filled.DirectionsBike
        WorkoutType.Walking -> Icons.AutoMirrored.Filled.DirectionsWalk
        else -> Icons.Filled.FitnessCenter
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkoutScreen(
    day: LocalDate,
    today: LocalDate,
    onBack: () -> Unit,
    onSaveWorkout: (WorkoutInput) -> Unit,
    existingWorkout: LoggedWorkout? = null,
) {
    // Key by the edited row, never by the mutable dashboard day. Saveable primitives
    // also preserve an explicitly chosen date/time across activity recreation.
    var chosenDayKey by rememberSaveable(existingWorkout?.id) { mutableStateOf(day.toDayKey()) }
    var startTime by rememberSaveable(existingWorkout?.id) {
        mutableStateOf(existingWorkout?.startTime ?: formatWorkoutTime(LocalTime.now()))
    }
    var workoutTypeName by rememberSaveable(existingWorkout?.id) { mutableStateOf(existingWorkout?.type?.name ?: WorkoutType.Strength.name) }
    var durationText by rememberSaveable(existingWorkout?.id) { mutableStateOf((existingWorkout?.durationMinutes ?: 45).toString()) }
    var notes by rememberSaveable(existingWorkout?.id) { mutableStateOf(existingWorkout?.notes.orEmpty()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val duration = durationText.toIntOrNull() ?: 0
    val workoutType = WorkoutType.valueOf(workoutTypeName)
    val chosenDay = chosenDayKey.toTrackDay()
    val input = WorkoutInput(chosenDay, workoutType, duration, startTime, notes)
    val calories = if (isValidWorkoutDuration(duration)) estimateWorkoutCalories(workoutType, duration) else null

    if (showDatePicker) {
        val selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    isAllowedWorkoutDate(utcTimeMillis.toWorkoutPickerDay(), today)
                override fun isSelectableYear(year: Int) = year <= today.year
            }
        }
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = chosenDay.toWorkoutPickerMillis(),
            yearRange = 1..maxOf(today.year, chosenDay.year),
            selectableDates = selectableDates,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                val picked = picker.selectedDateMillis?.toWorkoutPickerDay()
                TextButton(enabled = picked != null && isAllowedWorkoutDate(picked, today), onClick = {
                    if (picked != null && isAllowedWorkoutDate(picked, today)) {
                        chosenDayKey = picked.toDayKey()
                        showDatePicker = false
                    }
                }) { Text("Set date") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }
    if (showTimePicker) {
        val initialTime = LocalTime.parse(startTime)
        val picker = rememberTimePickerState(initialTime.hour, initialTime.minute,
            android.text.format.DateFormat.is24HourFormat(LocalContext.current))
        var keyboardMode by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Start time") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (keyboardMode) TimeInput(picker) else TimePicker(picker)
                    TextButton(onClick = { keyboardMode = !keyboardMode }) {
                        Text(if (keyboardMode) "Use clock" else "Enter time")
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                startTime = formatWorkoutTime(LocalTime.of(picker.hour, picker.minute))
                showTimePicker = false
            }) { Text("Set time") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        AddWorkoutTopBar(onBack = onBack, title = if (existingWorkout == null) "Add Workout" else "Edit Workout")
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ActivityTypeSelector(workoutType) { workoutTypeName = it.name }
            DateAndTimeRow(formatWorkoutDate(chosenDay, today), startTime,
                onDateClick = { showDatePicker = true }, onTimeClick = { showTimePicker = true })
            if (!isAllowedWorkoutDate(chosenDay, today)) {
                Text("Choose today or a past date", color = MaterialTheme.colorScheme.error)
            }
            DurationCard(durationText, onDurationChanged = { durationText = it })
            Text(
                text = "Estimated calories: ${calories?.let { "~$it kcal" } ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NotesField(notes = notes, onNotesChanged = { notes = it })
        }
        Button(
            onClick = { if (input.isValid(today)) onSaveWorkout(input) },
            enabled = input.isValid(today),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().height(60.dp),
            shape = CircleShape,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (existingWorkout == null) "Save Workout" else "Save Changes",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AddWorkoutTopBar(onBack: () -> Unit, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun ActivityTypeSelector(
    selectedType: WorkoutType,
    onTypeSelected: (WorkoutType) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Activity Type")
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clickable(role = Role.DropdownList) { expanded = true }
                    .semantics { contentDescription = "Choose activity type" },
                shape = RoundedCornerShape(16.dp),
                color = WorkoutNeutral,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp,
                    ) {
                        Icon(
                            imageVector = selectedType.icon,
                            contentDescription = null,
                            modifier = Modifier.padding(9.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = selectedType.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.88f).heightIn(max = 360.dp),
            ) {
                WorkoutType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.label) },
                        onClick = {
                            onTypeSelected(type)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DateAndTimeRow(date: String, startTime: String, onDateClick: () -> Unit, onTimeClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PickerField(
            label = "Date",
            value = date,
            icon = Icons.Outlined.CalendarToday,
            onClick = onDateClick,
            modifier = Modifier.weight(1f),
        )
        PickerField(
            label = "Start Time",
            value = startTime,
            icon = Icons.Outlined.Schedule,
            onClick = onTimeClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FieldLabel(label)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "Change $label" },
            shape = RoundedCornerShape(16.dp),
            color = WorkoutNeutral,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DurationCard(durationText: String, onDurationChanged: (String) -> Unit) {
    val duration = durationText.toIntOrNull() ?: 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = durationText,
                onValueChange = onDurationChanged,
                label = { Text("Duration (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                singleLine = true,
                isError = !isValidWorkoutDuration(duration),
                supportingText = { if (!isValidWorkoutDuration(duration)) Text("Enter 1 to 1440 minutes") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                listOf(30, 45, 60).forEach { preset ->
                    DurationChip(preset, duration == preset) { onDurationChanged(preset.toString()) }
                }
            }
        }
    }
}

@Composable
private fun DurationChip(
    value: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(56.dp)
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else WorkoutNeutral,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun NotesField(notes: String, onNotesChanged: (String) -> Unit) {
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    // Repeat relocation as the IME animates and the scroll viewport shrinks.
    LaunchedEffect(focused, imeBottom) {
        if (focused) bringIntoView.bringIntoView()
    }
    TextField(
        value = notes,
        onValueChange = onNotesChanged,
        label = { Text("Notes (Optional)") },
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView)
            .onFocusChanged { focused = it.isFocused },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(16.dp),
        minLines = 2,
        maxLines = 4,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = WorkoutNeutral,
            unfocusedContainerColor = WorkoutNeutral,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.3.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun AddWorkoutScreenPreview() {
    TrackTheme {
        AddWorkoutScreen(
            day = TrackDemoBaseline.referenceDay,
            today = TrackDemoBaseline.referenceDay,
            onBack = {},
            onSaveWorkout = {},
        )
    }
}
