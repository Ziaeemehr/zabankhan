package com.ziaee.zabankhan.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun AchievementsScreen(vm: AppViewModel, settings: AppSettings, back: () -> Unit) {
    val summary by vm.progress.collectAsState()
    // Switching language never briefly presents the previous language's badges.
    val loaded = summary?.takeIf { it.badges.firstOrNull()?.language == settings.learningLang }
    Column(Modifier.fillMaxSize()) {
        EditorialTopAppBar(stringResource(Res.string.progress_achievements), back)
        if (loaded == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EditorialCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.progress_level, loaded.level.toString()), style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(Res.string.progress_total, loaded.totalPoints.toString()))
                            LinearProgressIndicator(progress = { loaded.levelFraction }, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(Res.string.progress_next, loaded.pointsToNextLevel.toString()), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EditorialCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.progress_weekly, loaded.weeklyPoints.toString(), loaded.weeklyGoal.toString()), style = MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(progress = { (loaded.weeklyPoints.toDouble() / loaded.weeklyGoal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(Res.string.progress_weekly_explanation), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val language = Languages.byCode(settings.learningLang.orEmpty())
                    Text(stringResource(Res.string.progress_badges_language, language?.let { languageName(it) }.orEmpty()), style = MaterialTheme.typography.titleMedium)
                }
                items(loaded.badges, key = { it.seenKey }) { badge -> BadgeCard(badge) }
            }
        }
    }
}

@Composable private fun BadgeCard(badge: ProgressBadge) {
    val colors = MaterialTheme.colorScheme
    val title = stringResource(when (badge.id) {
        "first_text" -> Res.string.progress_badge_first_text
        "streak_7" -> Res.string.progress_badge_streak_7
        "streak_30" -> Res.string.progress_badge_streak_30
        "learned_100" -> Res.string.progress_badge_learned_100
        "learned_500" -> Res.string.progress_badge_learned_500
        "learned_1000" -> Res.string.progress_badge_learned_1000
        "reviews_1000" -> Res.string.progress_badge_reviews_1000
        "texts_10" -> Res.string.progress_badge_texts_10
        else -> Res.string.progress_badge_reading_600
    })
    EditorialCard(containerColor = if (badge.unlocked) colors.primaryContainer else colors.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (badge.unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                stringResource(if (badge.unlocked) Res.string.progress_unlocked else Res.string.progress_locked),
                tint = if (badge.unlocked) colors.primary else colors.onSurfaceVariant.copy(alpha = .55f))
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = if (badge.unlocked) colors.onPrimaryContainer else colors.onSurfaceVariant)
            Text(stringResource(Res.string.progress_badge_count, badge.current.toString(), badge.target.toString()),
                style = MaterialTheme.typography.bodySmall, color = if (badge.unlocked) colors.onPrimaryContainer else colors.onSurfaceVariant)
        }
    }
}
