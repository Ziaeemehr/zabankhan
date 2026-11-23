package com.ziaee.zabankhan.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
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

fun watchRoute(videoId: String = "", path: String = "", subs: String = "", shadow: Boolean = false) =
    "watch?videoId=${percentEncode(videoId)}&path=${percentEncode(path)}&subs=${percentEncode(subs)}" +
        if (shadow) "&shadow=true" else ""

@Composable fun ShadowEntryScreen(vm: AppViewModel, settings: AppSettings, back: () -> Unit, choose: () -> Unit, resume: (RecentVideo) -> Unit) {
    val lang = Languages.learnable.firstOrNull { it.code == settings.learningLang } ?: return
    var recent by remember(lang) { mutableStateOf<RecentVideo?>(null) }
    LaunchedEffect(lang) { recent = WatchHistory(vm.container.cache).readShadow(lang) }
    Column(Modifier.fillMaxSize()) {
        EditorialTopAppBar(stringResource(Res.string.shadow_entry_title), back = back)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(34.dp))
                    }
                    Text(stringResource(Res.string.shadow_entry_intro), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Res.string.shadow_step_listen to Icons.Default.Hearing, Res.string.shadow_step_repeat to Icons.Default.Mic,
                            Res.string.shadow_step_compare to Icons.Default.CheckCircle).forEach { (label, icon) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
            recent?.let { video ->
                Text(stringResource(Res.string.shadow_entry_resume_label), style = MaterialTheme.typography.titleSmall)
                EditorialCard(onClick = {
                    resume(video.copy(
                        path = video.path?.let { com.ziaee.zabankhan.data.rebaseSavedVideoPath(it, com.ziaee.zabankhan.pdf.AppFiles.filesDir) },
                        subs = video.subs?.let { com.ziaee.zabankhan.data.rebaseSavedVideoPath(it, com.ziaee.zabankhan.pdf.AppFiles.filesDir) }))
                }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.PlayCircle, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(video.title.ifBlank { video.videoId }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Text(stringResource(Res.string.shadow_entry_new_label), style = MaterialTheme.typography.titleSmall)
            Button(onClick = choose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.VideoLibrary, null); Spacer(Modifier.width(8.dp)); Text(stringResource(Res.string.shadow_entry_choose))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable fun VideoPickerScreen(vm: AppViewModel, settings: AppSettings, back: () -> Unit, shadow: Boolean = false, open: (String, String, String) -> Unit) {
    val lang = Languages.learnable.firstOrNull { it.code == settings.learningLang } ?: return
    val savedChannels by vm.container.database.videoChannels().observe(lang.code).collectAsState(emptyList())
    val savedVideos by vm.container.database.savedVideos().observeFavorites(lang.code).collectAsState(emptyList())
    val allSavedVideos by vm.container.database.savedVideos().observeAll(lang.code).collectAsState(emptyList())
    val curatedChannels = remember(lang) { VideoCatalog.channels(lang) }
    val videoChannels = remember(curatedChannels, savedChannels) { mergeVideoChannels(curatedChannels,
        savedChannels.map { VideoChannel(it.channelId, it.title, it.level, category = it.category, personal = true) }) }
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var levelMenu by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var channelDialog by remember { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var channelError by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var localPath by rememberSaveable { mutableStateOf("") }
    var subtitlePath by rememberSaveable { mutableStateOf("") }
    var importFailed by remember { mutableStateOf(false) }
    var selectedChannel by rememberSaveable(lang) { mutableStateOf<String?>(null) }
    var showingFavorites by rememberSaveable(lang) { mutableStateOf(false) }
    // shortcut: feeds live only while this screen is shown; add a disk cache if channel lists load too slowly.
    var feeds by remember(lang) { mutableStateOf(emptyMap<String, List<ChannelVideo>>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var recent by remember(lang) { mutableStateOf(emptyList<RecentVideo>()) }
    val scope = rememberCoroutineScope()
    val savedVideoRepository = remember(vm) { com.ziaee.zabankhan.data.SavedVideoRepository(vm.container.database) }
    val playlistRepository = remember(vm) { com.ziaee.zabankhan.data.VideoPlaylistRepository(vm.container.database) }
    val playlists by remember(playlistRepository, lang) { playlistRepository.observe(lang.code) }.collectAsState(emptyList())
    var playlistManager by remember { mutableStateOf(false) }
    var playlistMenu by remember { mutableStateOf(false) }
    var playlistMembershipVideo by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by rememberSaveable(lang) { mutableStateOf<String?>(null) }
    val membershipRows by remember(playlistRepository, selectedPlaylist) {
        selectedPlaylist?.let(playlistRepository::memberships)
            ?: kotlinx.coroutines.flow.flowOf(emptyList<com.ziaee.zabankhan.data.VideoPlaylistMembershipEntity>())
    }.collectAsState(emptyList())
    val favoriteKeys = remember(savedVideos) { savedVideos.mapTo(mutableSetOf()) { it.stableKey } }
    if (playlistManager) VideoPlaylistManagerDialog(playlistRepository, lang.code) { playlistManager = false }
    playlistMembershipVideo?.let { key -> VideoPlaylistMembershipDialog(playlistRepository, lang.code, key) { playlistMembershipVideo = null } }
    fun toggleFavorite(videoId: String, path: String, subtitlePath: String, title: String, channel: String) {
        vm.action { savedVideoRepository.toggleFavorite(lang.code, videoId, path, subtitlePath, title, channel) }
    }
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
    BackHandler(selected != null || showingFavorites || selectedPlaylist != null) {
        if (selected != null) selectedChannel = null else if (showingFavorites) showingFavorites = false else selectedPlaylist = null
    }
    val search = normalizeForMatch(query.trim(), lang)
    val channels = searchVideoChannels(filterVideoChannelsByCategory(videoChannels, categoryFilter), query, level, feeds, lang)
    if (channelDialog) AddVideoChannelDialog(
        http = vm.container.http,
        onDismiss = { channelDialog = false },
        onSave = { channel, category ->
            val existing = vm.container.database.videoChannels().get(channel.channelId)
            if (existing != null || curatedChannels.any { it.channelId == channel.channelId }) channelError = true
            else {
                vm.container.database.videoChannels().upsert(com.ziaee.zabankhan.data.VideoChannelEntity(
                    channelId = channel.channelId, lang = lang.code, title = channel.title, category = category,
                ))
                channelDialog = false
            }
        }, duplicate = channelError,
    )
    if (linkDialog) WatchLinkDialog(onDismiss = { linkDialog = false }, onOpen = {
        linkDialog = false
        open(it, "", "")
    })
    Column(Modifier.fillMaxSize()) {
        EditorialTopAppBar(if (showingFavorites) stringResource(Res.string.videos_favorites) else selected?.title ?: stringResource(Res.string.videos_title, languageName(lang)),
            back = { if (selected != null) selectedChannel = null else if (showingFavorites) showingFavorites = false else if (selectedPlaylist != null) selectedPlaylist = null else back() })
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorialSearchEntry(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                hint = stringResource(if (showingFavorites) Res.string.videos_search_favorites else Res.string.videos_search),
            )
            IconButton(onClick = { linkDialog = true }) {
                Icon(Icons.Default.Link, stringResource(Res.string.videos_paste_link))
            }
        }
        // Wraps instead of scrolling sideways; local import is the secondary entry beside the level filter.
        FlowRow(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
            Box {
                TextButton(onClick = { playlistMenu = true }) { Text(playlists.firstOrNull { it.syncId == selectedPlaylist }?.name ?: stringResource(Res.string.videos_playlists)) }
                DropdownMenu(playlistMenu, onDismissRequest = { playlistMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(Res.string.video_playlists)) }, onClick = { playlistMenu = false; playlistManager = true })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.videos_all_videos)) }, onClick = { playlistMenu = false; selectedPlaylist = null; selectedChannel = null; showingFavorites = false })
                    playlists.forEach { playlist -> DropdownMenuItem(text = { Text(playlist.name) }, onClick = { playlistMenu = false; selectedPlaylist = playlist.syncId; showingFavorites = false; selectedChannel = null }) }
                }
            }
            TextButton(onClick = { showingFavorites = !showingFavorites; selectedChannel = null; selectedPlaylist = null }) {
                Text(stringResource(if (showingFavorites) Res.string.videos_all_videos else Res.string.videos_favorites))
            }
            if (!showingFavorites) {
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
            Box {
                TextButton(onClick = { categoryMenu = true }) { Text(stringResource(Res.string.videos_category_filter, stringResource(categoryLabel(categoryFilter)))) }
                DropdownMenu(categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    (listOf(null) + VideoCatalog.categories).forEach { category ->
                        DropdownMenuItem(text = { Text(stringResource(categoryLabel(category))) }, onClick = { categoryFilter = category; categoryMenu = false })
                    }
                }
            }
            TextButton(onClick = { channelError = false; channelDialog = true }) { Text(stringResource(Res.string.videos_add_channel)) }
            TextButton(onClick = pickVideo) { Text(stringResource(Res.string.watch_open_local)) }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (importFailed) item { Text(stringResource(Res.string.watch_import_error), color = MaterialTheme.colorScheme.error) }
            if (localPath.isNotBlank()) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                    Text(stringResource(if (subtitlePath.isBlank()) Res.string.watch_local_ready else Res.string.watch_subtitles_ready),
                        Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = pickSubtitles) { Text(stringResource(Res.string.watch_add_subtitles)) }
                    Button(onClick = { open("", localPath, subtitlePath) }) { Text(stringResource(Res.string.watch_play)) }
                }
            }
            if (showingFavorites) {
                val matches = savedVideos.filter { it.title.contains(query.trim(), ignoreCase = true) }
                items(matches, key = { it.stableKey }) { video ->
                    VideoRow(video.title.ifBlank { video.path.substringAfterLast('/').ifBlank { video.videoId } }, video.channel, vm.container.videoThumbnails, video.videoId,
                        isFavorite = true, favoriteLabel = stringResource(Res.string.videos_favorite_remove, video.title),
                        onToggleFavorite = { toggleFavorite(video.videoId, video.path, video.subtitlePath, video.title, video.channel) },
                        onPlaylist = { playlistMembershipVideo = video.stableKey },
                        onClick = { open(video.videoId,
                            com.ziaee.zabankhan.data.rebaseSavedVideoPath(video.path, com.ziaee.zabankhan.pdf.AppFiles.filesDir),
                            com.ziaee.zabankhan.data.rebaseSavedVideoPath(video.subtitlePath, com.ziaee.zabankhan.pdf.AppFiles.filesDir)) })
                }
                if (matches.isEmpty()) item { Text(stringResource(Res.string.videos_no_favorites)) }
            } else if (selected == null) {
                val playlistId = selectedPlaylist
                if (playlistId != null) {
                    val playlistVideos = allSavedVideos.filter { video -> membershipRows.any { it.videoKey == video.stableKey } && video.title.contains(query.trim(), ignoreCase = true) }
                    item { Text(playlists.firstOrNull { it.syncId == playlistId }?.name.orEmpty(), style = MaterialTheme.typography.titleMedium) }
                    items(playlistVideos, key = { "playlist:${it.stableKey}" }) { video ->
                        VideoRow(video.title.ifBlank { video.videoId.ifBlank { video.path } }, video.channel, vm.container.videoThumbnails, video.videoId,
                            isFavorite = video.isFavorite, favoriteLabel = stringResource(if (video.isFavorite) Res.string.videos_favorite_remove else Res.string.videos_favorite_add, video.title),
                            onToggleFavorite = { toggleFavorite(video.videoId, video.path, video.subtitlePath, video.title, video.channel) },
                            onPlaylist = { playlistMembershipVideo = video.stableKey }, onClick = { open(video.videoId,
                                com.ziaee.zabankhan.data.rebaseSavedVideoPath(video.path, com.ziaee.zabankhan.pdf.AppFiles.filesDir),
                                com.ziaee.zabankhan.data.rebaseSavedVideoPath(video.subtitlePath, com.ziaee.zabankhan.pdf.AppFiles.filesDir)) })
                    }
                } else {
                val recents = recent.filter { item ->
                    val channel = videoChannels.firstOrNull { it.title == item.channel }
                    videoLevelOverlaps(channel?.level, level) && (search.isBlank() ||
                        normalizeForMatch("${item.title} ${item.channel}", lang).contains(search))
                }
                if (recents.isNotEmpty()) {
                    item { Text(stringResource(Res.string.videos_continue), style = MaterialTheme.typography.titleMedium) }
                    items(recents, key = { "recent:${it.path ?: it.videoId}" }) { item ->
                        val key = if (item.videoId.isNotBlank() || !item.path.isNullOrBlank())
                            com.ziaee.zabankhan.data.savedVideoStableKey(lang.code, item.videoId, item.path.orEmpty()) else ""
                        RecentVideoRow(item, vm.container.videoThumbnails, onWatch = { open(item.videoId, item.path.orEmpty(), item.subs.orEmpty()) },
                            favorite = key in favoriteKeys,
                            favoriteLabel = stringResource(if (key in favoriteKeys) Res.string.videos_favorite_remove else Res.string.videos_favorite_add, item.title),
                            favoriteEnabled = key.isNotBlank(),
                            onToggleFavorite = { toggleFavorite(item.videoId, item.path.orEmpty(), item.subs.orEmpty(), item.title, item.channel) },
                            onPlaylist = { scope.launch {
                                savedVideoRepository.setFavorite(lang.code, item.videoId, item.path.orEmpty(), item.subs.orEmpty(), item.title, item.channel, key in favoriteKeys)
                                playlistMembershipVideo = key
                            } },
                            onRemove = { scope.launch { recent = history.remove(lang, item) } })
                    }
                }
                item { Text(stringResource(Res.string.videos_channels), style = MaterialTheme.typography.titleMedium) }
                items(channels, key = { it.channelId }) { channel ->
                    Surface(shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().clickable { selectedChannel = channel.channelId }.padding(10.dp)) {
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
                            if (channel.personal) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(stringResource(categoryLabel(channel.category)), Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelSmall)
                                    var editMenu by remember { mutableStateOf(false) }
                                    Box {
                                        TextButton(onClick = { editMenu = true }) { Text(stringResource(Res.string.videos_manage_channel)) }
                                        DropdownMenu(editMenu, onDismissRequest = { editMenu = false }) {
                                            VideoCatalog.categories.forEach { category ->
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(categoryLabel(category))) },
                                                    onClick = {
                                                        vm.action {
                                                            vm.container.database.videoChannels().upsert(
                                                                savedChannels.first { it.channelId == channel.channelId }
                                                                    .copy(category = category)
                                                            )
                                                        }
                                                        editMenu = false
                                                    }
                                                )
                                            }
                                            DropdownMenuItem(text = { Text(stringResource(Res.string.videos_remove_channel)) }, onClick = {
                                                vm.action { vm.container.database.videoChannels().delete(channel.channelId) }; editMenu = false
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (channels.isEmpty()) item { Text(stringResource(Res.string.videos_no_results)) }
                }
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
                    val key = com.ziaee.zabankhan.data.savedVideoStableKey(lang.code, video.videoId, "")
                    VideoRow(video.title, video.published.take(10), vm.container.videoThumbnails, video.videoId, isFavorite = key in favoriteKeys,
                        favoriteLabel = stringResource(if (key in favoriteKeys) Res.string.videos_favorite_remove else Res.string.videos_favorite_add, video.title),
                        onToggleFavorite = { toggleFavorite(video.videoId, "", "", video.title, selected.title) },
                        onPlaylist = { scope.launch { savedVideoRepository.setFavorite(lang.code, video.videoId, "", "", video.title, selected.title, key in favoriteKeys); playlistMembershipVideo = key } }, onClick = { open(video.videoId, "", "") })
                }
                if (!loading && !error && videos.isEmpty()) item { Text(stringResource(Res.string.videos_no_results)) }
            }
        }
    }
}

@Composable
private fun RecentVideoRow(video: RecentVideo, thumbnails: VideoThumbnailLoader, favorite: Boolean, favoriteLabel: String, favoriteEnabled: Boolean, onToggleFavorite: () -> Unit, onWatch: () -> Unit, onRemove: () -> Unit, onPlaylist: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        VideoRow(video.title.ifBlank { video.videoId },
            stringResource(Res.string.videos_progress, videoResumeTime(video.positionMs)) +
                video.channel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
            thumbnails = thumbnails, videoId = video.videoId, modifier = Modifier.weight(1f), onClick = onWatch)
        IconButton(enabled = favoriteEnabled, onClick = onToggleFavorite) {
            Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, favoriteLabel)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(Res.string.videos_options)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.video_playlist_choose)) }, enabled = favoriteEnabled, onClick = { menu = false; onPlaylist() })
                DropdownMenuItem(text = { Text(stringResource(Res.string.videos_remove)) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun VideoRow(title: String, support: String, thumbnails: VideoThumbnailLoader, videoId: String, modifier: Modifier = Modifier, isFavorite: Boolean? = null,
    favoriteLabel: String = "", onToggleFavorite: (() -> Unit)? = null, onPlaylist: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VideoThumbnail(Modifier.width(88.dp), thumbnails = thumbnails, videoId = videoId)
        Column(Modifier.weight(1f)) {
            Text(title, Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.merge(contentStyle(title)))
            Text(support, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
        if (isFavorite != null && onToggleFavorite != null) IconButton(onClick = onToggleFavorite) {
            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, favoriteLabel)
        }
        if (onPlaylist != null) IconButton(onClick = onPlaylist) { Icon(Icons.Default.PlaylistAdd, stringResource(Res.string.video_playlist_choose)) }
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

private fun categoryLabel(category: String?): org.jetbrains.compose.resources.StringResource = when (category) {
    "education" -> Res.string.videos_category_education
    "films" -> Res.string.videos_category_films
    "news" -> Res.string.videos_category_news
    "documentaries" -> Res.string.videos_category_documentaries
    "other" -> Res.string.videos_category_other
    else -> Res.string.videos_category_all
}

@Composable
private fun AddVideoChannelDialog(http: io.ktor.client.HttpClient, onDismiss: () -> Unit,
    onSave: suspend (VideoChannel, String) -> Unit, duplicate: Boolean) {
    var url by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("education") }
    var loading by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(Res.string.videos_add_channel)) }, text = {
        Column {
            OutlinedTextField(url, { url = it; invalid = false }, label = { Text(stringResource(Res.string.videos_channel_url)) }, singleLine = true, isError = invalid)
            if (invalid || duplicate) Text(stringResource(if (duplicate) Res.string.videos_channel_duplicate else Res.string.videos_channel_invalid), color = MaterialTheme.colorScheme.error)
            var expanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { expanded = true }) { Text(stringResource(categoryLabel(category))) }
                DropdownMenu(expanded, { expanded = false }) { VideoCatalog.categories.forEach { value -> DropdownMenuItem(
                    text = { Text(stringResource(categoryLabel(value))) }, onClick = { category = value; expanded = false }) } }
            }
        }
    }, confirmButton = {
        TextButton(enabled = !loading, onClick = {
            if (!validateYoutubeChannelUrl(url)) { invalid = true; return@TextButton }
            loading = true
            // Resolution uses network I/O; dialog button stays disabled while it completes.
            scope.launch {
                try { onSave(resolveYoutubeChannel(http, url), category) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { invalid = true }
                finally { loading = false }
            }
        }) { Text(stringResource(if (loading) Res.string.loading else Res.string.videos_add_channel)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } })
}
