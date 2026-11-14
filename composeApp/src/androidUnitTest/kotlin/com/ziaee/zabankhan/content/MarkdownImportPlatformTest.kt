package com.ziaee.zabankhan.content

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.buildDatabase
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.ui.reader.BlockType
import com.ziaee.zabankhan.ui.reader.paragraphs
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class MarkdownImportPlatformTest {
    @Test fun importingMarkdownPreservesFormattingAndFrontMatterMetadata() = runTest {
        val db = buildDatabase(Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext()))
        try {
            val body = "# Heading\n\nA **bold** and *italic* [word](https://example.com).\n\n- item\n\n| A | B |\n|---|---|\n| one | <span style='color: red'>two</span> |"
            val raw = "---\ntitle: My book\nlevel: B1\nlang: fa\n---\n$body"
            for (name in listOf("book.md", "book.MD", "book.markdown")) {
                val id = ImportRepository(db, Languages.FRENCH, "fa").text(raw, name)
                val stored = assertNotNull(db.texts().getById(id))
                assertEquals(body, stored.body)
                assertEquals("My book", stored.title)
                assertEquals("B1", stored.level)
                assertEquals(Languages.FRENCH.code, stored.lang)
                val blocks = paragraphs(stored.body).map { it.block }
                assertEquals(BlockType.HEADER, blocks[0].type)
                assertTrue(blocks[1].emphasisSpans.any { it.bold })
                assertTrue(blocks[1].emphasisSpans.any { it.italic })
                assertEquals(BlockType.LIST_ITEM, blocks[2].type)
                assertEquals(BlockType.TABLE, blocks[3].type)
            }
        } finally { db.close() }
    }

    @Test fun textImportsRetainTextAndRejectEmptyFiles() = runTest {
        val db = buildDatabase(Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext()))
        try {
            val repository = ImportRepository(db, Languages.FRENCH, "fa")
            val id = repository.text("  A normal paragraph.  ", "normal.txt")
            assertEquals("A normal paragraph.", assertNotNull(db.texts().getById(id)).body)
            kotlin.test.assertFailsWith<IllegalArgumentException> { repository.text("  \n ", "empty.md") }
        } finally { db.close() }
    }
}
