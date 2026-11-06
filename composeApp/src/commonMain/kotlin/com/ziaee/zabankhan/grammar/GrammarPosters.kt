package com.ziaee.zabankhan.grammar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.ziaee.zabankhan.ui.theme.ZabankhanDesign
import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.stringResource
import com.ziaee.zabankhan.ui.contentStyle
import com.ziaee.zabankhan.ui.theme.LocalGrammarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reference poster list, with data supplied by the active language pack. */
@Composable
fun GrammarPostersScreen(posters: List<GrammarPoster>, showPersian: Boolean, onBack: () -> Unit) {
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(ZabankhanDesign.spacing.small),
            verticalArrangement = Arrangement.spacedBy(ZabankhanDesign.spacing.xSmall)
        ) {
            item(key = "back") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                    }
                    Text(stringResource(Res.string.grammar_posters), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (posters.isEmpty()) item(key = "empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = ZabankhanDesign.spacing.xLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ZabankhanDesign.spacing.small)
                ) {
                    Icon(
                        Icons.Default.Collections, contentDescription = null,
                        tint = LocalGrammarColors.current.accent,
                        modifier = Modifier.size(ZabankhanDesign.sizes.touchTarget)
                    )
                    Text(
                        stringResource(Res.string.grammar_posters_empty),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Titles only for now: opening a poster waits for the PDF viewer.
            items(posters, key = { it.id }) { poster ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(ZabankhanDesign.spacing.small)) {
                        GrammarText(GrammarCell(poster.titleLearning), rtl = grammarIsRtlText(poster.titleLearning))
                        val meaningTitle = if (showPersian) poster.titleFa else poster.titleEnglish.takeIf { it != poster.titleLearning }
                        if (!meaningTitle.isNullOrBlank()) GrammarText(GrammarCell(meaningTitle), rtl = grammarIsRtlText(meaningTitle), secondary = true)
                    }
                }
            }
        }
    }
}
