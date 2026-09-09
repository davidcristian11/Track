package com.example.track

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.track.ui.theme.TrackTheme

private val SearchFieldBackground = Color(0xFFF0F0F0)

@Composable
fun AddFoodSearchScreen(
    onBack: () -> Unit,
    onFoodSelected: (FoodDefinition) -> Unit,
    onBarcodeClick: () -> Unit,
    onCreateFood: () -> Unit = {},
    recentFoods: List<FoodDefinition> = emptyList(),
    search: FoodSearchState = FoodSearchState(),
    onSearch: (String) -> Unit = {},
    onSearchClosed: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { onSearch(query) }
    DisposableEffect(Unit) { onDispose { onSearchClosed() } }
    val currentSearch = search.takeIf { it.query == query } ?: FoodSearchState(query, loading = query.trim().length >= 2)
    val filteredFoods = currentSearch.foods

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        AddFoodSearchTopBar(onBack = onBack)
        Spacer(Modifier.height(8.dp))
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            placeholder = {
                Text(
                    text = "Search foods",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                IconButton(onClick = onBarcodeClick) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = "Barcode scanner",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SearchFieldBackground,
                unfocusedContainerColor = SearchFieldBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCreateFood) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Create Food")
            }
        }

        if (query.isBlank()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            ) {
                item {
                    Text(
                        text = "Recent Foods",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                if (recentFoods.isEmpty()) {
                    item {
                        Text("No recent foods yet", style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("Foods you add will appear here.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    item { SearchResultsCard(recentFoods, onFoodSelected) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            ) {
                item {
                    Text(
                        text = "SEARCH RESULTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                if (currentSearch.loading) {
                    item {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Searching Open Food Facts…", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
                if (currentSearch.unavailable) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Online search unavailable", style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { onSearch(query) }) { Text("Retry") }
                        }
                    }
                }
                if (filteredFoods.isEmpty() && !currentSearch.loading && !currentSearch.unavailable) {
                    item {
                        Text(
                            text = "No foods found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (filteredFoods.isNotEmpty()) {
                    item {
                        SearchResultsCard(
                            foods = filteredFoods,
                            onFoodSelected = onFoodSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddFoodSearchTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "Add Food",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(40.dp))
    }
}

@Composable
private fun SearchResultsCard(
    foods: List<FoodDefinition>,
    onFoodSelected: (FoodDefinition) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            foods.forEachIndexed { index, food ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFoodSelected(food) }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = food.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = food.searchMetadata,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Select ${food.name}",
                            modifier = Modifier.padding(6.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (index < foods.lastIndex) {
                    HorizontalDivider(color = SearchFieldBackground)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun AddFoodSearchScreenPreview() {
    TrackTheme {
        AddFoodSearchScreen(
            onBack = {},
            onFoodSelected = {},
            onBarcodeClick = {},
        )
    }
}
