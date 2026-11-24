package com.ziaee.zabankhan.update

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient

@Composable internal actual fun UpdateReminder(http: HttpClient, snackbar: SnackbarHostState, show: Boolean) = Unit
@Composable internal actual fun UpdateSettings(http: HttpClient) = Unit
