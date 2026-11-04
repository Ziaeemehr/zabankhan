package com.ziaee.zabankhan.app

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ziaee.zabankhan.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(val container: AppContainer) : ViewModel() {
    val settings = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var failed by mutableStateOf(false); private set
    fun clearError() { failed = false }
    fun action(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
    } }
}
