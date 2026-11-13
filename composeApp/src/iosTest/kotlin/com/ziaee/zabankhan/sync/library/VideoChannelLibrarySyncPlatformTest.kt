package com.ziaee.zabankhan.sync.library

import androidx.room.Room
import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.buildDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class VideoChannelLibrarySyncPlatformTest {
    @Test fun channelDataAndDeletionRoundTripThroughLibrarySync() = runTest {
        assertVideoChannelLibraryRoundTrip { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(name = path)) }
    }
}
