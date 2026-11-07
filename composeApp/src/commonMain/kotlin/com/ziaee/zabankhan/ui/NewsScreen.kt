package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.data.TextDocument
import com.ziaee.zabankhan.discover.*
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

// Ported from French Reader's ui/home/NewsScreen.kt; headlines come from the learning language's ContentSources.

data class Headline(val item: NewsItem, val source: NewsSource)

private fun personalizeNews(headlines: List<Headline>, rawKeywords: String, onlyMatching: Boolean): List<Headline> {
    val keywords = rawKeywords.split(',', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    if (keywords.isEmpty()) return headlines
    val (matching, other) = headlines.partition { h -> keywords.any((h.item.title + " " + h.item.snippet).lowercase()::contains) }
    return when { onlyMatching -> matching; else -> matching + other }
}

/** Merged headlines of every news source of a language; [failed] counts sources that could not be refreshed. */
internal data class NewsLoad(val headlines: List<Headline>, val failed: Int)

internal suspend fun loadNews(sources: List<NewsSource>, fetch: suspend (NewsSource) -> List<NewsItem>): NewsLoad = coroutineScope {
    val results = sources.map { source ->
        async {
            try { fetch(source).map { Headline(it, source) } }
            catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
    }.awaitAll()
    val bySource = results.filterNotNull()
    val merged = mergeHeadlines(*bySource.map { list -> list.map { it.item } }.toTypedArray(), limit = 50)
    // mergeHeadlines keeps the first copy of a duplicate link, so its source wins too.
    val sourceOf = bySource.flatten().distinctBy { normalizeArticleUrl(it.item.link) }.associate { normalizeArticleUrl(it.item.link) to it.source }
    NewsLoad(merged.mapNotNull { item -> sourceOf[normalizeArticleUrl(item.link)]?.let { Headline(item, it) } }, results.count { it == null })
}

/** The external key Discover also uses, so an article saved from either screen is found again. */
internal fun newsExternalKey(lang: String, url: String) = "discover:$lang:${normalizeArticleUrl(url)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit, onOpenText: (Long) -> Unit) {
    val lang = requireNotNull(settings.learningLang)
    val spec = requireNotNull(Languages.byCode(lang))
    val sources = remember(spec) { ContentSources.forLanguage(spec).filterIsInstance<NewsSource>() }
    var refresh by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(true) }
    var load by remember(lang) { mutableStateOf(NewsLoad(emptyList(), 0)) }
    var selected by remember { mutableStateOf<Headline?>(null) }
    var existingId by remember { mutableStateOf<Long?>(null) }
    var importing by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf(false) }
    var category by remember(lang) { mutableStateOf(settings.newsCategories[lang] ?: "all") }
    var keywords by remember(lang) { mutableStateOf(settings.newsKeywords[lang].orEmpty()) }
    var categoryMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val http = vm.container.http
    LaunchedEffect(lang, refresh) {
        isRefreshing = true
        val stored = vm.container.database.headlines().getRecent(lang, 100).mapNotNull { row ->
            val source = sources.firstOrNull { it.id == row.sourceId } ?: return@mapNotNull null
            Headline(NewsItem(row.title, row.snippet, row.articleUrl, row.externalId, row.publishedAtMs, row.imageUrl), source)
        }
        load = load.copy(headlines = stored)
        val fresh = loadNews(sources) { source ->
            val items = NewsFetcher(http).fetchItems(source.feedUrl, 25)
            vm.container.database.headlines().replaceSource(lang, source.id, items.map { item ->
                com.ziaee.zabankhan.data.HeadlineEntity(source.id, source.name, item.guid, item.title, item.snippet, item.link,
                    item.imageUrl, item.publishedAtMs, com.ziaee.zabankhan.data.nowMs(), lang)
            })
            items
        }
        load = if (fresh.headlines.isEmpty() && fresh.failed > 0) NewsLoad(stored, fresh.failed) else fresh
        isRefreshing = false
    }
    LaunchedEffect(selected) {
        existingId = selected?.let { vm.container.texts.getByExternalKey(newsExternalKey(lang, it.item.link))?.id }
    }
    Column(Modifier.fillMaxSize()) {
        EditorialTopAppBar(stringResource(Res.string.home_section_today_news), onBack)
        if (sources.isEmpty()) {
            val language = languageName(spec)
            EditorialEmptyState(Icons.Default.Newspaper, stringResource(Res.string.news_no_sources_title, language),
                stringResource(Res.string.news_no_sources_body), Modifier.fillMaxSize())
            return@Column
        }
        val visible = personalizeNews(load.headlines.filter { category == "all" || category in it.source.categories }, keywords, settings.newsOnlyMatching)
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { refresh++ }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                item {
                    val categories = (listOf("all") + sources.flatMap { it.categories }).distinct()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            TextButton(onClick = { categoryMenu = true }) { Text(category) }
                            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                                categories.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = {
                                    category = value; categoryMenu = false; scope.launch { vm.container.settings.setNewsCategory(lang, value) }
                                }) }
                            }
                        }
                    }
                    OutlinedTextField(keywords, { value -> keywords = value; scope.launch { vm.container.settings.setNewsKeywords(lang, value) } },
                        modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.batch_e_news_keywords)) })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(settings.newsOnlyMatching, { value -> scope.launch { vm.container.settings.setNewsOnlyMatching(value) } })
                        Text(stringResource(Res.string.batch_e_news_only_matching))
                    }
                }
                item { NewsStatus(load.failed > 0 && load.headlines.isNotEmpty(), !isRefreshing && load.headlines.isEmpty(), { refresh++ }) }
                items(visible, key = { it.source.id + it.item.guid }) { headline ->
                    NewsHeadlineRow(headline, onClick = { previewError = false; selected = headline })
                }
            }
        }
    }
    selected?.let { headline ->
        NewsPreviewSheet(
            headline = headline, isImporting = importing, hasError = previewError, isAlreadyDownloaded = existingId != null,
            onDownloadOrOpen = {
                scope.launch {
                    importing = true; previewError = false
                    try {
                        val id = existingId ?: run {
                            val body = ArticleExtractor.extract(http.contentGet(headline.item.link))
                            check(!body.isNullOrBlank()) { "No article text" }
                            val document = TextDocument(lang = lang, title = headline.item.title, body = body,
                                sourceUrl = headline.item.link, sourceName = headline.source.name, externalKey = newsExternalKey(lang, headline.item.link))
                            val documentId = vm.container.texts.insert(document)
                            // French Reader ArticleImportRepository.persistImage: image failure never loses the article.
                            headline.item.imageUrl?.let { url ->
                                val relative = com.ziaee.zabankhan.images.ArticleImageStore(http).downloadAndStore(documentId, url)
                                if (relative != null) vm.container.texts.update(document.copy(id = documentId,
                                    imagePath = "${com.ziaee.zabankhan.pdf.AppFiles.filesDir}/$relative"))
                            }
                            documentId
                        }
                        selected = null
                        onOpenText(id)
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { previewError = true }
                    finally { importing = false }
                }
            },
            onDismiss = { if (!importing) selected = null },
        )
    }
}
