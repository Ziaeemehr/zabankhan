package com.ziaee.zabankhan.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.translate.*
import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.stringResource

fun translationSource(engine: String): ExampleSource = when (engine) {
    "override", "identity", "" -> ExampleSource.EDITED
    "bundled" -> ExampleSource.BUNDLED
    "own-dictionary" -> ExampleSource.DICTIONARY
    "google-gtx", "google-clients5", "mymemory" -> ExampleSource.GOOGLE
    else -> ExampleSource.MODEL
}

@Composable
fun TranslationSourceIcon(source: ExampleSource, engine: String? = null) {
    val icon = when (source) {
        ExampleSource.MODEL -> Icons.Default.AutoAwesome
        ExampleSource.GOOGLE -> Icons.Default.Translate
        ExampleSource.BUNDLED, ExampleSource.DICTIONARY -> Icons.AutoMirrored.Filled.MenuBook
        ExampleSource.EDITED -> return
    }
    val description = when (source) {
        ExampleSource.MODEL -> engine?.let { stringResource(Res.string.translation_source_model_named, it) }
            ?: stringResource(Res.string.translation_source_model)
        ExampleSource.GOOGLE -> if (engine == "mymemory") "MyMemory" else stringResource(Res.string.translation_source_google)
        ExampleSource.BUNDLED -> stringResource(Res.string.translation_source_bundled)
        ExampleSource.DICTIONARY -> stringResource(Res.string.translation_source_dictionary)
        ExampleSource.EDITED -> return
    }
    Icon(icon, description, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun TranslationSourceIcon(translation: ExampleTranslation?) {
    translation?.let { TranslationSourceIcon(it.source, it.engine) }
}
