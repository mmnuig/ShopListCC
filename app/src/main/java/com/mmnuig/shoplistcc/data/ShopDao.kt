package com.mmnuig.shoplistcc.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {

    @Query("SELECT * FROM categories ORDER BY position")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM items ORDER BY position")
    fun items(): Flow<List<Item>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Insert
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Update
    suspend fun updateCategories(categories: List<Category>)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Insert
    suspend fun insertItem(item: Item): Long

    @Insert
    suspend fun insertItems(items: List<Item>)

    @Update
    suspend fun updateItem(item: Item)

    @Update
    suspend fun updateItems(items: List<Item>)

    @Delete
    suspend fun deleteItem(item: Item)

    @Query("UPDATE items SET crossed = :crossed")
    suspend fun setAllCrossed(crossed: Boolean)

    @Query("UPDATE items SET crossed = :crossed WHERE categoryId = :categoryId")
    suspend fun setCategoryCrossed(categoryId: Long, crossed: Boolean)

    @Query("UPDATE items SET crossed = 0, bought = 0")
    suspend fun uncrossAll()

    @Query("UPDATE items SET crossed = 0, bought = 0 WHERE categoryId = :categoryId")
    suspend fun uncrossCategory(categoryId: Long)

    /** Invariant repair: a bought item is always also crossed off. */
    @Query("UPDATE items SET crossed = 1 WHERE bought = 1")
    suspend fun syncCrossedWithBought()

    /** Start of a new shop: nothing is in the trolley yet. Crossed stays. */
    @Query("UPDATE items SET bought = 0")
    suspend fun clearAllBought()

    @Query("SELECT value FROM prefs WHERE `key` = :key")
    fun pref(key: String): Flow<String?>

    @Query("SELECT value FROM prefs WHERE `key` = :key")
    suspend fun prefValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPref(pref: Pref)

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()

    @Transaction
    suspend fun replaceAll(categoriesWithItems: List<Pair<Category, List<Item>>>) {
        deleteAllItems()
        deleteAllCategories()
        for ((category, items) in categoriesWithItems) {
            val catId = insertCategory(category)
            insertItems(items.map { it.copy(categoryId = catId) })
        }
    }
}
