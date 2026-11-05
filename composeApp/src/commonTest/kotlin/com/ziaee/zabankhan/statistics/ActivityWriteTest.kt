package com.ziaee.zabankhan.statistics

import com.ziaee.zabankhan.data.ActivityDao
import com.ziaee.zabankhan.data.ActivityLogEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ActivityWriteTest {
    private class Dao(private val failure: Exception? = null, private val failOnWrite: Boolean = false) : ActivityDao {
        var stored = ActivityLogEntry("2026-11-05", "en", readingMs = 10)
        override fun observe(lang: String) = flowOf(listOf(stored))
        override suspend fun get(date: String, lang: String): ActivityLogEntry {
            if (!failOnWrite) failure?.let { throw it }
            return stored
        }
        override suspend fun upsert(e: ActivityLogEntry) {
            failure?.let { throw it }
            stored = e
        }
    }

    @Test fun disposedScreenFlushToleratesClosedDatabase() = runTest {
        for (failOnWrite in listOf(false, true)) {
            writeActivity(Dao(IllegalStateException("Database is closed"), failOnWrite), mapOf("2026-11-05" to 20L), "en", ActivityKind.READING)
        }
    }

    @Test fun cancellationStillPropagates() = runTest {
        assertFailsWith<CancellationException> {
            writeActivity(Dao(CancellationException("cancelled")), mapOf("2026-11-05" to 20L), "en", ActivityKind.READING)
        }
    }

    @Test fun successfulFlushAddsMeasuredTime() = runTest {
        val dao = Dao()
        writeActivity(dao, mapOf("2026-11-05" to 20L), "en", ActivityKind.READING)
        assertEquals(30L, dao.stored.readingMs)
    }
}
