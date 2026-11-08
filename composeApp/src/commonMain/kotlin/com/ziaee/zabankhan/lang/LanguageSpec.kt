package com.ziaee.zabankhan.lang

enum class TextDir { LTR, RTL }
enum class Script { LATIN, ARABIC, OTHER }

data class DictionaryLink(
    val name: String,
    val urlTemplate: String,
    val meaningLangs: Set<String> = emptySet(),
) {
    fun urlFor(word: String): String = urlTemplate.replace("{word}", percentEncode(word))
    fun supportsMeaningLanguage(code: String): Boolean = meaningLangs.isEmpty() || code in meaningLangs
}

data class OfflineSpeechModel(val name: String, val url: String, val downloadMb: Int)

data class LanguageSpec(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val script: Script,
    val direction: TextDir,
    val elisionPrefixes: List<String>,
    val ttsLocale: String,
    val sttLocale: String,
    val dictionaryLinks: List<DictionaryLink>,
    val onlineVoices: List<String> = emptyList(),
    val voicePreview: String = "",
    val offlineSpeechModel: OfflineSpeechModel? = null,
    val grammarSearchStopWords: Set<String> = emptySet(),
    val grammarAlternativeSeparator: String? = null,
    val vocabularyLessonTag: Regex? = null,
    val ownDictionaryAvailable: Boolean = false,
    val dictionaryLeadingArticles: Set<String> = emptySet(),
)

object Languages {
    val FRENCH = LanguageSpec(
        "fr", "French", "Français", Script.LATIN, TextDir.LTR,
        listOf("l'", "d'", "j'", "m'", "t'", "s'", "n'", "c'", "qu'", "jusqu'", "lorsqu'", "puisqu'"),
        "fr-FR", "fr-FR", listOf(
            // Keep this reference available even when the app's meaning language is Persian;
            // WordReference still provides a useful French-English entry.
            DictionaryLink("WordReference", "https://www.wordreference.com/fren/{word}"),
            DictionaryLink("Reverso Conjugaison", "https://conjugator.reverso.net/conjugation-french-verb-{word}.html"),
            DictionaryLink("B-amooz", "https://dic.b-amooz.com/fr/dictionary/w?word={word}", setOf("fa")),
            DictionaryLink("Glosbe", "https://glosbe.com/fr/fa/{word}", setOf("fa")),
            DictionaryLink("Larousse", "https://www.larousse.fr/dictionnaires/francais/{word}"),
            DictionaryLink("Linguee", "https://www.linguee.com/french-english/search?source=auto&query={word}", setOf("en")),
            DictionaryLink("Wiktionary", "https://fr.wiktionary.org/wiki/{word}"),
        ),
        onlineVoices = listOf("fr-FR-DeniseNeural", "fr-FR-HenriNeural", "fr-FR-VivienneMultilingualNeural", "fr-FR-RemyMultilingualNeural", "fr-CA-SylvieNeural", "fr-CA-AntoineNeural", "fr-BE-CharlineNeural", "fr-CH-ArianeNeural"),
        offlineSpeechModel = OfflineSpeechModel("vosk-model-small-fr-0.22", "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip", 41),
        voicePreview = "Bonjour ! Voici un exemple de cette voix naturelle.",
        grammarSearchStopWords = setOf("et", "ou", "vs", "la", "le", "les", "de", "des", "du", "un", "une"),
        grammarAlternativeSeparator = " ou ",
        vocabularyLessonTag = Regex("\\s*—\\s*Leçon\\s+(\\d+)\\s*$"),
        ownDictionaryAvailable = true,
        dictionaryLeadingArticles = setOf("le", "la", "les", "un", "une", "du", "des", "l'"),
    )
    val ENGLISH = LanguageSpec(
        "en", "English", "English", Script.LATIN, TextDir.LTR, emptyList(), "en-US", "en-US", listOf(
            DictionaryLink("Cambridge", "https://dictionary.cambridge.org/dictionary/english/{word}"),
            DictionaryLink("B-amooz", "https://dic.b-amooz.com/en/dictionary/w?word={word}", setOf("fa")),
            DictionaryLink("Glosbe", "https://glosbe.com/en/fa/{word}", setOf("fa")),
            DictionaryLink("Wiktionary", "https://en.wiktionary.org/wiki/{word}"),
            DictionaryLink("Merriam-Webster", "https://www.merriam-webster.com/dictionary/{word}"),
        ),
        onlineVoices = listOf("en-US-AriaNeural", "en-US-GuyNeural", "en-US-JennyNeural", "en-US-AvaMultilingualNeural", "en-US-AndrewMultilingualNeural", "en-GB-SoniaNeural", "en-GB-RyanNeural", "en-AU-NatashaNeural"),
        offlineSpeechModel = OfflineSpeechModel("vosk-model-small-en-us-0.15", "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", 40),
        voicePreview = "Hello! Here is a sample of this natural voice.",
        grammarSearchStopWords = setOf("and", "or", "vs", "the", "a", "an", "of"),
        grammarAlternativeSeparator = " or ",
        dictionaryLeadingArticles = setOf("the", "a", "an"),
    )
    val PERSIAN = LanguageSpec(
        "fa", "Persian", "فارسی", Script.ARABIC, TextDir.RTL, emptyList(), "fa-IR", "fa-IR", listOf(
            DictionaryLink("Dehkhoda / Vajehyab", "https://vajehyab.com/?q={word}"),
            DictionaryLink("Glosbe", "https://glosbe.com/fa/en/{word}", setOf("en")),
            DictionaryLink("Abadis", "https://abadis.ir/fatofa/{word}/"),
        ),
    )
    val all: List<LanguageSpec> = listOf(FRENCH, ENGLISH, PERSIAN)

    /** Languages offered for learning. Persian is only ever a meaning/translation language. */
    val learnable: List<LanguageSpec> = listOf(FRENCH, ENGLISH)
    fun byCode(code: String): LanguageSpec? = all.firstOrNull { it.code == code.lowercase() }
}

/** RFC 3986 UTF-8 encoding, safe for both path segments and query values. */
fun percentEncode(word: String): String = buildString {
    val hex = "0123456789ABCDEF"
    for (byte in word.encodeToByteArray()) {
        val value = byte.toInt() and 255
        if (value in 65..90 || value in 97..122 || value in 48..57 || value.toChar() in "-._~") append(value.toChar())
        else { append('%'); append(hex[value shr 4]); append(hex[value and 15]) }
    }
}
