package com.ziaee.zabankhan.translate

import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.lang.TextDir

data class MeaningOption(val text: String, val gloss: String?, val partOfSpeech: String?)

data class MeaningCandidates(val best: String?, val options: List<MeaningOption>)

internal fun normalizePersian(text: String): String = text.replace('ي', 'ی').replace('ك', 'ک')
    .replace("\u200C", "")
    .replace(Regex("\\p{M}+"), "")
    .replace(Regex("\\s+"), " ").trim().lowercase()

internal fun filterWordSenses(senses: List<WordSense>): List<WordSense> {
    val topScores = senses.groupBy { it.partOfSpeech }.mapValues { (_, group) -> group.maxOf { it.score } }
    val counts = mutableMapOf<String, Int>()
    return senses.filter { sense ->
        val count = counts[sense.partOfSpeech] ?: 0
        val keep = (sense.score == 0.0 || sense.score >= topScores.getValue(sense.partOfSpeech) * 0.01) && count < 4
        if (keep) counts[sense.partOfSpeech] = count + 1
        keep
    }.take(8)
}

internal fun rankWordSenses(senses: List<WordSense>, translatedSentence: String?, guessedPos: String? = null): List<WordSense> {
    fun words(text: String) = Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase()).map { it.value }.toList()
    val sentenceWords = words(translatedSentence.orEmpty())
    val ignored = setOf("to", "a", "an", "the", "be")
    fun matches(sense: WordSense): Boolean {
        val allWords = words(sense.term)
        val significant = allWords.filterNot { it in ignored }.ifEmpty { allWords }
        return significant.isNotEmpty() && significant.all { word ->
            val prefix = word.take(minOf(word.length, 5))
            sentenceWords.any { it.startsWith(prefix) }
        }
    }
    return if (senses.any(::matches)) senses.sortedByDescending(::matches)
    else senses.sortedByDescending { guessedPos != null && it.partOfSpeech.equals(guessedPos, ignoreCase = true) }
}

internal fun contextTranslationLines(targets: List<String>, before: String?): List<String> =
    listOfNotNull(before) + targets

internal fun selectDictionaryTerm(senses: List<WordSense>, partOfSpeech: String): String? {
    val pos = senses.firstOrNull { it.partOfSpeech.equals(partOfSpeech, ignoreCase = true) }?.partOfSpeech
        ?: senses.firstOrNull()?.partOfSpeech ?: return null
    return senses.filter { it.partOfSpeech == pos }.maxByOrNull { it.score }?.term
}

internal fun missingContextTranslations(
    targets: List<String>, before: String?, translated: List<String>, missing: Set<String>
): List<Pair<String, String>> = targets.zip(translated.drop(if (before != null) 1 else 0))
    .filter { it.first in missing }

/** A small context hint; dictionary sentence matches remain authoritative. */
internal fun guessFrenchPartOfSpeech(sentence: String, word: String): String? {
    fun tokens(text: String) = Regex("[\\p{L}]+['’]?")
        .findAll(text.lowercase()).map { it.value.replace('’', '\'') }.toList()
    val sentenceTokens = tokens(sentence)
    val target = tokens(word).singleOrNull() ?: return null
    val index = sentenceTokens.indexOf(target)
    if (index < 0) return null
    val previous = sentenceTokens.getOrNull(index - 1)
    val determiners = setOf("le", "la", "les", "l'", "un", "une", "des", "du", "au", "aux",
        "ce", "cet", "cette", "ces", "mon", "ma", "mes", "ton", "ta", "tes", "son", "sa", "ses",
        "notre", "nos", "votre", "vos", "leur", "leurs", "quel", "quelle", "quels", "quelles",
        "chaque", "plusieurs", "certains", "certaines")
    val verbContexts = setOf("je", "j'", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles",
        "ne", "n'", "de", "d'", "à", "pour", "sans", "par", "après")
    val conjugatedVerbs = setOf("ai", "as", "a", "avons", "avez", "ont", "avais", "avait", "avions", "aviez", "avaient",
        "suis", "es", "est", "sommes", "êtes", "sont", "étais", "était", "étions", "étiez", "étaient",
        "été", "être", "avoir", "eu", "serai", "seras", "sera", "serons", "serez", "seront",
        "aurai", "auras", "aura", "aurons", "aurez", "auront", "serais", "serait", "serions", "seriez", "seraient",
        "aurais", "aurait", "aurions", "auriez", "auraient", "peux", "peut", "pouvons", "pouvez", "peuvent",
        "pouvais", "pouvait", "pouvions", "pouviez", "pouvaient", "pourrais", "pourrait", "pourrions", "pourriez", "pourraient",
        "dois", "doit", "devons", "devez", "doivent", "devais", "devait", "devions", "deviez", "devaient",
        "veux", "veut", "voulons", "voulez", "veulent", "voulais", "voulait", "voulions", "vouliez", "voulaient",
        "vais", "vas", "va", "allons", "allez", "vont", "faut",
        "devrais", "devrait", "devrions", "devriez", "devraient")
    val infinitive = listOf("er", "ir", "re", "oir").any { target.endsWith(it) }
    if (infinitive && (previous == "l'" ||
            previous in setOf("le", "la", "les") && sentenceTokens.getOrNull(index - 2) in conjugatedVerbs)) return "verb"
    if (previous in determiners) return "noun"
    return if (previous in verbContexts || previous in conjugatedVerbs || infinitive) "verb" else null
}

/** Long dictionary senses translate literally too often when no dictionary entry exists. */
internal fun allowPlainSenseTranslation(englishSense: String): Boolean =
    englishSense.trim().split(Regex("\\s+")).size <= 2

internal fun englishSenseHeadWord(englishSense: String): String? =
    englishSense.trim().split(Regex("\\s+")).firstOrNull {
        it.isNotBlank() && it.lowercase() !in setOf("to", "be", "a", "an", "the")
    }

internal fun isRtlTarget(targetLang: String): Boolean {
    val base = TranslationService.baseLanguage(targetLang)
    return Languages.byCode(base)?.let { it.direction == TextDir.RTL } ?: (base in setOf("ar", "he", "iw", "ur", "ps"))
}
