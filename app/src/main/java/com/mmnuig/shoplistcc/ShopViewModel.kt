package com.mmnuig.shoplistcc

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mmnuig.shoplistcc.data.AppDatabase
import com.mmnuig.shoplistcc.data.Category
import com.mmnuig.shoplistcc.data.Item
import com.mmnuig.shoplistcc.data.Pref
import com.mmnuig.shoplistcc.xlsx.Xlsx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ShopViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).dao()

    val categories = dao.categories()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val items = dao.items()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val planDate = dao.pref("planDate")
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            if (dao.categoryCount() == 0) {
                listOf("Dairy", "Vegetables", "Fruit", "Meat", "Other")
                    .forEachIndexed { i, name ->
                        dao.insertCategory(Category(name = name, position = i))
                    }
            }
        }
    }

    fun itemsFor(categoryId: Long): List<Item> =
        items.value.filter { it.categoryId == categoryId }.sortedBy { it.position }

    // --- Categories ---

    fun addCategory(name: String, atEnd: Boolean = true) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        val pos = if (atEnd) (categories.value.maxOfOrNull { it.position } ?: -1) + 1
        else (categories.value.minOfOrNull { it.position } ?: 1) - 1
        dao.insertCategory(Category(name = trimmed, position = pos))
    }

    fun renameCategory(category: Category, name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        dao.updateCategory(category.copy(name = trimmed))
    }

    fun deleteCategory(category: Category) = viewModelScope.launch {
        dao.deleteCategory(category)
    }

    fun reorderCategories(ordered: List<Category>) = viewModelScope.launch {
        dao.updateCategories(ordered.mapIndexed { i, c -> c.copy(position = i) })
    }

    /** Tick a category in Plan mode: cross off (or uncross) every item in it. */
    fun setCategoryCrossed(categoryId: Long, crossed: Boolean) = viewModelScope.launch {
        dao.setCategoryCrossed(categoryId, crossed)
    }

    // --- Items ---

    fun addItem(categoryId: Long, name: String, atEnd: Boolean = true) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        val siblings = itemsFor(categoryId)
        val pos = if (atEnd) (siblings.maxOfOrNull { it.position } ?: -1) + 1
        else (siblings.minOfOrNull { it.position } ?: 1) - 1
        dao.insertItem(Item(categoryId = categoryId, name = trimmed, position = pos))
    }

    fun renameItem(item: Item, name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        dao.updateItem(item.copy(name = trimmed))
    }

    fun deleteItem(item: Item) = viewModelScope.launch {
        dao.deleteItem(item)
    }

    fun reorderItems(ordered: List<Item>) = viewModelScope.launch {
        dao.updateItems(ordered.mapIndexed { i, it -> it.copy(position = i) })
    }

    fun setCrossed(item: Item, crossed: Boolean) = viewModelScope.launch {
        dao.updateItem(item.copy(crossed = crossed))
    }

    fun setBought(item: Item, bought: Boolean) = viewModelScope.launch {
        dao.updateItem(item.copy(bought = bought))
    }

    /**
     * Shop-mode checkbox: checked means "crossed out" (bought, or excluded by
     * Plan). Ticking an open item marks it bought; unticking a crossed item
     * clears both flags, pulling it back onto this week's list.
     */
    fun shopToggle(item: Item) = viewModelScope.launch {
        if (item.bought || item.crossed) {
            dao.updateItem(item.copy(bought = false, crossed = false))
        } else {
            dao.updateItem(item.copy(bought = true))
        }
    }

    fun setFlagged(item: Item, flagged: Boolean) = viewModelScope.launch {
        dao.updateItem(item.copy(flagged = flagged))
    }

    /** Clear All in Plan: cross or uncross every item, and stamp a new plan date. */
    fun setAllCrossed(crossed: Boolean) = viewModelScope.launch {
        dao.setAllCrossed(crossed)
        dao.setPref(Pref("planDate", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)))
    }

    // --- Import/Export ---

    fun importFrom(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        try {
            val data = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.use { Xlsx.read(it) }
                    ?: throw IllegalStateException("Cannot open file")
            }.map { (name, itemList) ->
                // Strip any leading number; categories are renumbered 1..N by
                // their parse order.
                name.replace(Regex("^\\d+\\s+"), "") to itemList
            }
            if (data.isEmpty()) {
                onResult("No categories found in file")
                return@launch
            }
            dao.replaceAll(
                data.mapIndexed { catPos, (catName, itemList) ->
                    Category(name = catName, position = catPos) to
                        itemList.mapIndexed { itemPos, (itemName, crossed) ->
                            Item(
                                categoryId = 0,
                                name = itemName,
                                position = itemPos,
                                crossed = crossed
                            )
                        }
                }
            )
            onResult("Imported ${data.size} categories, ${data.sumOf { it.second.size }} items")
        } catch (e: Exception) {
            onResult("Import failed: ${e.message}")
        }
    }

    fun exportTo(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        try {
            val data = categories.value.mapIndexed { i, c ->
                "${i + 1} ${c.name}" to itemsFor(c.id).map { it.name to it.crossed }
            }
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?.use { Xlsx.write(it, data) }
                    ?: throw IllegalStateException("Cannot open file")
            }
            onResult("Exported ${data.size} categories, ${data.sumOf { it.second.size }} items")
        } catch (e: Exception) {
            onResult("Export failed: ${e.message}")
        }
    }
}
