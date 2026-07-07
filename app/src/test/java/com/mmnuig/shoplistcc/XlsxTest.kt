package com.mmnuig.shoplistcc

import com.mmnuig.shoplistcc.xlsx.Xlsx
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class XlsxTest {

    @Test
    fun roundTrip_preservesCategoriesItemsAndCrossedState() {
        val data = listOf(
            "Dairy" to listOf("Bread" to false, "Milk" to true, "Butter & Cream" to false),
            "Vegetables" to listOf("Potatoes" to false, "Apples <green>" to true),
            "Fruit" to listOf("Bananas" to false, "Plums" to true),
            "Meat" to listOf("Chicken" to true, "Steak" to true, "Mince" to false),
            "Other" to (100..119).map { "a$it" to (it % 2 == 0) }
        )
        val out = ByteArrayOutputStream()
        Xlsx.write(out, data)
        val back = Xlsx.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(data, back)
    }

    @Test
    fun roundTrip_emptyCategories() {
        val data = listOf(
            "Empty" to emptyList(),
            "NotEmpty" to listOf("Thing" to false)
        )
        val out = ByteArrayOutputStream()
        Xlsx.write(out, data)
        val back = Xlsx.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(data, back)
    }
}
