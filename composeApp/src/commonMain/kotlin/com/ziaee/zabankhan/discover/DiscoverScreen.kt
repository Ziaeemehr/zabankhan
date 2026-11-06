package com.ziaee.zabankhan.discover

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.data.TextDocument
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.*
import com.ziaee.zabankhan.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private data class Preview(val title: String, val snippet: String, val url: String, val source: ContentSource, val pageId: Int? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DiscoverScreen(vm: AppViewModel, settings: AppSettings, back: () -> Unit, open: (Long) -> Unit) {
    val lang = requireNotNull(settings.learningLang)
    val sources = ContentSources.forLanguage(requireNotNull(Languages.byCode(lang)))
    var sourceId by remember(lang) { mutableStateOf(sources.firstOrNull()?.id.orEmpty()) }
    val source = sources.firstOrNull { it.id == sourceId }
    var query by remember(lang) { mutableStateOf("") }
    var headlines by remember(lang, sourceId) { mutableStateOf(emptyList<NewsItem>()) }
    var results by remember(lang, sourceId) { mutableStateOf(emptyList<WikiSearchResult>()) }
    var preview by remember(lang, sourceId) { mutableStateOf<Preview?>(null) }
    var busy by remember(lang, sourceId) { mutableStateOf(false) }
    var error by remember(lang, sourceId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val http = vm.container.http
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(lang, sourceId, refresh) {
        if (source is NewsSource) {
            busy = true
            error = false
            try { headlines = mergeHeadlines(NewsFetcher(http).fetchItems(source.feedUrl, 50), limit = 50) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    Page {
        EditorialTopAppBar(stringResource(Res.string.discover_title), back)
        Choice(stringResource(Res.string.discover_source), sourceId, sources.map { it.id to it.name }) { sourceId = it }
        if (source is WikiSource) {
            EditorialSearchEntry(query, { query = it })
            Button(enabled = query.isNotBlank() && !busy, onClick = {
                val selected = source
                scope.launch {
                    busy = true; error = false
                    try {
                        val found = MediaWikiClient(http, selected).search(query)
                        if (sourceId == selected.id) results = found
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                    finally { busy = false }
                }
            }) { Text(stringResource(Res.string.search)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error) {
            Text(stringResource(Res.string.discover_error), color = MaterialTheme.colorScheme.error)
            if (source is NewsSource) TextButton(onClick = { refresh++ }) { Text(stringResource(Res.string.retry)) }
        }
        if (!busy && !error && headlines.isEmpty() && results.isEmpty()) EditorialEmptyState(Icons.Default.Explore,
            stringResource(Res.string.discover_empty), stringResource(Res.string.discover_hint), Modifier.heightIn(min = 240.dp))
        if (headlines.isNotEmpty() || results.isNotEmpty()) EditorialSectionHeader(source?.name.orEmpty())
        headlines.forEach { item ->
            EditorialCard(onClick = { source?.let { preview = Preview(item.title, item.snippet, item.link, it) } }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    source?.let { MetadataBadge(it.name) }
                    Text(item.title, modifier = Modifier.fillMaxWidth(), style = editorialStyle(item.title))
                    Text(item.snippet, modifier = Modifier.fillMaxWidth(), maxLines = 3, style = contentStyle(item.snippet))
                }
            }
        }
        results.forEach { item ->
            EditorialCard(onClick = { (source as? WikiSource)?.let { preview = Preview(item.title, item.snippet, it.articleUrl(item.title), it, item.pageId) } }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    source?.let { MetadataBadge(it.name) }
                    Text(item.title, modifier = Modifier.fillMaxWidth(), style = editorialStyle(item.title))
                    Text(item.snippet, modifier = Modifier.fillMaxWidth(), maxLines = 3, style = contentStyle(item.snippet))
                    Text(stringResource(Res.string.discover_word_count, item.wordCount), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    preview?.let { item ->
        ModalBottomSheet(onDismissRequest = { if (!busy) preview = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetadataBadge(item.source.name)
                Text(item.title, modifier = Modifier.fillMaxWidth(), style = editorialStyle(item.title, EditorialRole.SectionTitle))
                Text(item.snippet, modifier = Modifier.fillMaxWidth(), style = contentStyle(item.snippet))
                Text(stringResource(Res.string.discover_preview_hint), style = MaterialTheme.typography.bodySmall)
                if (error) Text(stringResource(Res.string.discover_error), color = MaterialTheme.colorScheme.error)
                EditorialPrimaryButton(stringResource(Res.string.discover_add_library), loading = busy, onClick = {
                    scope.launch {
                        busy = true; error = false
                        try {
                            val key = "discover:$lang:${normalizeArticleUrl(item.url)}"
                            val existing = vm.container.texts.getByExternalKey(key)
                            val id = existing?.id ?: run {
                                val wiki = item.source as? WikiSource
                                val body = if (wiki != null && item.pageId != null) MediaWikiClient(http, wiki).fetchArticle(item.pageId)?.text
                                    else ArticleExtractor.extract(http.contentGet(item.url))
                                check(!body.isNullOrBlank()) { "No article text" }
                                vm.container.texts.insert(TextDocument(lang = lang, title = item.title, body = body, sourceUrl = item.url, sourceName = item.source.name, externalKey = key))
                            }
                            preview = null
                            open(id)
                        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                        finally { busy = false }
                    }
                })
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
