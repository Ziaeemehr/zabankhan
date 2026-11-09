package com.ziaee.zabankhan.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupTest {
    private class MemoryPreferencesStore : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
        private val state = kotlinx.coroutines.flow.MutableStateFlow(androidx.datastore.preferences.core.emptyPreferences())
        override val data = state
        override suspend fun updateData(transform: suspend (t: androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
    @Test fun manifestAcceptsCurrentAndOlderSchemas() {
        validateBackupManifest(BackupManifest("0.1.0", 4))
        validateBackupManifest(BackupManifest("0.0.1", 1))
    }
    @Test fun manifestRejectsFutureOrInvalidFormats() {
        assertFailsWith<IllegalArgumentException> { validateBackupManifest(BackupManifest("0.2.0", BACKUP_SCHEMA_VERSION + 1)) }
        assertFailsWith<IllegalArgumentException> { validateBackupManifest(BackupManifest("0.1.0", 0)) }
        assertFailsWith<IllegalArgumentException> { validateBackupManifest(BackupManifest("0.1.0", 3, 2)) }
        assertFailsWith<IllegalArgumentException> { validateBackupManifest(BackupManifest("", 3)) }
    }
    @Test fun databaseHeaderMustMatchManifestSchema() {
        val header = ByteArray(100)
        "SQLite format 3\u0000".encodeToByteArray().copyInto(header)
        header[63] = 3
        validateDatabaseHeader(header, 3)
        assertFailsWith<IllegalArgumentException> { validateDatabaseHeader(header, 2) }
        header[60] = 1 // Big-endian version must be read in full, not just its final byte.
        assertFailsWith<IllegalArgumentException> { validateDatabaseHeader(header, 3) }
        assertFailsWith<IllegalArgumentException> { validateDatabaseHeader(ByteArray(100), 3) }
    }

    @Test fun pdfBackupPreferenceDefaultsOffUnlessExplicitlyEnabled() = kotlinx.coroutines.test.runTest {
        val prefs = BackupPrefs(MemoryPreferencesStore())
        assertEquals(false, prefs.backUpPdfFiles())
        prefs.setBackUpPdfFiles(true)
        assertEquals(true, prefs.backUpPdfFiles())
        prefs.setBackUpPdfFiles(false)
        assertEquals(false, prefs.backUpPdfFiles())
    }
}
