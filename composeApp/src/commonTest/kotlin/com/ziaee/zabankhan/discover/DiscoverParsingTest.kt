package com.ziaee.zabankhan.discover

import kotlin.test.*
import kotlin.time.Instant

class DiscoverParsingTest {
    @Test fun atomUsesAlternateLinkAndCdata() {
        val feed = """<feed><entry><title><![CDATA[News &amp; culture]]></title><link rel="self" href="https://example.org/api/1"/><link rel="alternate" href="https://example.org/story"/><summary><![CDATA[<p>A <b>short</b> summary.</p>]]></summary><updated>2026-09-16T07:37:29Z</updated><id>unique</id></entry></feed>"""
        val item = NewsFetcher.parseItems(feed).single()
        assertEquals("News & culture", item.title)
        assertEquals("A short summary.", item.snippet)
        assertEquals("https://example.org/story", item.link)
        assertEquals("unique", item.guid)
        assertEquals(Instant.parse("2026-09-16T07:37:29Z").toEpochMilliseconds(), item.publishedAtMs)
    }
    @Test fun invalidLinksAreNotImported() {
        assertTrue(NewsFetcher.parseItems("<rss><item><title>Unsafe</title><link>javascript:alert(1)</link></item></rss>").isEmpty())
        assertTrue(NewsFetcher.parseItems("<rss/>", 0).isEmpty())
    }
    @Test fun fallbackChoosesLargestParagraphClusterAndSkipsChrome() {
        val html = """<body><nav><p>Navigation that should never be read.</p></nav><div><p>Short aside.</p></div><article><p>A long first paragraph with the actual article text.</p><p>A second paragraph provides additional information.</p></article><footer><p>Copyright and menu.</p></footer></body>"""
        assertEquals("A long first paragraph with the actual article text.\n\nA second paragraph provides additional information.", ArticleExtractor.extractFallback(html))
    }
    @Test fun fallbackReturnsNullWithoutParagraphs() { assertNull(ArticleExtractor.extractFallback("<script>ignore</script><div>menu</div>")) }
    @Test fun mergerUpgradesAndDeduplicatesUrlsPreferringImage() {
        val first = NewsItem("First", "Summary", "http://EXAMPLE.org/a/#tracking", "1", 1)
        val image = first.copy(link = "https://example.org/a", imageUrl = "https://example.org/image.png")
        val newer = first.copy(title = "New", link = "https://example.org/b", publishedAtMs = 2)
        assertEquals(listOf(newer, image), mergeHeadlines(listOf(first), listOf(image, newer), limit = 10))
        assertTrue(mergeHeadlines(listOf(first), limit = 0).isEmpty())
    }
    @Test fun fallbackJoinsParagraphsInsideIndividualDivWrappers() {
        val html = """<body><div class="sidebar"><p>Short link.</p></div><div class="story"><div class="paragraph"><p>First complete paragraph of the story.</p></div><div class="paragraph"><p>Second complete paragraph of the story.</p></div></div></body>"""
        assertEquals("First complete paragraph of the story.\n\nSecond complete paragraph of the story.", ArticleExtractor.extractFallback(html))
    }
    @Test fun skipsMediaOnlyFeedEntriesWithoutTeasers() {
        val xml = """<rss><item><title>Audio player</title><description></description><link>https://example.org/audio</link></item><item><title>Article</title><description>A real teaser.</description><link>https://example.org/article</link></item></rss>"""
        assertEquals("Article", NewsFetcher.parseItems(xml).single().title)
    }
    @Test fun missingWikiExtractDoesNotBecomeAnArticle() {
        assertNull(MediaWikiClient.parseArticle("""{"query":{"pages":{"1":{"title":"Missing","missing":""}}}}""", 1))
    }
}
