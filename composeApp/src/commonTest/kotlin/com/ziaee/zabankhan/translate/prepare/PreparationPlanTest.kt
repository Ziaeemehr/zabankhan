package com.ziaee.zabankhan.translate.prepare

import kotlin.test.Test
import kotlin.test.assertEquals

class PreparationPlanTest {
    @Test fun groupsAdjacentLinesOnly() {
        val items = listOf(
            PreparationItem("one", "fa", group = "text", position = 0),
            PreparationItem("two", "fa", group = "text", position = 1),
            PreparationItem("three", "en", group = "text", position = 2)
        )
        assertEquals(listOf(2, 1), preparationBatches(items).map { it.size })
    }

    @Test fun filtersCacheAndDeduplicatesSelectedSources() {
        val item = PreparationItem("hello", "fa")
        val sources = listOf(PreparationSource("a", "A", listOf(item)), PreparationSource("b", "B", listOf(item)))
        assertEquals(listOf(item), selectedItems(sources, setOf("a", "b")))
        assertEquals(emptyList(), pendingItems(listOf(item)) { true })
    }
}
