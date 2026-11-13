package com.ziaee.zabankhan.sync.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.buildDatabase
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class VideoChannelLibrarySyncPlatformTest {
    @Test fun channelDataAndDeletionRoundTripThroughLibrarySync() = runTest {
        assertVideoChannelLibraryRoundTrip { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext(), path)) }
    }
}
