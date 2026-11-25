package com.ziaee.zabankhan.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.EditorialCard
import com.ziaee.zabankhan.ui.EditorialSearchEntry
import com.ziaee.zabankhan.ui.EditorialTopAppBar
import com.ziaee.zabankhan.ui.contentStyle
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

@Composable
fun LearnScreen(
    back: () -> Unit,
    openResources: () -> Unit,
    openGrammar: (() -> Unit)?,
    openExams: (() -> Unit)?,
    openReview: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        EditorialTopAppBar(stringResource(Res.string.learn_title), back = back)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Button(openResources, Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.learn_resources))
                }
            }
            openGrammar?.let {
                item {
                    OutlinedButton(it, Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.learn_grammar))
                    }
                }
            }
            openExams?.let {
                item {
                    OutlinedButton(it, Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.learn_exam_practice))
                    }
                }
            }
            item {
                OutlinedButton(openReview, Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.learn_vocab_review))
                }
            }
        }
    }
}

@Composable
fun LearningResourceScreen(language: String, back: () -> Unit, importPdf: () -> Unit) {
    val result by produceState<Result<LocalLearningResources>?>(null) {
        value = try {
            Result.success(LocalLearningResources.fromJson(Res.readBytes("files/learning/resources.json").decodeToString()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    var query by rememberSaveable(language) { mutableStateOf("") }
    var selectedLevel by rememberSaveable(language) { mutableStateOf<String?>(null) }
    val repository = result?.getOrNull()
    val available = repository?.browse(language).orEmpty()
    val levels = available.flatMap { it.levels }.distinct().sorted()
    val shown = repository?.browse(language, query, selectedLevel).orEmpty()
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxSize()) {
        EditorialTopAppBar(stringResource(Res.string.learn_resources), back = back)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedButton(importPdf, Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.pdf_import))
                }
            }
            when {
                result == null -> item { CircularProgressIndicator() }
                result?.isFailure == true -> item { Text(stringResource(Res.string.learn_catalog_error)) }
                available.isEmpty() -> item { Text(stringResource(Res.string.learn_catalog_empty)) }
                else -> {
                    item {
                        EditorialSearchEntry(query, { query = it }, Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            levels.forEach { level ->
                                FilterChip(
                                    selectedLevel == level,
                                    { selectedLevel = level.takeUnless { selectedLevel == it } },
                                    { Text(level) },
                                )
                            }
                        }
                    }
                    if (shown.isEmpty()) item { Text(stringResource(Res.string.learn_no_matches)) }
                    items(shown, key = { it.id }) { resource ->
                        EditorialCard {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(resource.series, style = MaterialTheme.typography.titleMedium.merge(contentStyle(resource.series)))
                                Text(resource.levels.joinToString(" · "))
                                val description = resource.descriptions.getValue("fa")
                                Text(description, style = MaterialTheme.typography.bodyMedium.merge(contentStyle(description)))
                                resource.officialUrl?.let { url ->
                                    OutlinedButton({ uriHandler.openUri(url) }) {
                                        Text(stringResource(Res.string.learn_official_source))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
