package com.ziaee.zabankhan.watch

import androidx.room.Room
import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.buildDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class WatchTranscriptDatabasePlatformTest {
    @Test fun captionsAreTransientUntilExplicitSaveAndRepeatSaveUpdatesInPlace() = runTest {
        assertOptionalTranscriptSave { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(name = path)) }
    }
}
