package com.mmnuig.shoplistcc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mmnuig.shoplistcc.data.AppDatabase
import com.mmnuig.shoplistcc.data.Category
import com.mmnuig.shoplistcc.data.Item
import com.mmnuig.shoplistcc.data.Pref
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    fun addCategory(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        val pos = (categories.value.maxOfOrNull { it.position } ?: -1) + 1
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

    fun addItem(categoryId: Long, name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        val pos = (itemsFor(categoryId).maxOfOrNull { it.position } ?: -1) + 1
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

    fun setFlagged(item: Item, flagged: Boolean) = viewModelScope.launch {
        dao.updateItem(item.copy(flagged = flagged))
    }

    /** Clear All in Plan: cross or uncross every item, and stamp a new plan date. */
    fun setAllCrossed(crossed: Boolean) = viewModelScope.launch {
        dao.setAllCrossed(crossed)
        dao.setPref(Pref("planDate", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)))
    }

    // --- Import/Export ---

    fun replaceAll(data: List<Pair<String, List<Pair<String, Boolean>>>>) = viewModelScope.launch {
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
    }
}
