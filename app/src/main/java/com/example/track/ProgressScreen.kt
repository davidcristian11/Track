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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.EggAlt
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.track.ui.theme.TrackTheme

private val ProgressNeutral = Color(0xFFF0F0F0)
private val ProgressChartBackground = Color(0xFFFBF9F6)
private val ProgressPhotoSage = Color(0xFFB9C6BA)
private val ProgressPhotoStone = Color(0xFFD7D5CF)

private enum class ProgressRange(val label: String) {
    SevenDays("7D"),
    ThirtyDays("30D"),
    ThreeMonths("3M"),
    SixMonths("6M"),
    OneYear("1Y"),
}

private data class ProgressRangeData(
    val points: List<Float>,
    val trend: String,
    val labels: List<String>,
)

private fun ProgressRange.data(): ProgressRangeData = when (this) {
    ProgressRange.SevenDays -> ProgressRangeData(
        points = listOf(73.0f, 72.9f, 73.0f, 72.7f, 72.6f, 72.5f, 72.4f),
        trend = "-0.6 kg this week",
        labels = listOf("Aug 25", "Aug 28", "Sep 1"),
    )
    ProgressRange.ThirtyDays -> ProgressRangeData(
        points = listOf(74.2f, 74.0f, 74.1f, 73.7f, 72.8f, 72.6f, 72.4f),
        trend = "-1.8 kg since Aug 1",
        labels = listOf("Aug 1", "Aug 15", "Aug 30"),
    )
    ProgressRange.ThreeMonths -> ProgressRangeData(
        points = listOf(76.0f, 75.7f, 75.8f, 75.1f, 74.8f, 74.2f, 73.8f, 73.0f, 72.4f),
        trend = "-3.6 kg in 3 months",
        labels = listOf("Jun", "Jul", "Sep"),
    )
    ProgressRange.SixMonths -> ProgressRangeData(
        points = listOf(78.1f, 77.8f, 77.3f, 76.9f, 76.1f, 75.6f, 74.4f, 73.5f, 72.4f),
        trend = "-5.7 kg in 6 months",
        labels = listOf("Mar", "Jun", "Sep"),
    )
    ProgressRange.OneYear -> ProgressRangeData(
        points = listOf(81.0f, 80.2f, 79.5f, 78.8f, 77.2f, 76.1f, 74.9f, 73.6f, 72.4f),
        trend = "-8.6 kg this year",
        labels = listOf("Sep '25", "Mar", "Sep '26"),
    )
}

@Composable
fun ProgressScreen(onAvatarClick: () -> Unit) {
    var selectedRangeName by rememberSaveable {
        mutableStateOf(ProgressRange.ThirtyDays.name)
    }
    val selectedRange = ProgressRange.valueOf(selectedRangeName)
    val rangeData = selectedRange.data()

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
        item { WeightProgressCard(data = rangeData) }
        item { ProgressPhotosSection() }
        item { MeasurementsCard() }
        item { ProgressAveragesGrid() }
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
                        .clickable { onRangeSelected(range) },
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
private fun WeightProgressCard(data: ProgressRangeData) {
    ProgressCard {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Current Weight",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "72.4",
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "kg",
                    modifier = Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = data.trend,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            WeightChart(
                points = data.points,
                labels = data.labels,
            )
        }
    }
}

@Composable
private fun WeightChart(
    points: List<Float>,
    labels: List<String>,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(192.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ProgressChartBackground),
    ) {
        val lineColor = MaterialTheme.colorScheme.primary
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 0.dp, top = 20.dp, end = 0.dp, bottom = 40.dp),
        ) {
            if (points.size < 2) return@Canvas

            val minPoint = points.min()
            val maxPoint = points.max()
            val range = (maxPoint - minPoint).coerceAtLeast(0.5f)
            val usableHeight = size.height * 0.56f
            val topInset = size.height * 0.17f
            val chartPoints = points.mapIndexed { index, value ->
                Offset(
                    x = size.width * index / points.lastIndex,
                    y = topInset + ((maxPoint - value) / range) * usableHeight,
                )
            }

            val linePath = smoothChartPath(chartPoints)
            val fillPath = smoothChartPath(chartPoints).apply {
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.16f),
                        lineColor.copy(alpha = 0f),
                    ),
                    startY = 0f,
                    endY = size.height,
                ),
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private fun smoothChartPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    for (index in 1 until points.size) {
        val previous = points[index - 1]
        val current = points[index]
        val centerX = (previous.x + current.x) / 2f
        cubicTo(
            centerX,
            previous.y,
            centerX,
            current.y,
            current.x,
            current.y,
        )
    }
}

@Composable
private fun ProgressPhotosSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UnfinishedProgressTitle(
            title = "Progress Photos",
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 24.dp),
        ) {
            item { UpcomingPhotoTile() }
            item {
                ProgressPhotoPlaceholder(
                    date = "Sep 2",
                    colors = listOf(ProgressPhotoStone, ProgressPhotoSage),
                )
            }
            item {
                ProgressPhotoPlaceholder(
                    date = "Aug 16",
                    colors = listOf(Color(0xFFC7C4BD), Color(0xFF9EAC9F)),
                )
            }
        }
    }
}

@Composable
private fun UpcomingPhotoTile() {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(width = 128.dp, height = 176.dp)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(7.dp.toPx(), 6.dp.toPx()),
                        ),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "Coming soon",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ProgressPhotoPlaceholder(
    date: String,
    colors: List<Color>,
) {
    Box(
        modifier = Modifier
            .size(width = 128.dp, height = 176.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(colors)),
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(88.dp),
            tint = Color.White.copy(alpha = 0.48f),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.48f)),
                    ),
                ),
        )
        Text(
            text = date,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun MeasurementsCard() {
    ProgressCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = ProgressNeutral,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Straighten,
                    contentDescription = null,
                    modifier = Modifier.padding(7.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            UnfinishedProgressTitle(
                title = "Measurements",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun UnfinishedProgressTitle(title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = "In progress",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProgressAveragesGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "30-Day Averages",
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProgressAverageCard(
                icon = Icons.Outlined.LocalFireDepartment,
                label = "Calories",
                value = "2,150",
                unit = "kcal",
                modifier = Modifier.weight(1f),
            )
            ProgressAverageCard(
                icon = Icons.Outlined.EggAlt,
                label = "Protein",
                value = "115",
                unit = "g",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProgressAverageCard(
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                label = "Steps",
                value = "8,400",
                unit = "/day",
                modifier = Modifier.weight(1f),
            )
            ProgressAverageCard(
                icon = Icons.Outlined.Bedtime,
                label = "Sleep",
                value = "7h 15m",
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
