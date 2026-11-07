package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.content.*
import com.ziaee.zabankhan.lang.Languages
import kotlin.test.*

class BatchBWordsTest {
    @Test fun englishLearningScreenExportsEnglishExamples() {
        val file = parseCsvWords(wordsCsvSample(Languages.ENGLISH.code))
        assertEquals("hello", file.words.first().word)
        assertEquals("Hello, how are you?", file.words.first().sentence)
        assertEquals("سلام", file.words.first().meaning)
        assertEquals(0, file.malformedRows)
    }

    @Test fun csvTargetOverridesColumnAndDeduplicatesInsideChosenDeck() {
        val file = parseCsvWords("word,meaning,sentence,deck\nhello,سلام,Hello!,One\nhello,سلام,Hello!,Two\nbye,خداحافظ,Bye!,Two")
        val plan = planCsvImport(file, CsvImportTarget.Deck("English"), setOf("english" to "bye"))
        assertEquals(listOf("English"), plan.deckNames)
        assertEquals(listOf("hello"), plan.words.map { it.word.word })
        assertEquals(2, plan.skipped)
    }

    @Test fun returningLearnedCardUsesLearnedBucketRegardlessOfDueDate() {
        assertEquals(WordsDueBucket.Learned, wordsDueBucket(0, 1000, learned = true))
        assertEquals(WordsDueBucket.Today, wordsDueBucket(1000, 1000))
        assertEquals(WordsDueBucket.InDays(1), wordsDueBucket(1001, 1000))
        assertEquals(WordsDueBucket.InDays(2), wordsDueBucket(86_401_001, 1000))
    }
}
