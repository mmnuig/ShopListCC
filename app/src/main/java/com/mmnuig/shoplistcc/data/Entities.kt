package com.mmnuig.shoplistcc.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId")]
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val position: Int,
    // Plan mode: ticked = crossed off = NOT on this week's shopping list
    val crossed: Boolean = false,
    // Shop mode: ticked = bought
    val bought: Boolean = false,
    val flagged: Boolean = false
)

@Entity(tableName = "prefs")
data class Pref(
    @PrimaryKey val key: String,
    val value: String
)
