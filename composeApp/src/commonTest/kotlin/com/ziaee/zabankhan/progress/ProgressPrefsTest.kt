package com.ziaee.zabankhan.progress

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import kotlin.test.*

class ProgressPrefsTest {
    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
    private fun summary(language: String = "fr") = computeProgress(ProgressInputs(language,
        LocalDate(2026, 11, 7), TimeZone.UTC, learnedWords = mapOf(language to 100L)))

    @Test fun goalDefaultsClampsAndSurvivesRepositoryRecreation() = runTest {
        val store = MemoryStore()
        val repository = ProgressPreferences(store)
        assertEquals(300, repository.goal.first())
        repository.setGoal(450)
        assertEquals(450, ProgressPreferences(store).goal.first())
        repository.setGoal(0)
        assertEquals(MIN_WEEKLY_GOAL, repository.goal.first())
        repository.setGoal(Int.MAX_VALUE)
        assertEquals(MAX_WEEKLY_GOAL, repository.goal.first())
    }
    @Test fun claimsPersistBeforeReturnAndAreAtomicAcrossCollectors() = runTest {
        val store = MemoryStore()
        val repository = ProgressPreferences(store)
        val claims = List(10) { async { repository.claim(summary()) } }.awaitAll()
        assertEquals(1, claims.count { it.hasNews })
        val restarted = ProgressPreferences(store)
        assertFalse(restarted.claim(summary()).hasNews)
        val otherLanguage = restarted.claim(summary("en"))
        assertFalse(otherLanguage.levelIncreased)
        assertEquals(1, otherLanguage.newBadgeCount)
        restarted.claim(summary().copy(level = 0, badges = emptyList()))
        assertFalse(restarted.claim(summary()).hasNews)
    }
}
