package com.ziaee.zabankhan.files

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PickedFileCopyTest {
    @Test fun copiesProviderContentVerbatimIntoPrivateStorageAndReusesIdenticalFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://files/test.MD")
        val raw = "# Title\n\n**bold** and فارسی"
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(raw.encodeToByteArray()) }
        val picked = copyPickedFile(context, uri, listOf(FileKind.TEXT))
        val stored = File(picked.path)
        assertEquals("test.MD", picked.displayName)
        assertTrue(stored.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator))
        assertEquals(raw, stored.readText())
        assertEquals("test", File(picked.path + ".title").readText())
        assertEquals(picked, copyPickedFile(context, uri, listOf(FileKind.TEXT)))
        assertTrue(stored.parentFile!!.listFiles()!!.none { it.name.startsWith("import-") })
    }


    @Test fun rejectsUnsupportedExtensionBeforeOpeningProvider() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFailsWith<IllegalArgumentException> {
            copyPickedFile(context, Uri.parse("content://missing/book.pdf"), listOf(FileKind.TEXT))
        }
    }

    @Test fun unreadableTextUriFailsInsteadOfReturningAnEmptyCopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFailsWith<Exception> {
            copyPickedFile(context, Uri.parse("content://missing/book.txt"), listOf(FileKind.TEXT))
        }
    }
}
