package com.mmnuig.shoplistcc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mmnuig.shoplistcc.ShopViewModel
import com.mmnuig.shoplistcc.data.Category
import com.mmnuig.shoplistcc.data.Item
import com.mmnuig.shoplistcc.ui.theme.LocalShopColors

@Composable
fun ShopScreen(
    viewModel: ShopViewModel,
    onHome: () -> Unit,
    initialPage: Int = 0,
    onSwitchToPlan: (categoryIndex: Int) -> Unit = {}
) {
    val categories = viewModel.categories.collectAsState().value ?: return
    val items by viewModel.items.collectAsState()
    val planDate by viewModel.planDate.collectAsState()
    var currentPage by remember { mutableStateOf(initialPage) }

    val title = formatPlanDate(planDate)?.let { "Shop (Planned $it)" } ?: "Shop"

    Scaffold(topBar = {
        GreenTopBar(title, onHome, extraActions = {
            IconButton(onClick = {
                onSwitchToPlan(if (currentPage < categories.size) currentPage else -1)
            }) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = "Switch to Plan",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        })
    }) { innerPadding ->
        // Pages 0..N-1: one category per page; page N: summary. Wraps around.
        val pageCount = categories.size + 1
        WrapAroundPager(
            pageCount = pageCount,
            modifier = Modifier.padding(innerPadding),
            initialPage = initialPage,
            onPageChanged = { currentPage = it }
        ) { page, goTo ->
            if (page < categories.size) {
                ShopCategoryPage(
                    viewModel,
                    categories[page],
                    items.filter { it.categoryId == categories[page].id },
                    number = page + 1,
                    onNext = { goTo((page + 1) % pageCount) }
                )
            } else {
                ShopSummaryPage(categories, items, onOpenCategory = goTo)
            }
        }
    }
}

@Composable
private fun ShopCategoryPage(
    viewModel: ShopViewModel,
    category: Category,
    items: List<Item>,
    number: Int,
    onNext: () -> Unit
) {
    val crossedOff = items.count { it.bought || it.crossed }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$number ${category.name}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text("$crossedOff/${items.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(items, key = { it.id }) { item ->
            ShopItemCard(
                item = item,
                onToggle = { viewModel.shopToggle(item) },
                onFlag = { viewModel.setFlagged(item, !item.flagged) }
            )
        }
        if (items.isNotEmpty() && crossedOff == items.size) {
            item(key = "next") {
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text("Next category")
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShopItemCard(
    item: Item,
    onToggle: () -> Unit,
    onFlag: () -> Unit
) {
    // Checked = crossed out: either excluded by Plan (dull blue) or bought
    // (slightly darker green). Open items on this week's list are bright green.
    val checked = item.bought || item.crossed
    val shopColors = LocalShopColors.current
    val (bg, border) = when {
        item.crossed -> shopColors.unplannedBg to shopColors.unplannedBorder
        item.bought -> shopColors.boughtBg to shopColors.boughtBorder
        else -> shopColors.plannedBg to shopColors.plannedBorder
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, border)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Text(
                item.name,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onFlag) {
                Icon(
                    if (item.flagged) Icons.Filled.Flag else Icons.Outlined.Flag,
                    contentDescription = if (item.flagged) "Unflag" else "Flag",
                    tint = LocalShopColors.current.flag
                )
            }
        }
    }
}

@Composable
private fun ShopSummaryPage(
    categories: List<Category>,
    items: List<Item>,
    onOpenCategory: (Int) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Text(
                "Summary",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
            val catItems = items.filter { it.categoryId == category.id }
            val crossedOff = catItems.count { it.bought || it.crossed }
            val flagged = catItems.count { it.flagged }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable { onOpenCategory(index) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1} ${category.name}",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "$crossedOff/${catItems.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(56.dp)
                    )
                    Icon(
                        Icons.Filled.Flag,
                        contentDescription = "Flagged",
                        tint = LocalShopColors.current.flag,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    Text(
                        "$flagged",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(24.dp)
                    )
                }
            }
        }
    }
}
