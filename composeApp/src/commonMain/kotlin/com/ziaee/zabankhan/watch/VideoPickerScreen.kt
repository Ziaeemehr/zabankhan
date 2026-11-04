package com.ziaee.zabankhan.watch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.*
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

fun watchRoute(videoId: String = "", path: String = "", subs: String = "") =
    "watch?videoId=${percentEncode(videoId)}&path=${percentEncode(path)}&subs=${percentEncode(subs)}"

@Composable fun VideoPickerScreen(vm: AppViewModel, settings: AppSettings, back: () -> Unit, open: (String, String, String) -> Unit) {
    val lang = Languages.learnable.firstOrNull { it.code == settings.learningLang } ?: return
    var url by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var localPath by rememberSaveable { mutableStateOf("") }
    var subtitlePath by rememberSaveable { mutableStateOf("") }
    var importFailed by remember { mutableStateOf(false) }
    var channel by remember(lang) { mutableStateOf<VideoChannel?>(null) }
    var videos by remember(channel) { mutableStateOf(emptyList<ChannelVideo>()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var recent by remember(lang) { mutableStateOf(emptyList<RecentVideo>()) }
    val pickVideo = rememberMediaPicker(MediaFileKind.VIDEO) { path -> if (path != null) { localPath = path; subtitlePath = ""; importFailed = false } }
    val pickSubtitles = rememberMediaPicker(MediaFileKind.SUBTITLES) { path -> if (path != null) subtitlePath = path }
    LaunchedEffect(lang) { recent = WatchHistory(vm.container.cache).read(lang) }
    LaunchedEffect(channel, retry) {
        val selected = channel ?: return@LaunchedEffect
        loading = true; failed = false
        try { videos = fetchChannelVideos(vm.container.http, selected.channelId) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item { TextButton(onClick = back) { Text(stringResource(Res.string.back)) } }
        item { Heading(stringResource(Res.string.watch_video)) }
        item { OutlinedTextField(url, { url = it; invalid = false }, label = { Text(stringResource(Res.string.watch_url)) }, isError = invalid, modifier = Modifier.fillMaxWidth(), textStyle = contentStyle(url), singleLine = true) }
        if (invalid) item { Text(stringResource(Res.string.watch_invalid_url), color = MaterialTheme.colorScheme.error) }
        item { Button(onClick = { val id = YoutubeUrl.videoId(url); if (id != null) open(id, "", "") else invalid = true }) { Text(stringResource(Res.string.watch_open_youtube)) } }
        item { OutlinedButton(onClick = pickVideo) { Text(stringResource(Res.string.watch_open_local)) } }
        if (importFailed) item { Text(stringResource(Res.string.watch_import_error)) }
        if (localPath.isNotBlank()) {
            item { Text(stringResource(Res.string.watch_local_ready)) }
            item { OutlinedButton(onClick = pickSubtitles) { Text(stringResource(Res.string.watch_add_subtitles)) } }
            if (subtitlePath.isNotBlank()) item { Text(stringResource(Res.string.watch_subtitles_ready)) }
            item { Button(onClick = { open("", localPath, subtitlePath) }) { Text(stringResource(Res.string.watch_play)) } }
        }
        if (recent.isNotEmpty()) item { Heading(stringResource(Res.string.watch_history)) }
        items(recent) { video ->
            ElevatedCard(onClick = { open(video.videoId, video.path.orEmpty(), video.subs.orEmpty()) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { Text(video.title, style = contentStyle(video.title)); Text(videoTime(video.positionMs), style = MaterialTheme.typography.labelSmall) }
            }
        }
        item { Heading(stringResource(Res.string.watch_channels)) }
        item { Choice(stringResource(Res.string.watch_channels), channel?.channelId.orEmpty(), listOf("" to stringResource(Res.string.watch_choose_channel)) + VideoCatalog.channels(lang).map { it.channelId to it.title }) { id -> channel = VideoCatalog.channels(lang).firstOrNull { it.channelId == id } } }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (failed) item { Text(stringResource(Res.string.watch_feed_error)); TextButton(onClick = { retry++ }) { Text(stringResource(Res.string.retry)) } }
        items(videos, key = { it.videoId }) { video ->
            ElevatedCard(onClick = { open(video.videoId, "", "") }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { Text(video.title, style = contentStyle(video.title)); Text(video.published.take(10), style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
