package com.ziaee.zabankhan.progress

import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.pdf.PdfReadingProgress
import com.ziaee.zabankhan.srs.*
import com.ziaee.zabankhan.statistics.localToday
import com.ziaee.zabankhan.ui.LibraryCompletionCache
import com.ziaee.zabankhan.ui.isTextFinished
import com.ziaee.zabankhan.ui.srsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone

/** All-language queries deliberately include languages removed from the settings picker. */
class ProgressRepository(private val container: AppContainer) {
    private data class Counts(val learned: Map<String, Long>, val finished: Map<String, Long>)
    private data class Logs(val activity: List<ActivityLogEntry>, val reviews: List<ReviewLogEntry>, val shadow: List<ShadowAttempt>, val exams: List<ExamAttempt>)

    fun observe(): Flow<ProgressSummary> {
        val db = container.database
        val learned = container.vocab.observeProgressWords().map { words ->
            words.filter { it.srsState().status() == VocabStatus.LEARNED }.groupingBy { it.lang }.eachCount().mapValues { it.value.toLong() }
        }.flowOn(Dispatchers.Default)
        val completionCache = LibraryCompletionCache()
        val finished = container.texts.observeProgressTexts().map { texts ->
            val pdf = texts.filter { it.pdfPath != null }.associate { doc ->
                doc.id to PdfReadingProgress(doc.lastOffset, container.cache.get("pdf:page-count:${doc.id}")?.toIntOrNull() ?: 0)
            }
            val complete = completionCache.completionFor(texts, { it.body }, { pdf[it] })
            texts.filter { isTextFinished(it, complete[it.id] == true) }.groupingBy { it.lang }.eachCount().mapValues { it.value.toLong() }
        }.flowOn(Dispatchers.Default)
        val counts = combine(learned, finished) { words, texts -> Counts(words, texts) }
        val logs = combine(db.activity().observeProgressActivity(), container.vocab.observeProgressReviews(),
            db.shadowAttempts().observeProgressShadow(), db.examAttempts().observeProgressExams()) { activity, reviews, shadow, exams ->
            Logs(activity, reviews, shadow, exams)
        }
        // Recompute after local midnight/timezone changes even if Room has no new writes.
        val calendar = flow {
            while (true) {
                val zone = TimeZone.currentSystemDefault()
                emit(localToday(zone) to zone)
                delay(30_000)
            }
        }.distinctUntilChanged()
        return combine(counts, logs, container.settings.settings, calendar) { countsValue, log, settings, day ->
            computeProgress(ProgressInputs(settings.learningLang.orEmpty(), day.first, day.second,
                log.activity, log.reviews, log.shadow, countsValue.learned, countsValue.finished,
                log.exams.map { ProgressExam(it.lang, it.createdAtMs) }, settings.weeklyPointsGoal))
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }
}
