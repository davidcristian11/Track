package com.example.track

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.track.ui.theme.TrackTheme
import java.time.LocalDate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role

private val ProgressChartBackground = Color(0xFFFBF9F6)

@Composable
fun ProgressScreen(
    onAvatarClick: () -> Unit,
    today: LocalDate = TrackDateProvider.today(),
    history: WeightHistoryState = WeightHistoryState(loading = false),
    goals: TrackGoals = TrackGoals(),
    onLogWeight: suspend (Double) -> Boolean = { false },
    measurements: MeasurementHistoryState = MeasurementHistoryState(loading = false),
    onSaveMeasurements: suspend (BodyMeasurement, LocalDate?) -> MeasurementSaveResult = { _, _ -> MeasurementSaveResult.Failed },
    onDeleteMeasurements: suspend (LocalDate) -> Boolean = { false },
    photos: ProgressPhotoHistory = ProgressPhotoHistory(loading = false),
    photoEditor: ProgressPhotoEditState = ProgressPhotoEditState(),
    photoActions: ProgressPhotoActions = ProgressPhotoActions(),
) {
    var addingPhoto by rememberSaveable { mutableStateOf(false) }
    var photoHistory by rememberSaveable { mutableStateOf(false) }
    var viewedPhoto by rememberSaveable { mutableStateOf<Long?>(null) }
    ProgressPhotoDialogs(photos, photoEditor, photoActions, today, addingPhoto, { addingPhoto = false },
        photoHistory, { photoHistory = false }, viewedPhoto, { viewedPhoto = it })
    var selectedRangeName by rememberSaveable {
        mutableStateOf(ProgressRange.ThirtyDays.name)
    }
    val selectedRange = ProgressRange.valueOf(selectedRangeName)
    val entries = history.entries.inRange(selectedRange, today)
    val latest = history.entries.latestWeight(today)
    val todayEntry = history.entries.firstOrNull { it.day == today }
    var loggingWeight by rememberSaveable { mutableStateOf(false) }
    if (loggingWeight) {
        WeightLogDialog(today, todayEntry, onDismiss = { loggingWeight = false }, onSave = onLogWeight)
    }

    var showingHistory by rememberSaveable { mutableStateOf(false) }
    var editorDay by rememberSaveable { mutableStateOf<String?>(null) }
    var loggingMeasurements by rememberSaveable { mutableStateOf(false) }
    if (showingHistory) {
        MeasurementHistoryDialog(measurements, selectedRange, today, { showingHistory = false },
            onLog = { editorDay = null; loggingMeasurements = true },
            onEdit = { editorDay = it.day.toDayKey(); loggingMeasurements = true })
    }
    if (loggingMeasurements && !measurements.loading && !measurements.error) {
        MeasurementEditorDialog(today, measurements.entries.firstOrNull { it.day.toDayKey() == editorDay },
            measurements.entries, { loggingMeasurements = false }, onSaveMeasurements, onDeleteMeasurements)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 16.dp,
            end = 24.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item { ProgressHeader(onAvatarClick = onAvatarClick) }
        item {
            TimeRangeSelector(
                selectedRange = selectedRange,
                onRangeSelected = { selectedRangeName = it.name },
            )
        }
        item {
            WeightProgressCard(history, entries, latest, todayEntry, selectedRange, today,
                onLogWeight = { loggingWeight = true })
        }
        item { ProgressPhotosSection(photos, today, photoEditor.busy, photoActions.load,
            onAdd = { addingPhoto = true }, onHistory = { photoHistory = true }, onOpen = { viewedPhoto = it }) }
        item { MeasurementsCard(measurements, selectedRange, today,
            onLog = { editorDay = null; loggingMeasurements = true }, onHistory = { showingHistory = true }) }
        item { ProgressSummaryGrid(entries, selectedRange, goals, history.loading || history.error) }
    }
}

@Composable
private fun ProgressHeader(onAvatarClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "Profile",
                modifier = Modifier.size(23.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "Progress",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TimeRangeSelector(
    selectedRange: ProgressRange,
    onRangeSelected: (ProgressRange) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRange.entries.forEach { range ->
                val selected = range == selectedRange
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .selectable(selected = selected, role = Role.Tab, onClick = { onRangeSelected(range) }),
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = range.label,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightProgressCard(
    history: WeightHistoryState, entries: List<WeightEntry>, latest: WeightEntry?,
    todayEntry: WeightEntry?, range: ProgressRange, today: LocalDate, onLogWeight: () -> Unit,
) {
    ProgressCard {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Current Weight", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(latest?.let { formatWeight(it.weightKg) } ?: "—",
                    fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp))
                Text("kg", modifier = Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (latest != null) {
                Text("Last logged · ${formatWorkoutDate(latest.day, today)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary) {
                Text("${formatWeightChange(weightChange(entries))} · ${range.label} change",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(16.dp))
            WeightChart(entries, range.startDay(today), today, when {
                history.loading -> "Loading weight…"
                history.error -> "Could not load weight history"
                latest == null -> "No weight logged yet"
                else -> "No entries in this range"
            })
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLogWeight, enabled = !history.loading && !history.error,
                modifier = Modifier.fillMaxWidth()) {
                Text(if (todayEntry == null) "Log weight" else "Update weight")
            }
        }
    }
}

@Composable
private fun WeightChart(entries: List<WeightEntry>, start: LocalDate, end: LocalDate, emptyMessage: String) {
    val scale = weightChartScale(entries, start, end)
    Box(modifier = Modifier.fillMaxWidth().height(192.dp)
        .clip(RoundedCornerShape(12.dp)).background(ProgressChartBackground)
        .semantics {
            contentDescription = if (entries.isEmpty()) emptyMessage else
                "Weight chart, ${entries.size} logged days, ${formatWeightChange(weightChange(entries))} change"
        }) {
        if (entries.isEmpty()) {
            Text(emptyMessage, modifier = Modifier.align(Alignment.Center).padding(16.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val lineColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize().padding(start = 48.dp, top = 20.dp, end = 12.dp, bottom = 40.dp)) {
                val points = scale.points.map { Offset(it.x * size.width, it.y * size.height) }
                if (points.size >= 2) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(points.last().x, size.height)
                        lineTo(points.first().x, size.height)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.16f), Color.Transparent)))
                    drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                }
                points.forEach { drawCircle(lineColor, radius = 4.dp.toPx(), center = it) }
            }
            Column(modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp, bottom = 20.dp)
                .height(132.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(scale.high, scale.low).forEach {
                    Text(formatWeight(it), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            Row(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = 48.dp, end = 12.dp, bottom = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(start, end).forEach {
                    Text(weightDateLabel(it), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun ProgressSummaryGrid(
    entries: List<WeightEntry>, range: ProgressRange, goals: TrackGoals, unavailable: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "${range.label} Weight Summary",
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProgressAverageCard(
                icon = Icons.Outlined.Flag,
                label = "Target Weight",
                value = formatWeight(goals.targetWeightKg.toDouble()),
                unit = "kg",
                modifier = Modifier.weight(1f),
            )
            ProgressAverageCard(
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                label = "Change",
                value = formatWeightChange(weightChange(entries)),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProgressAverageCard(
                icon = Icons.Outlined.CalendarToday,
                label = "Days Logged",
                value = if (unavailable) "—" else entries.size.toString(),
                modifier = Modifier.weight(1f),
            )
            ProgressAverageCard(
                icon = Icons.Outlined.MonitorWeight,
                label = "Average Weight",
                value = if (entries.isEmpty()) "—" else formatWeight(entries.map { it.weightKg }.average()),
                unit = "kg",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProgressAverageCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
) {
    Card(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (unit != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = unit,
                            modifier = Modifier.padding(bottom = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        content()
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1398)
@Composable
private fun ProgressScreenPreview() {
    TrackTheme { ProgressScreen(onAvatarClick = {}) }
}
