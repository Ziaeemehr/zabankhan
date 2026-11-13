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
class SavedVideoLibrarySyncPlatformTest {
    @Test fun favoriteAndUnfavoritePropagateThroughRoomLibrarySync() = runTest {
        assertSavedVideoLibraryRoundTrip { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext(), path)) }
    }

    @Test fun backupRebasePreservesLocalFavorite() = runTest {
        assertSavedVideoBackupRebase { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext(), path)) }
    }
    @Test fun namedPlaylistsRoundTripThroughRoomSync() = runTest {
        assertVideoPlaylistSync { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext(), path)) }
    }
}
