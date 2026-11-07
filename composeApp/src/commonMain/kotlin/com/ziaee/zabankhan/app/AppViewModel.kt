package com.ziaee.zabankhan.app

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ziaee.zabankhan.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(val container: AppContainer) : ViewModel() {
    val progress = com.ziaee.zabankhan.progress.ProgressRepository(container).observe()
        .catch { error -> if (error is CancellationException) throw error else failed = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var failed by mutableStateOf(false); private set
    /** Port of ReadingViewModel.saveEdit: body edits restart reading; renames keep position. */
    suspend fun updateText(document: TextDocument, title: String, body: String) {
        val stored = container.texts.getById(document.id) ?: return
        val updated = editedTextDocument(stored, title, body)
        if (updated.body != stored.body) {
            // Audio and translation files are keyed by source text. Drop this document's old
            // offline pins; changed paragraphs receive new cache keys when re-chunked.
            container.ttsCache.unpinDocument("reader:${stored.id}")
        }
        container.texts.update(updated)
    }
    fun clearError() { failed = false }
    fun action(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
    } }
}
