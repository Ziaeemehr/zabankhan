package com.ziaee.zabankhan.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupTest {
    @Test fun manifestAcceptsCurrentAndOlderSchemas() {
        validateBackupManifest(BackupManifest("0.1.0", 4))
        validateBackupManifest(BackupManifest("0.0.1", 1))
    }
    @Test fun manifestRejectsFutureOrInvalidFormats() {
        assertFailsWith<IllegalArgumentException> { validateBackupManifest(BackupManifest("0.2.0", 5)) }
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
    @Test fun retentionKeepsExactlyFiveLatestAndIgnoresPartialFiles() {
        val names = (1..8).map { "zabankhan-000$it.zip" }
        assertEquals(names.takeLast(5).toSet(), selectBackupsToKeep(names + "partial.zip.tmp"))
        assertEquals(emptySet(), selectBackupsToKeep(emptyList()))
    }
}
