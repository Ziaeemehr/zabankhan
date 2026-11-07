package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.discover.NewsItem
import com.ziaee.zabankhan.discover.NewsSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NewsLoadTest {
    private fun item(link: String, at: Long) = NewsItem("t$at", "s", link, link, at)

    @Test fun mergesSourcesNewestFirstAndCountsFailures() = runTest {
        val a = NewsSource("a", "A", "https://a"); val b = NewsSource("b", "B", "https://b"); val c = NewsSource("c", "C", "https://c")
        val load = loadNews(listOf(a, b, c)) { source ->
            when (source) {
                a -> listOf(item("https://x/1", 1), item("https://x/3", 3))
                b -> listOf(item("https://x/2", 2), item("https://x/3/", 3))
                else -> error("offline")
            }
        }
        assertEquals(listOf("https://x/3", "https://x/2", "https://x/1"), load.headlines.map { it.item.link })
        assertEquals(listOf("A", "B", "A"), load.headlines.map { it.source.name })
        assertEquals(1, load.failed)
    }

    @Test fun languageWithoutSourcesLoadsNothing() = runTest {
        assertEquals(NewsLoad(emptyList(), 0), loadNews(emptyList()) { error("unused") })
    }
}
