package com.ziaee.zabankhan.watch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.resources.Res
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.contentStyle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun VideoPlaylistManagerDialog(repository: VideoPlaylistRepository, lang: String, dismiss: () -> Unit) {
    val playlists by remember(repository, lang) { repository.observe(lang) }.collectAsState(emptyList())
    var editing by remember { mutableStateOf<VideoPlaylistEntity?>(null) }
    var draft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(Res.string.video_playlists)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(playlists, key = { it.syncId }) { list ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            list.name,
                            style = MaterialTheme.typography.titleSmall.merge(contentStyle(list.name)),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editing = list; draft = list.name }) {
                                Text(stringResource(Res.string.rename))
                            }
                            TextButton(onClick = { scope.launch { repository.delete(list.syncId) } }) {
                                Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(if (editing == null) Res.string.video_playlist_new else Res.string.video_playlist_rename)) }, singleLine = true)
        } }, confirmButton = { TextButton(enabled = draft.isNotBlank(), onClick = { scope.launch { val old = editing; if (old == null) repository.create(lang, draft) else repository.rename(old.syncId, draft); draft = ""; editing = null } }) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(Res.string.done)) } })
}

@Composable
internal fun VideoPlaylistMembershipDialog(repository: VideoPlaylistRepository, lang: String, videoKey: String, dismiss: () -> Unit) {
    val lists by remember(repository, lang) { repository.observe(lang) }.collectAsState(emptyList())
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(Res.string.video_playlist_choose)) },
        text = { Column {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
            items(lists, key = { it.syncId }) { list ->
                val memberships by remember(list.syncId) { repository.memberships(list.syncId) }.collectAsState(emptyList())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        list.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.merge(contentStyle(list.name)),
                    )
                    Checkbox(memberships.any { it.videoKey == videoKey }, { scope.launch { repository.toggle(list.syncId, videoKey) } })
                }
            }
            }
            if (create) OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.video_playlist_new)) }, singleLine = true)
        } }, confirmButton = { Row { TextButton(onClick = { create = true }) { Text(stringResource(Res.string.video_playlist_new)) }; if (create) TextButton(enabled = name.isNotBlank(), onClick = { scope.launch { val list = repository.create(lang, name); repository.toggle(list.syncId, videoKey); name = ""; create = false } }) { Text(stringResource(Res.string.add)) } } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(Res.string.done)) } })
}
