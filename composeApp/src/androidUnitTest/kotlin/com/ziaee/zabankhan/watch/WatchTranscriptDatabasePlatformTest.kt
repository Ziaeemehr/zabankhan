package com.ziaee.zabankhan.watch

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.buildDatabase
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class WatchTranscriptDatabasePlatformTest {
    @Test fun captionsAreTransientUntilExplicitSaveAndRepeatSaveUpdatesInPlace() = runTest {
        assertOptionalTranscriptSave { path ->
            buildDatabase(Room.databaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext(), path))
        }
    }
}
