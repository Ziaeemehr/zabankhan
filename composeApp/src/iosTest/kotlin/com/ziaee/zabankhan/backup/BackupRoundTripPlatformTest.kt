package com.ziaee.zabankhan.backup

import androidx.room.Room
import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.buildDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class BackupRoundTripPlatformTest {
    @Test fun zippedDatabasePreservesEveryUserTableIncludingSrsAndStudyLog() = runTest {
        assertBackupRoundTrip { path ->
            buildDatabase(Room.databaseBuilder<AppDatabase>(name = path))
        }
    }
}
