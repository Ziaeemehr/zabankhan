package com.ziaee.zabankhan.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.serialization.json.*
import kotlin.test.*

/** Runs the actual generated Room migration against the exported v4 database. */
class BatchAMigrationTest {
    private fun schema(version: Int): JsonObject {
        val relative = "schemas/com.ziaee.zabankhan.data.AppDatabase/$version.json"
        val file = listOf(File(relative), File("composeApp/$relative")).first { it.exists() }
        return Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
    }
    private fun create(db: SQLiteConnection, version: Int) {
        schema(version).getValue("entities").jsonArray.forEach { value ->
            val entity = value.jsonObject
            val table = entity.getValue("tableName").jsonPrimitive.content
            db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity["indices"]?.jsonArray.orEmpty().forEach { index ->
                db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
    }
    private fun rows(db: SQLiteConnection, sql: String, dropFirst: Boolean = false): Set<List<String?>> = db.prepare(sql).use { s ->
        buildSet { while (s.step()) add(((if (dropFirst) 1 else 0) until s.getColumnCount()).map { if (s.isNull(it)) null else s.getText(it).takeUnless { value -> value == "NULL" } }) }
    }
    @Test fun migratesFourToFivePreservingEnglishDataAndMatchingExportedSchema() {
        BundledSQLiteDriver().open(":memory:").use { actual ->
            BundledSQLiteDriver().open(":memory:").use { expected ->
                create(actual, 4)
                create(expected, 5)
                actual.execSQL("INSERT INTO texts (id,lang,title,body,createdAtMs,lastAccessedAtMs,lastOffset,pinned,readMark) VALUES (7,'en','Book','Body',1,2,3,1,0)")
                actual.execSQL("INSERT INTO vocab (id,lang,word,sentence,meaningLang,learned,createdAtMs,leitnerBox,nextReviewAtMs) VALUES (8,'en','book','A book','fa',0,1,3,12)")
                AppDatabase_AutoMigration_4_5_Impl().migrate(actual)
                assertEquals(setOf(listOf("en", "Book", "Body", "3", "1", null, "", "fa")), rows(actual, "SELECT lang,title,body,lastOffset,pinned,author,voice,translationLang FROM texts WHERE id=7"))
                assertEquals(setOf(listOf("en", "book", "3", "12")), rows(actual, "SELECT lang,word,leitnerBox,nextReviewAtMs FROM vocab WHERE id=8"))
                schema(5).getValue("entities").jsonArray.forEach { entity ->
                    val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                    assertEquals(rows(expected, "PRAGMA table_info(`$table`)", true), rows(actual, "PRAGMA table_info(`$table`)", true), table)
                    assertEquals(rows(expected, "PRAGMA foreign_key_list(`$table`)"), rows(actual, "PRAGMA foreign_key_list(`$table`)"), table)
                    assertEquals(rows(expected, "SELECT name,sql FROM sqlite_master WHERE type='index' AND tbl_name='$table'"), rows(actual, "SELECT name,sql FROM sqlite_master WHERE type='index' AND tbl_name='$table'"), table)
                }
                actual.execSQL("INSERT INTO library_tags (lang,name,createdAtMs) VALUES ('en','Reading',1),('fr','Reading',1)")
                assertEquals(setOf(listOf("2")), rows(actual, "SELECT COUNT(*) FROM library_tags"))
                assertEquals(emptySet(), rows(actual, "PRAGMA foreign_key_check"))
            }
        }
    }
}
