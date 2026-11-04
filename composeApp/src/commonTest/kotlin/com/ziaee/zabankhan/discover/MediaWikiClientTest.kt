package com.ziaee.zabankhan.discover

import kotlin.test.assertEquals
import kotlin.test.Test

class MediaWikiClientTest {
    @Test
    fun convertsMediawikiWikitextHeadingsToMarkdownHeadings() {
        val json = """
            {
              "query": {
                "pages": {
                  "8826": {
                    "pageid": 8826,
                    "title": "Espace",
                    "extract": "L'espace est l'étendue.\n\n\n== La conquête de l'espace ==\n\nLes humains ont été fascinés.\n\n\n== Notes ==\n\n\n== Voir aussi ==\nConquête de l'espace",
                    "revisions": [ { "timestamp": "2026-03-30T20:04:50Z" } ]
                  }
                }
              }
            }
        """.trimIndent()

        val article = MediaWikiClient.parseArticle(json, pageId = 8826)

        requireNotNull(article)
        assertEquals(
            "L'espace est l'étendue.\n\n\n## La conquête de l'espace\n\nLes humains ont été fascinés.\n\n\n## Notes\n\n\n## Voir aussi\nConquête de l'espace",
            article.text
        )
    }

    @Test
    fun leavesPlainTextWithNoHeadingMarkupUnchanged() {
        val json = """
            {
              "query": {
                "pages": {
                  "123": {
                    "pageid": 123,
                    "title": "Test",
                    "extract": "Une phrase normale sans en-tête.",
                    "revisions": []
                  }
                }
              }
            }
        """.trimIndent()

        val article = MediaWikiClient.parseArticle(json, pageId = 123)

        requireNotNull(article)
        assertEquals("Une phrase normale sans en-tête.", article.text)
    }

    // Trimmed real response shape from
    // https://fr.vikidia.org/w/api.php?action=query&list=search&srsearch=espace&srlimit=2&srprop=snippet|wordcount&format=json
    private val sampleSearchJson = """
        {
          "batchcomplete": "",
          "query": {
            "searchinfo": { "totalhits": 1857 },
            "search": [
              {
                "ns": 0,
                "title": "Espace",
                "pageid": 8826,
                "wordcount": 457,
                "snippet": "dans la course à <span class=\"searchmatch\">l'espace</span>. À ne pas confondre."
              },
              {
                "ns": 0,
                "title": "Superficie",
                "pageid": 8218,
                "wordcount": 227,
                "snippet": "comme un terrain (un jardin, un champ...)"
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun parsesTitlesPageidsWordcountsAndStripsSnippetMarkup() {
        val results = MediaWikiClient.parseSearchResults(sampleSearchJson)

        assertEquals(2, results.size)
        assertEquals(WikiSearchResult(
            pageId = 8826,
            title = "Espace",
            snippet = "dans la course à l'espace. À ne pas confondre.",
            wordCount = 457
        ), results[0])
        assertEquals("Superficie", results[1].title)
        assertEquals(227, results[1].wordCount)
    }

    @Test
    fun returnsEmptyListWhenSearchArrayIsMissing() {
        val results = MediaWikiClient.parseSearchResults("""{"query": {}}""")
        assertEquals(emptyList<WikiSearchResult>(), results)
    }

    // Trimmed real response shape from
    // https://fr.vikidia.org/w/api.php?action=query&prop=extracts|revisions&explaintext=1&rvprop=timestamp&pageids=8826&format=json
    private val sampleArticleJson = """
        {
          "query": {
            "pages": {
              "8826": {
                "pageid": 8826,
                "ns": 0,
                "title": "Espace",
                "extract": "L'espace est l'étendue qui sépare les planètes.",
                "revisions": [ { "timestamp": "2026-03-30T20:04:50Z" } ]
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun parsesArticleExtractAndRevisionTimestamp() {
        val article = MediaWikiClient.parseArticle(sampleArticleJson, pageId = 8826)

        requireNotNull(article)
        assertEquals("Espace", article.title)
        assertEquals("L'espace est l'étendue qui sépare les planètes.", article.text)
        assertEquals(1774901090000L, article.publishedAtMs) // 2026-03-30T20:04:50Z
    }

    @Test
    fun returnsNullWhenTheRequestedPageIdIsMissing() {
        val article = MediaWikiClient.parseArticle("""{"query": {"pages": {}}}""", pageId = 8826)
        assertEquals(null, article)
    }

    @Test
    fun articleUrlBuildsAWikiLinkWithUnderscoresAndEncoding() {
        assertEquals(
            "https://example.org/wiki/Conqu%C3%AAte_de_l%27espace",
            WikiSource("test", "Test", "example.org").articleUrl("Conquête de l'espace")
        )
    }
}
