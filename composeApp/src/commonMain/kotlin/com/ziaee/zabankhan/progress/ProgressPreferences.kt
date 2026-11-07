package com.ziaee.zabankhan.progress

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Only user settings and celebration memory are persisted; points always come from source data. */
class ProgressPreferences(private val store: DataStore<Preferences>) {
    val goal = store.data.map { readGoal(it) }.distinctUntilChanged()

    suspend fun setGoal(points: Int) = store.edit {
        it[weeklyGoalKey] = points.coerceIn(MIN_WEEKLY_GOAL, MAX_WEEKLY_GOAL)
    }

    /** Claim in one DataStore transaction before displaying, so concurrent collectors cannot repeat it. */
    suspend fun claim(summary: ProgressSummary): ProgressCelebration {
        var claimed: ProgressCelebration? = null
        store.edit { prefs ->
            val seen = runCatching { Json.decodeFromString<ProgressSeen>(prefs[seenKey] ?: "{}") }.getOrDefault(ProgressSeen())
            val change = progressCelebration(summary, seen)
            if (change.seen != seen) prefs[seenKey] = Json.encodeToString(change.seen)
            claimed = change
        }
        return requireNotNull(claimed)
    }

    companion object {
        private val weeklyGoalKey = intPreferencesKey("weekly_points_goal")
        private val seenKey = stringPreferencesKey("progress_seen")
        fun readGoal(prefs: Preferences): Int = (prefs[weeklyGoalKey] ?: DEFAULT_WEEKLY_GOAL).coerceIn(MIN_WEEKLY_GOAL, MAX_WEEKLY_GOAL)
    }
}
