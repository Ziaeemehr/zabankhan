package com.ziaee.zabankhan.ui

import kotlinx.coroutines.IO
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.data.TextDocument
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.theme.LocalGrammarColors
import com.ziaee.zabankhan.ui.theme.ZabankhanDesign
import com.ziaee.zabankhan.watch.RecentVideo
import com.ziaee.zabankhan.watch.VideoThumbnail
import com.ziaee.zabankhan.watch.VideoThumbnailLoader
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// Ported from French Reader's ui/home/HomeComponents.kt.

data class HomeProgress(val streakDays: Int, val weekNewWords: Int, val learnedWords: Int, val todayMinutes: Int, val goalMinutes: Int)

@Composable
fun ProgressCard(state: HomeProgress, onOpen: () -> Unit, onSavedWords: () -> Unit, modifier: Modifier = Modifier,
    motivation: com.ziaee.zabankhan.progress.ProgressSummary? = null, onAchievements: () -> Unit = {}) {
    val progress = (state.todayMinutes.toFloat() / state.goalMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
    val goalReached = progress >= 1f
    val colors = MaterialTheme.colorScheme
    EditorialCard(modifier, onClick = onOpen, containerColor = colors.primaryContainer) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 6.dp)) {
            motivation?.let { summary ->
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssistChip(onClick = onAchievements, label = { Text(stringResource(Res.string.progress_level, summary.level.toString())) },
                        leadingIcon = { Icon(Icons.Default.EmojiEvents, null, Modifier.size(16.dp)) })
                    LinearProgressIndicator(progress = { summary.levelFraction }, modifier = Modifier.weight(1f).height(3.dp),
                        drawStopIndicator = {})
                }
            }
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProgressStat(Icons.Default.LocalFireDepartment, Color(0xFFF2994A), state.streakDays.toString(),
                    pluralStringResource(Res.plurals.home_streak_days, state.streakDays), Modifier.weight(1f))
                // New words saved this week: a number the reader can act on; time spent is the goal bar below.
                ProgressStat(Icons.Default.BookmarkAdded, colors.primary, state.weekNewWords.toString(),
                    stringResource(Res.string.home_new_words_week), Modifier.weight(1f))
                // Learned words only: saving is one tap, learning is the progress that matters.
                ProgressStat(Icons.Default.School, colors.tertiary, state.learnedWords.toString(),
                    stringResource(Res.string.review_learned_words),
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onSavedWords))
            }
            Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = if (goalReached) colors.tertiary else colors.primary,
                    trackColor = colors.onSurface.copy(alpha = 0.10f),
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    drawStopIndicator = {},
                )
                if (goalReached) Icon(Icons.Default.CheckCircle, stringResource(Res.string.activity_goal_done),
                    tint = colors.tertiary, modifier = Modifier.padding(horizontal = 8.dp).size(16.dp))
                else Text(stringResource(Res.string.home_goal_caption, state.todayMinutes.toString(), state.goalMinutes.toString()),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                    maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

/** A softly tinted icon badge, a bold value, and a caption that can wrap. */
@Composable
private fun ProgressStat(icon: ImageVector, accent: Color, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DailyReviewCard(dueCount: Int, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val due = dueCount > 0
    val accent = if (due) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
    EditorialCard(modifier, onClick = onStart, enabled = due) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Flashcards, not a book: the book icon belongs to "continue reading".
            Box(Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(if (due) Icons.Default.Style else Icons.Default.CheckCircle, contentDescription = null,
                    tint = accent, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.home_section_daily_review), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (due) stringResource(Res.string.home_review_due, dueCount) else stringResource(Res.string.home_review_complete),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (due) {
                Spacer(Modifier.width(ZabankhanDesign.spacing.xSmall))
                FilledTonalIconButton(onClick = onStart, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(Res.string.home_review_start), Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeEntryRow(icon: ImageVector, accent: Color, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun HomeEntryDivider() {
    HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** Entry point to the built-in grammar reference; [language] is the learning language's name. */
@Composable
internal fun GrammarRow(language: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    HomeEntryRow(Icons.Default.Spellcheck, LocalGrammarColors.current.accent, stringResource(Res.string.grammar_title),
        stringResource(Res.string.grammar_entry_description, language), onOpen, modifier)
}

@Composable
internal fun ExamsRow(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    HomeEntryRow(Icons.Default.School, MaterialTheme.colorScheme.primary, stringResource(Res.string.exams_title),
        stringResource(Res.string.exams_entry_description), onOpen, modifier)
}

@Composable
internal fun DatasetEntryRow(language: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val packCount by produceState(0, language) {
        val spec = com.ziaee.zabankhan.lang.Languages.byCode(language)
        value = if (spec == null) 0 else com.ziaee.zabankhan.content.bundledDatasetPackCount(spec)
    }
    HomeEntryRow(Icons.Default.CollectionsBookmark, MaterialTheme.colorScheme.primary,
        stringResource(Res.string.dataset_home_title), stringResource(Res.string.dataset_home_count, packCount), onOpen, modifier)
}

@Composable
internal fun TodayNewsRow(headlineCount: Int, language: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    HomeEntryRow(Icons.Default.Newspaper, MaterialTheme.colorScheme.secondary, stringResource(Res.string.home_section_today_news),
        if (headlineCount > 0) pluralStringResource(Res.plurals.home_news_headlines, headlineCount, headlineCount.toString())
        else stringResource(Res.string.home_news_description, language), onOpen, modifier)
}

@Composable
internal fun WatchVideosRow(language: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    HomeEntryRow(Icons.Default.PlayCircle, MaterialTheme.colorScheme.secondary, stringResource(Res.string.home_watch_videos),
        stringResource(Res.string.videos_home_support), onOpen, modifier)
}

@Composable
internal fun ShadowingRow(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    HomeEntryRow(Icons.Default.Mic, MaterialTheme.colorScheme.tertiary, stringResource(Res.string.shadow_entry_title),
        stringResource(Res.string.shadow_home_support), onOpen, modifier)
}

/** Copied from French Reader's ui/home/HomeComponents.kt; local images decode on both platforms. */
@Composable
internal fun DocumentThumbnail(doc: TextDocument, modifier: Modifier = Modifier) {
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, doc.imagePath) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            doc.imagePath?.let { saved ->
                runCatching {
                    val path = if (saved.startsWith("/")) saved else "${com.ziaee.zabankhan.pdf.AppFiles.filesDir}/$saved"
                    loadImportImage(path)
                }.getOrNull()
            }
        }
    }
    val shape = RoundedCornerShape(8.dp)
    Box(modifier = modifier.clip(shape), contentAlignment = Alignment.Center) {
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = requireNotNull(image),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    if (doc.pdfPath != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ),
                contentAlignment = Alignment.Center
            ) {
                if (doc.pdfPath != null) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(26.dp))
                } else {
                    Box(
                        modifier = Modifier.size(width = 32.dp, height = 38.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(4.dp)
                            .background(MaterialTheme.colorScheme.primary))
                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
                    }
                }
            }
        }
        if (doc.pdfPath != null) {
            Text(
                text = "PDF",
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.error),
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
fun TextLevelBadge(level: String?, modifier: Modifier = Modifier) {
    MetadataBadge(
        text = level ?: stringResource(Res.string.text_level_unspecified), modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant.let { if (level == null) it.copy(alpha = 0.65f) else it },
    )
}

@Composable
fun ContinueReadingCard(doc: TextDocument, onClick: () -> Unit, modifier: Modifier = Modifier, pdfPageCount: Int = 0) {
    val metrics = remember(doc.id, doc.body, doc.pdfPath, doc.lastOffset, pdfPageCount) {
        homeReadingMetrics(doc, pdfPageCount = pdfPageCount)
    }
    EditorialCard(modifier, onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DocumentThumbnail(doc, Modifier.size(ZabankhanDesign.sizes.thumbnailMedium))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                ContinueKindLabel(Icons.Default.MenuBook, stringResource(Res.string.home_continue_reading))
                // Direction follows the title, not the interface.
                Text(doc.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall.merge(contentStyle(doc.title)),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    doc.sourceName?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(ZabankhanDesign.spacing.half))
                    }
                    doc.level?.let { TextLevelBadge(it) }
                }
                Spacer(Modifier.height(ZabankhanDesign.spacing.half))
                EditorialProgressIndicator(metrics.progressFraction, stringResource(Res.string.home_progress_percent, metrics.progressPercent))
                if (metrics.estimatedRemainingMinutes > 0 || metrics.progressPercent == 100) {
                    Text(
                        if (metrics.progressPercent == 100) stringResource(Res.string.home_reading_complete)
                        else stringResource(Res.string.home_remaining_minutes, metrics.estimatedRemainingMinutes),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(ZabankhanDesign.spacing.xSmall))
            ContinueButton(Icons.Default.MenuBook, stringResource(Res.string.home_continue_reading), onClick)
        }
    }
}

internal fun videoResumeTime(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
internal fun ContinueWatchingCard(video: RecentVideo, thumbnails: VideoThumbnailLoader, onClick: () -> Unit, modifier: Modifier = Modifier) {
    EditorialCard(modifier, onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            VideoThumbnail(Modifier.width(ZabankhanDesign.sizes.thumbnailMedium), thumbnails, video.videoId)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                ContinueKindLabel(Icons.Default.PlayArrow, stringResource(Res.string.home_continue_watching))
                val title = video.title.ifBlank { video.videoId }
                Text(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall.merge(contentStyle(title)),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(Res.string.videos_progress, videoResumeTime(video.positionMs)) +
                    video.channel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(ZabankhanDesign.spacing.xSmall))
            ContinueButton(Icons.Default.PlayArrow, stringResource(Res.string.home_continue_watching), onClick)
        }
    }
}

/** Says at a glance whether a continue card resumes a text or a video. */
@Composable
private fun ContinueKindLabel(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A round resume button whose icon matches the content kind; narrower than a text pill so titles keep their width. */
@Composable
private fun ContinueButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun NewsStatus(hasPartialError: Boolean, isEmptyError: Boolean, onRetry: () -> Unit) {
    if (hasPartialError || isEmptyError) {
        Text(stringResource(if (isEmptyError) Res.string.home_news_empty_error else Res.string.home_news_partial_error),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}

/** French Reader adds a 56dp article image when the feed has one; without a network image loader the row is text-only. */
@Composable
internal fun NewsHeadlineRow(headline: Headline, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(headline.source.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(headline.item.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium.merge(contentStyle(headline.item.title)),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** shortcut: always French Reader's source placeholder, since no network image loader exists; add one to show feed images. */
@Composable
internal fun NewsCardImage(@Suppress("UNUSED_PARAMETER") headline: Headline, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = stringResource(Res.string.home_news_image_unavailable),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
