package com.ziaee.zabankhan.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

fun watchRoute(videoId: String = "", path: String = "", subs: String = "") =
    "watch?videoId=${percentEncode(videoId)}&path=${percentEncode(path)}&subs=${percentEncode(subs)}"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable fun VideoPickerScreen(vm: AppViewModel, settings: AppSettings, back: () -> Unit, open: (String, String, String) -> Unit) {
    val lang = Languages.learnable.firstOrNull { it.code == settings.learningLang } ?: return
    val videoChannels = remember(lang) { VideoCatalog.channels(lang) }
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var levelMenu by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var localPath by rememberSaveable { mutableStateOf("") }
    var subtitlePath by rememberSaveable { mutableStateOf("") }
    var importFailed by remember { mutableStateOf(false) }
    var selectedChannel by rememberSaveable(lang) { mutableStateOf<String?>(null) }
    // shortcut: feeds live only while this screen is shown; add a disk cache if channel lists load too slowly.
    var feeds by remember(lang) { mutableStateOf(emptyMap<String, List<ChannelVideo>>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var recent by remember(lang) { mutableStateOf(emptyList<RecentVideo>()) }
    val scope = rememberCoroutineScope()
    val history = remember(vm) { WatchHistory(vm.container.cache) }
    val language = Locale.current.language
    val pickVideo = rememberMediaPicker(MediaFileKind.VIDEO) { path -> if (path != null) { localPath = path; subtitlePath = ""; importFailed = false } }
    val pickSubtitles = rememberMediaPicker(MediaFileKind.SUBTITLES) { path -> if (path != null) subtitlePath = path }
    LaunchedEffect(lang) { recent = history.read(lang) }
    val selected = videoChannels.firstOrNull { it.channelId == selectedChannel }
    LaunchedEffect(selected, retry) {
        val channel = selected ?: return@LaunchedEffect
        loading = true; error = false
        try { feeds = feeds + (channel.channelId to fetchChannelVideos(vm.container.http, channel.channelId)) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
        finally { loading = false }
    }
    BackHandler(selected != null) { selectedChannel = null }
    val search = normalizeForMatch(query.trim(), lang)
    val channels = searchVideoChannels(videoChannels, query, level, feeds, lang)
    if (linkDialog) WatchLinkDialog(onDismiss = { linkDialog = false }, onOpen = {
        linkDialog = false
        open(it, "", "")
    })
    Column(Modifier.fillMaxSize()) {
        EditorialTopAppBar(selected?.title ?: stringResource(Res.string.videos_title, languageName(lang)),
            back = { if (selected != null) selectedChannel = null else back() })
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                label = { Text(stringResource(Res.string.videos_search)) }, textStyle = contentStyle(query))
            IconButton(onClick = { linkDialog = true }) {
                Icon(Icons.Default.Link, stringResource(Res.string.videos_paste_link))
            }
        }
        // Wraps instead of scrolling sideways; local import is the secondary entry beside the level filter.
        FlowRow(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
            Box {
                TextButton(onClick = { levelMenu = true }) {
                    Text(stringResource(Res.string.videos_level, level ?: stringResource(Res.string.videos_all)))
                }
                DropdownMenu(expanded = levelMenu, onDismissRequest = { levelMenu = false }) {
                    listOf(null, "A1–A2", "B1", "B2", "C1+").forEach { option ->
                        DropdownMenuItem(text = { Text(option ?: stringResource(Res.string.videos_all)) }, onClick = {
                            level = option; levelMenu = false
                        })
                    }
                }
            }
            TextButton(onClick = pickVideo) { Text(stringResource(Res.string.watch_open_local)) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (importFailed) item { Text(stringResource(Res.string.watch_import_error), color = MaterialTheme.colorScheme.error) }
            if (localPath.isNotBlank()) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                    Text(stringResource(if (subtitlePath.isBlank()) Res.string.watch_local_ready else Res.string.watch_subtitles_ready),
                        Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = pickSubtitles) { Text(stringResource(Res.string.watch_add_subtitles)) }
                    Button(onClick = { open("", localPath, subtitlePath) }) { Text(stringResource(Res.string.watch_play)) }
                }
            }
            if (selected == null) {
                val recents = recent.filter { item ->
                    val channel = videoChannels.firstOrNull { it.title == item.channel }
                    videoLevelOverlaps(channel?.level, level) && (search.isBlank() ||
                        normalizeForMatch("${item.title} ${item.channel}", lang).contains(search))
                }
                if (recents.isNotEmpty()) {
                    item { Text(stringResource(Res.string.videos_continue), style = MaterialTheme.typography.titleMedium) }
                    items(recents, key = { "recent:${it.path ?: it.videoId}" }) { item ->
                        RecentVideoRow(item, onWatch = { open(item.videoId, item.path.orEmpty(), item.subs.orEmpty()) },
                            onRemove = { scope.launch { recent = history.remove(lang, item) } })
                    }
                }
                item { Text(stringResource(Res.string.videos_channels), style = MaterialTheme.typography.titleMedium) }
                items(channels, key = { it.channelId }) { channel ->
                    Surface(onClick = { selectedChannel = channel.channelId }, shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(channel.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall.merge(contentStyle(channel.title)))
                                channel.level?.let { value ->
                                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                        Text(value, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            val description = channel.description[language] ?: channel.description["en"]
                            if (description != null) Text(description, Modifier.fillMaxWidth(), maxLines = 1,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.merge(contentStyle(description)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (channels.isEmpty()) item { Text(stringResource(Res.string.videos_no_results)) }
            } else {
                if (loading) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                if (error) item {
                    Column {
                        Text(stringResource(Res.string.watch_feed_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { retry++ }) { Text(stringResource(Res.string.retry)) }
                    }
                }
                val videos = if (videoLevelOverlaps(selected.level, level))
                    searchChannelVideos(feeds[selected.channelId].orEmpty(), query, lang) else emptyList()
                items(videos, key = { it.videoId }) { video ->
                    VideoRow(video.title, video.published.take(10), onClick = { open(video.videoId, "", "") })
                }
                if (!loading && !error && videos.isEmpty()) item { Text(stringResource(Res.string.videos_no_results)) }
            }
        }
    }
}

@Composable
private fun RecentVideoRow(video: RecentVideo, onWatch: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onWatch), verticalAlignment = Alignment.CenterVertically) {
        VideoRow(video.title.ifBlank { video.videoId },
            stringResource(Res.string.videos_progress, videoResumeTime(video.positionMs)) +
                video.channel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
            modifier = Modifier.weight(1f), onClick = onWatch)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(Res.string.videos_options)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.videos_remove)) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun VideoRow(title: String, support: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VideoThumbnail(Modifier.width(112.dp))
        Column(Modifier.weight(1f)) {
            Text(title, Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.merge(contentStyle(title)))
            Text(support, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** shortcut: the project has no network image loader, so a 16:9 tinted frame stands in for the YouTube thumbnail; add one to show real thumbnails. */
@Composable
internal fun VideoThumbnail(modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WatchLinkDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(Res.string.watch_action)) }, text = {
        Column {
            OutlinedTextField(value = link, onValueChange = { link = it; invalid = false },
                label = { Text(stringResource(Res.string.watch_link_hint)) }, isError = invalid, singleLine = true)
            if (invalid) Text(stringResource(Res.string.watch_invalid_link), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        TextButton(onClick = {
            val id = YoutubeUrl.videoId(link)
            if (id == null) invalid = true else onOpen(id)
        }) { Text(stringResource(Res.string.watch_open)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } })
}
