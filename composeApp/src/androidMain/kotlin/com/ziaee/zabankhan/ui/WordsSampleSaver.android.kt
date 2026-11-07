package com.ziaee.zabankhan.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.stringResource

@Composable internal actual fun rememberWordsSampleSaver(): (String, String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val saved = stringResource(Res.string.b_words_csv_import_sample_saved)
    val failed = stringResource(Res.string.b_words_csv_import_failed)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val text = pending
        pending = null
        if (uri != null && text != null) scope.launch {
            val success = withContext(Dispatchers.IO) { runCatching {
                requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }.isSuccess }
            Toast.makeText(context, if (success) saved else failed, Toast.LENGTH_SHORT).show()
        }
    }
    return { text, fileName -> pending = text; launcher.launch(fileName) }
}
