package com.ziaee.zabankhan.sync.library

import androidx.room.Room
import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.buildDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SavedVideoLibrarySyncPlatformTest {
    @Test fun favoriteAndUnfavoritePropagateThroughRoomLibrarySync() = runTest {
        assertSavedVideoLibraryRoundTrip { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(name = path)) }
    }

    @Test fun backupRebasePreservesLocalFavorite() = runTest {
        assertSavedVideoBackupRebase { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(name = path)) }
    }
    @Test fun namedPlaylistsRoundTripThroughRoomSync() = runTest {
        assertVideoPlaylistSync { path -> buildDatabase(Room.databaseBuilder<AppDatabase>(name = path)) }
    }
}
