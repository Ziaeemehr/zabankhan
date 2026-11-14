package com.ziaee.zabankhan

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class IncomingTextUriTest {
    private val uri = Uri.parse("content://provider/book.md")

    @Test fun readsSendStream() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri)
        intent.clipData = ClipData.newRawUri("other", Uri.parse("content://provider/other.md"))
        assertEquals(uri, incomingTextUri(intent))
    }

    @Test fun fallsBackToClipData() {
        val intent = Intent(Intent.ACTION_SEND).apply { clipData = ClipData.newRawUri("book.md", uri) }
        assertEquals(uri, incomingTextUri(intent))
    }

    @Test fun ignoresPlainTextSharesAndMissingIntents() {
        assertEquals(null, incomingTextUri(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "some text")))
        assertEquals(null, incomingTextUri(null))
    }

    @Test fun readsContentViewAndIgnoresNonContentView() {
        assertEquals(uri, incomingTextUri(Intent(Intent.ACTION_VIEW, uri)))
        assertEquals(null, incomingTextUri(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))))
    }
}
