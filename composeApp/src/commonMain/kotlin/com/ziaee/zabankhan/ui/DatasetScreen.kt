package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.content.DatasetDeckPack
import com.ziaee.zabankhan.content.DatasetInstallResult
import com.ziaee.zabankhan.content.DatasetManifest
import com.ziaee.zabankhan.content.DatasetPackStatus
import com.ziaee.zabankhan.content.DatasetRepository
import com.ziaee.zabankhan.content.DatasetStatus
import com.ziaee.zabankhan.content.DatasetStoryPack
import com.ziaee.zabankhan.content.DatasetSource
import com.ziaee.zabankhan.data.AppDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class DatasetViewModel(
    private val repository: DatasetRepository,
    private val database: AppDatabase,
    private val cache: com.ziaee.zabankhan.translate.KeyValueCache
) : ViewModel() {

    var manifest by mutableStateOf<DatasetManifest?>(null)
        private set
    var status by mutableStateOf<DatasetStatus?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var result by mutableStateOf<DatasetInstallResult?>(null)
        private set
    var removedCards by mutableStateOf<Int?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var progress by mutableStateOf<Pair<Long, Long?>?>(null)
        private set
    var sizes by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    init {
        refresh()
    }

    private fun refresh() = viewModelScope.launch {
        try {
            manifest = repository.loadManifest()
            // Retry first-use starter seeding when this screen is opened after an offline startup.
            try { com.ziaee.zabankhan.content.DatasetSeeder.seedIfNeeded(repository, database, cache) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Keep cached/offline catalogs usable; pending seeding retries later. */ }
            status = repository.status(manifest)
            val current = requireNotNull(manifest)
            sizes = coroutineScope {
                val deckSizes = current.decks.map { pack -> async { pack.key to (repository.downloadSize(pack) ?: 0L) } }.map { it.await() }
                val storySizes = current.stories.map { pack -> async { pack.key to (repository.downloadSize(pack) ?: 0L) } }.map { it.await() }
                (deckSizes + storySizes).filter { it.second > 0L }.toMap()
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true; manifest = DatasetManifest(0, emptyList(), emptyList()) }
    }

    fun addDeck(pack: DatasetDeckPack) = run(pack.key) {
        repository.installDeck(pack) { done, total -> progress = done to total }
    }

    fun addStories(pack: DatasetStoryPack) = run(pack.key) {
        repository.installStories(pack) { done, total -> progress = done to total }
    }

    fun addAll() = run(ALL_PACKS_ID) {
        repository.installAll(requireNotNull(manifest))
    }

    fun removeOrphanedCards() {
        if (busy != null) return
        busy = CLEANUP_ID
        viewModelScope.launch {
            try {
                removedCards = repository.removeOrphanedDatasetCards()
                status = repository.status(requireNotNull(manifest))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failed = true
            } finally {
                progress = null
                busy = null
            }
        }
    }

    private fun run(id: String, block: suspend () -> DatasetInstallResult) {
        if (busy != null) return
        busy = id
        viewModelScope.launch {
            try {
                result = block()
                status = repository.status(requireNotNull(manifest))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failed = true
            } finally {
                progress = null
                busy = null
            }
        }
    }

    fun consumeResult() {
        result = null
        removedCards = null
        failed = false
    }

    private companion object {
        const val ALL_PACKS_ID = "all"
        const val CLEANUP_ID = "cleanup"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val language = requireNotNull(Languages.byCode(requireNotNull(settings.learningLang)))
    val viewModel = remember(vm.container, language.code, settings.meaningLang) { DatasetViewModel(DatasetRepository(vm.container.database, language, settings.meaningLang, vm.container.http), vm.container.database, vm.container.cache) }
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(Res.string.error_generic)
    var confirmCleanup by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.failed) {
        if (viewModel.failed) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.consumeResult()
        }
    }
    LaunchedEffect(viewModel.result) {
        viewModel.result?.let { result ->
            snackbarHostState.showSnackbar(getString(Res.string.dataset_added_result, result.added, result.skipped))
            viewModel.consumeResult()
        }
    }
    LaunchedEffect(viewModel.removedCards) {
        viewModel.removedCards?.let { count ->
            snackbarHostState.showSnackbar(getString(Res.string.dataset_removed_result, count))
            viewModel.consumeResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.dataset_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(Res.string.accessibility_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::addAll,
                        enabled = viewModel.busy == null
                    ) {
                        Text(stringResource(Res.string.dataset_add_all))
                    }
                }
            )
        }
    ) { padding ->
        val manifest = viewModel.manifest
        if (manifest == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        stringResource(Res.string.dataset_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    TextButton(
                        onClick = { confirmCleanup = true },
                        enabled = viewModel.busy == null
                    ) {
                        Text(stringResource(Res.string.dataset_remove_deleted_deck_cards))
                    }
                }
                item {
                    Text(
                        stringResource(Res.string.dataset_decks),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (manifest.decks.isEmpty() && manifest.stories.isEmpty()) item { Text(stringResource(Res.string.dataset_empty_language), style = MaterialTheme.typography.bodyMedium) }
                items(manifest.decks, key = { it.key }) { pack ->
                    DatasetItem(
                        name = pack.name,
                        level = pack.level,
                        count = stringResource(Res.string.dataset_cards_count, pack.count),
                        status = viewModel.status?.decks?.get(pack.key),
                        busy = viewModel.busy == pack.key,
                        enabled = viewModel.busy == null,
                        remote = pack.source is DatasetSource.Remote,
                        downloadSize = viewModel.sizes[pack.key] ?: pack.sizeBytes,
                        progress = if (viewModel.busy == pack.key) viewModel.progress else null,
                        onAdd = { viewModel.addDeck(pack) }
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.dataset_stories),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                items(manifest.stories, key = { it.key }) { pack ->
                    DatasetItem(
                        name = pack.name,
                        level = pack.level,
                        count = stringResource(Res.string.dataset_stories_count, pack.count),
                        status = viewModel.status?.stories?.get(pack.key),
                        busy = viewModel.busy == pack.key,
                        enabled = viewModel.busy == null,
                        remote = pack.sourceRef is DatasetSource.Remote,
                        downloadSize = viewModel.sizes[pack.key] ?: pack.sizeBytes,
                        progress = if (viewModel.busy == pack.key) viewModel.progress else null,
                        onAdd = { viewModel.addStories(pack) }
                    )
                }
            }
        }
    }

    if (confirmCleanup) {
        AlertDialog(
            onDismissRequest = { confirmCleanup = false },
            title = { Text(stringResource(Res.string.dataset_remove_deleted_deck_cards)) },
            text = { Text(stringResource(Res.string.dataset_remove_deleted_deck_cards_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCleanup = false
                    viewModel.removeOrphanedCards()
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCleanup = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun DatasetItem(
    name: String,
    level: String,
    count: String,
    status: DatasetPackStatus?,
    busy: Boolean,
    enabled: Boolean,
    remote: Boolean,
    downloadSize: Long?,
    progress: Pair<Long, Long?>?,
    onAdd: () -> Unit
) {
    Card {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text("$level · $count", style = MaterialTheme.typography.bodySmall)
                val size = downloadSize?.let(::formatDatasetBytes) ?: stringResource(Res.string.dataset_size_unknown)
                Text(stringResource(Res.string.dataset_pack_size, size), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(Res.string.dataset_installed_count, status?.installed ?: 0, status?.total ?: 0), style = MaterialTheme.typography.bodySmall)
            }
            if (busy) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    progress?.let { (done, total) ->
                        val pct = total?.takeIf { it > 0 }?.let { "${(done * 100 / it).coerceIn(0, 100)}%" }
                        if (pct != null) Text(pct, style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Button(onClick = onAdd, enabled = enabled && status?.complete != true) {
                    val label = when {
                        status?.complete == true -> Res.string.dataset_added
                        (status?.installed ?: 0) > 0 -> Res.string.dataset_add_missing
                        remote -> Res.string.dataset_download_install
                        else -> Res.string.dataset_add
                    }
                    Text(stringResource(label))
                }
            }
        }
    }
}

private fun formatDatasetBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
