package com.mmnuig.shoplistcc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mmnuig.shoplistcc.ShopViewModel
import com.mmnuig.shoplistcc.data.Category
import com.mmnuig.shoplistcc.data.Item
import com.mmnuig.shoplistcc.ui.theme.LocalShopColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun formatPlanDate(iso: String?): String? = try {
    iso?.let { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM")) }
} catch (e: Exception) {
    null
}

@Composable
fun PlanScreen(
    viewModel: ShopViewModel,
    onHome: () -> Unit,
    initialPage: Int = 0,
    onSwitchToShop: (categoryIndex: Int) -> Unit = {}
) {
    val categories = viewModel.categories.collectAsState().value ?: return
    val items by viewModel.items.collectAsState()
    val planDate by viewModel.planDate.collectAsState()
    var currentPage by remember { mutableStateOf(initialPage) }

    val title = formatPlanDate(planDate)?.let { "Plan: $it" } ?: "Plan"

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deleteWithUndo: (Item) -> Unit = { item ->
        viewModel.deleteItem(item)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Deleted ${item.name}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreItem(item)
        }
    }

    Scaffold(
        topBar = {
            GreenTopBar(title, onHome, extraActions = {
                IconButton(onClick = { onSwitchToShop(currentPage - 1) }) {
                    Icon(
                        Icons.Filled.ShoppingCart,
                        contentDescription = "Switch to Shop",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        WrapAroundPager(
            pageCount = categories.size + 1,
            modifier = Modifier.padding(innerPadding),
            initialPage = initialPage,
            onPageChanged = { currentPage = it }
        ) { page, goTo ->
            if (page == 0) {
                PlanCategoriesPage(
                    viewModel, categories, items,
                    onOpenCategory = { index -> goTo(index + 1) }
                )
            } else {
                PlanCategoryPage(
                    viewModel,
                    categories[page - 1],
                    items.filter { it.categoryId == categories[page - 1].id },
                    number = page,
                    onDeleteItem = deleteWithUndo
                )
            }
        }
    }
}

@Composable
private fun PlanCategoriesPage(
    viewModel: ShopViewModel,
    categories: List<Category>,
    items: List<Item>,
    onOpenCategory: (Int) -> Unit
) {
    var showClearAll by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Category?>(null) }
    var deleteTarget by remember { mutableStateOf<Category?>(null) }

    var localCategories by remember(categories) { mutableStateOf(categories) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = localCategories.indexOfFirst { it.id == from.key }
        val toIdx = localCategories.indexOfFirst { it.id == to.key }
        if (fromIdx != -1 && toIdx != -1) {
            localCategories = localCategories.toMutableList()
                .apply { add(toIdx, removeAt(fromIdx)) }
        }
    }

    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Column {
                Text(
                    "Categories",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                )
                Button(
                    onClick = { showClearAll = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("CLEAR ALL")
                }
                AddField(onAdd = { viewModel.addCategory(it, atEnd = false) })
            }
        }
        itemsIndexed(localCategories, key = { _, category -> category.id }) { index, category ->
            ReorderableItem(reorderableState, key = category.id) {
                val catItems = items.filter { it.categoryId == category.id }
                val allCrossed = catItems.isNotEmpty() && catItems.all { it.crossed }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .pointerInput(category.id) {
                            val open = {
                                val idx = categories.indexOfFirst { it.id == category.id }
                                if (idx != -1) onOpenCategory(idx)
                            }
                            detectTapGestures(
                                onTap = { open() },
                                onDoubleTap = { open() }
                            )
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = allCrossed,
                            onCheckedChange = { viewModel.setCategoryCrossed(category.id, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = LocalShopColors.current.blueCheck
                            )
                        )
                        Text(
                            "${index + 1} ${category.name}",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        CompactIconButton(Icons.Filled.Edit, "Rename") { renameTarget = category }
                        CompactIconButton(Icons.Filled.Delete, "Delete") { deleteTarget = category }
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                        ) {
                            IconButton(
                                onClick = {},
                                modifier = Modifier
                                    .size(40.dp)
                                    .draggableHandle(
                                        onDragStopped = { viewModel.reorderCategories(localCategories) }
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.DragHandle, "Reorder",
                                    tint = LocalShopColors.current.flag
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "footer") {
            AddField(onAdd = { viewModel.addCategory(it, atEnd = true) })
        }
    }

    if (showClearAll) {
        ClearAllDialog(
            onCrossAll = { viewModel.setAllCrossed(true) },
            onUncrossAll = { viewModel.setAllCrossed(false) },
            onDismiss = { showClearAll = false }
        )
    }
    renameTarget?.let { target ->
        RenameDialog(
            title = "Rename category",
            initial = target.name,
            onConfirm = { viewModel.renameCategory(target, it) },
            onDismiss = { renameTarget = null }
        )
    }
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete category",
            message = "Delete \"${target.name}\" and all its items?",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteCategory(target) },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun ClearAllDialog(
    onCrossAll: () -> Unit,
    onUncrossAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a new plan") },
        text = { Text("This stamps today's date on the plan. Flags are kept; bought ticks reset when you next open Shop.") },
        confirmButton = {
            Column {
                TextButton(onClick = { onCrossAll(); onDismiss() }) {
                    Text("Cross off all items")
                }
                TextButton(onClick = { onUncrossAll(); onDismiss() }) {
                    Text("Uncross all items")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun PlanCategoryPage(
    viewModel: ShopViewModel,
    category: Category,
    items: List<Item>,
    number: Int,
    onDeleteItem: (Item) -> Unit
) {
    var renameTarget by remember { mutableStateOf<Item?>(null) }

    var localItems by remember(items) { mutableStateOf(items) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = localItems.indexOfFirst { it.id == from.key }
        val toIdx = localItems.indexOfFirst { it.id == to.key }
        if (fromIdx != -1 && toIdx != -1) {
            localItems = localItems.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
        }
    }

    val allCrossed = items.isNotEmpty() && items.all { it.crossed }

    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = allCrossed,
                        onCheckedChange = { viewModel.setCategoryCrossed(category.id, it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = LocalShopColors.current.blueCheck
                        )
                    )
                    Text("$number ${category.name}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AddField(onAdd = { viewModel.addItem(category.id, it, atEnd = false) })
            }
        }
        items(localItems, key = { it.id }) { item ->
            ReorderableItem(reorderableState, key = item.id) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { viewModel.setCrossed(item, !item.crossed) },
                    colors = CardDefaults.cardColors(containerColor = LocalShopColors.current.unplannedBg),
                    border = BorderStroke(1.dp, LocalShopColors.current.unplannedBorder)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Crossed = not needed this week = the blue state, so
                        // the tick is blue like the card, never green-on-blue.
                        Checkbox(
                            checked = item.crossed,
                            onCheckedChange = { viewModel.setCrossed(item, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = LocalShopColors.current.blueCheck
                            )
                        )
                        Text(
                            item.name,
                            textDecoration = if (item.crossed) TextDecoration.LineThrough else null,
                            color = if (item.crossed) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                            modifier = Modifier.weight(1f)
                        )
                        CompactIconButton(Icons.Filled.Edit, "Rename") { renameTarget = item }
                        CompactIconButton(Icons.Filled.Delete, "Delete") { onDeleteItem(item) }
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                        ) {
                            IconButton(
                                onClick = {},
                                modifier = Modifier
                                    .size(40.dp)
                                    .draggableHandle(
                                        onDragStopped = { viewModel.reorderItems(localItems) }
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.DragHandle, "Reorder",
                                    tint = LocalShopColors.current.flag
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "footer") {
            Column {
                AddField(onAdd = { viewModel.addItem(category.id, it, atEnd = true) })
                Spacer(Modifier.padding(bottom = 24.dp))
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            title = "Rename item",
            initial = target.name,
            onConfirm = { viewModel.renameItem(target, it) },
            onDismiss = { renameTarget = null }
        )
    }
}
