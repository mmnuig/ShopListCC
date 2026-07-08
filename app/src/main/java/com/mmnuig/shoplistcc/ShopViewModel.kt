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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ShopViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).dao()

    // null until the database has emitted; screens wait for it so the pager is
    // created with the right page count (matters for state restoration).
    val categories: StateFlow<List<Category>?> = dao.categories()
        .map<List<Category>, List<Category>?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val items = dao.items()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val planDate = dao.pref("planDate")
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val largeText = dao.pref("largeText")
        .map { it == "1" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** "system" (default), "light" or "dark". */
    val themePref = dao.pref("theme")
        .map { it ?: "system" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    fun setLargeText(large: Boolean) = viewModelScope.launch {
        dao.setPref(Pref("largeText", if (large) "1" else "0"))
    }

    fun setTheme(theme: String) = viewModelScope.launch {
        dao.setPref(Pref("theme", theme))
    }

    init {
        viewModelScope.launch {
            if (dao.categoryCount() == 0) {
                listOf("Dairy", "Vegetables", "Fruit", "Meat", "Other")
                    .forEachIndexed { i, name ->
                        dao.insertCategory(Category(name = name, position = i))
                    }
            }
            // Bought implies crossed off; repairs data written before the two
            // states were unified.
            dao.syncCrossedWithBought()
        }
    }

    fun itemsFor(categoryId: Long): List<Item> =
        items.value.filter { it.categoryId == categoryId }.sortedBy { it.position }

    // --- Categories ---

    fun addCategory(name: String, atEnd: Boolean = true) = viewModelScope.launch {
        val tidied = tidyName(name)
        if (tidied.isEmpty()) return@launch
        val existing = categories.value.orEmpty()
        val pos = if (atEnd) (existing.maxOfOrNull { it.position } ?: -1) + 1
        else (existing.minOfOrNull { it.position } ?: 1) - 1
        dao.insertCategory(Category(name = tidied, position = pos))
    }

    fun renameCategory(category: Category, name: String) = viewModelScope.launch {
        val tidied = tidyName(name)
        if (tidied.isEmpty()) return@launch
        dao.updateCategory(category.copy(name = tidied))
    }

    fun deleteCategory(category: Category) = viewModelScope.launch {
        dao.deleteCategory(category)
    }

    fun reorderCategories(ordered: List<Category>) = viewModelScope.launch {
        dao.updateCategories(ordered.mapIndexed { i, c -> c.copy(position = i) })
    }

    /** Tick a category in Plan mode: cross off (or uncross) every item in it. */
    fun setCategoryCrossed(categoryId: Long, crossed: Boolean) = viewModelScope.launch {
        if (crossed) dao.setCategoryCrossed(categoryId, true)
        else dao.uncrossCategory(categoryId)
    }

    // --- Items ---

    /** Title-case a name the user typed all in lowercase; leave mixed case alone. */
    private fun tidyName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed != trimmed.lowercase()) return trimmed
        return trimmed.split(Regex("\\s+"))
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }

    fun addItem(categoryId: Long, name: String, atEnd: Boolean = true) = viewModelScope.launch {
        val tidied = tidyName(name)
        if (tidied.isEmpty()) return@launch
        val siblings = itemsFor(categoryId)
        val pos = if (atEnd) (siblings.maxOfOrNull { it.position } ?: -1) + 1
        else (siblings.minOfOrNull { it.position } ?: 1) - 1
        dao.insertItem(Item(categoryId = categoryId, name = tidied, position = pos))
    }

    fun renameItem(item: Item, name: String) = viewModelScope.launch {
        val tidied = tidyName(name)
        if (tidied.isEmpty()) return@launch
        // An edited item is treated as newly wanted: back on this week's list.
        dao.updateItem(item.copy(name = tidied, crossed = false, bought = false))
    }

    fun deleteItem(item: Item) = viewModelScope.launch {
        dao.deleteItem(item)
    }

    /** Undo of a delete: re-insert with the original position and flags. */
    fun restoreItem(item: Item) = viewModelScope.launch {
        dao.insertItem(item.copy(id = 0))
    }

    fun reorderItems(ordered: List<Item>) = viewModelScope.launch {
        dao.updateItems(ordered.mapIndexed { i, it -> it.copy(position = i) })
    }

    fun setCrossed(item: Item, crossed: Boolean) = viewModelScope.launch {
        // Uncrossing puts the item back on the list to buy, so it can't stay
        // marked as bought; crossing keeps bought-ness for Shop's colouring.
        dao.updateItem(
            if (crossed) item.copy(crossed = true)
            else item.copy(crossed = false, bought = false)
        )
    }

    fun setBought(item: Item, bought: Boolean) = viewModelScope.launch {
        dao.updateItem(item.copy(bought = bought))
    }

    /**
     * Shop-mode checkbox: checked means "crossed out" (bought, or excluded by
     * Plan). Ticking an open item marks it bought AND crossed off, so the
     * cross-off shows in Plan too; unticking clears both flags, pulling the
     * item back onto this week's list.
     */
    fun shopToggle(item: Item) = viewModelScope.launch {
        if (item.bought || item.crossed) {
            dao.updateItem(item.copy(bought = false, crossed = false))
        } else {
            dao.updateItem(item.copy(bought = true, crossed = true))
        }
    }

    fun setFlagged(item: Item, flagged: Boolean) = viewModelScope.launch {
        dao.updateItem(item.copy(flagged = flagged))
    }

    /** Clear All in Plan: cross or uncross every item, and stamp a new plan date. */
    fun setAllCrossed(crossed: Boolean) = viewModelScope.launch {
        if (crossed) dao.setAllCrossed(true) else dao.uncrossAll()
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
            val data = categories.value.orEmpty().mapIndexed { i, c ->
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
