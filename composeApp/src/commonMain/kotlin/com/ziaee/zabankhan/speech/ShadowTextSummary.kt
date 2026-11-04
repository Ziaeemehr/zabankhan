package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.data.ShadowAttempt
import kotlin.math.roundToInt

data class WeakSentence(val sentence: String, val accuracyPercent: Int)
data class ShadowTextSummary(val sentences: Int, val accuracyPercent: Int, val averagePaceRatio: Float?, val goodPacePercent: Int?, val weakest: List<WeakSentence>)
/** Only the latest attempt per language/text/sentence counts, as in FrenchReader. */
fun shadowTextSummary(attempts: List<ShadowAttempt>): ShadowTextSummary? {
    if (attempts.isEmpty()) return null
    val latest = attempts.groupBy { Triple(it.lang, it.textId, it.sentence) }
        .values.map { rows -> rows.maxWith(compareBy({ it.timestampMs }, { it.id })) }
    val total = latest.sumOf { it.total }
    val paces = latest.mapNotNull { it.paceRatio }
    return ShadowTextSummary(latest.size, if (total == 0) 0 else (latest.sumOf { it.matched } * 100f / total).roundToInt(),
        paces.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
        paces.takeIf { it.isNotEmpty() }?.let { (it.count { PaceAnalyzer.rate(it) == PaceRating.GOOD } * 100f / it.size).roundToInt() },
        latest.filter { it.total > 0 && it.matched < it.total }.map { WeakSentence(it.sentence, (it.matched * 100f / it.total).roundToInt()) }
            .sortedWith(compareBy({ it.accuracyPercent }, { it.sentence })).take(3))
}
