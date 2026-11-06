package com.ziaee.zabankhan.grammar

import androidx.compose.runtime.Composable

/** Platform document picker/share sheet; kept outside the copied common UI. */
@Composable
expect fun rememberGrammarExport(): (text: String, fileName: String?, save: Boolean) -> Unit

@Composable
expect fun rememberGrammarPdfExport(): (blocks: List<GrammarPdfBlock>, fileName: String, appName: String) -> Unit

/** Keep a document name inside the platform's export directory. */
internal fun grammarExportFileName(value: String?, fallback: String): String = value.orEmpty()
    .map { if (it == '/' || it == '\\' || it.code < 32) '_' else it }
    .joinToString("").trim().take(160).takeUnless { it.isBlank() || it == "." || it == ".." } ?: fallback
