package com.ziaee.zabankhan.sync.library

import androidx.room.Room
import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.buildDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class LibraryApplyPlatformTest {
    @Test fun appliesAndDeletesLibraryItems() = runTest {
        assertLibraryApply { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(name = path)) }
    }
    @Test fun syncsLibraryThroughEngine() = runTest {
        assertLibrarySyncEngine { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(name = path)) }
    }
    @Test fun generatesAndBackfillsSyncIds() = runTest {
        assertSyncIdsGeneratedAndBackfilled { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(name = path)) }
    }
    @Test fun backfillsDeterministicallyOnOpen() = runTest {
        assertDeterministicBackfillOnOpen { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(name = path)) }
    }
    @Test fun applierKeepsLocalStateAndOrdersFolders() = runTest {
        assertApplierKeepsLocalStateAndOrdersFolders { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(name = path)) }
    }
    @Test fun engineReviewScenarios() = runTest {
        assertEngineReviewScenarios { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(name = path)) }
    }
}
