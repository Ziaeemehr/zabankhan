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
)

object Languages {
    val FRENCH = LanguageSpec(
        "fr", "French", "Français", Script.LATIN, TextDir.LTR,
        listOf("l'", "d'", "j'", "m'", "t'", "s'", "n'", "c'", "qu'", "jusqu'", "lorsqu'", "puisqu'"),
        "fr-FR", "fr-FR", listOf(
            DictionaryLink("WordReference", "https://www.wordreference.com/fren/{word}", setOf("en")),
            DictionaryLink("Reverso Conjugaison", "https://conjugator.reverso.net/conjugation-french-verb-{word}.html"),
            DictionaryLink("B-amooz", "https://dic.b-amooz.com/fr/dictionary/w?word={word}", setOf("fa")),
            DictionaryLink("Glosbe", "https://glosbe.com/fr/fa/{word}", setOf("fa")),
            DictionaryLink("Larousse", "https://www.larousse.fr/dictionnaires/francais/{word}"),
            DictionaryLink("Linguee", "https://www.linguee.com/french-english/search?source=auto&query={word}", setOf("en")),
            DictionaryLink("Wiktionary", "https://fr.wiktionary.org/wiki/{word}"),
        ),
    )
    val ENGLISH = LanguageSpec(
        "en", "English", "English", Script.LATIN, TextDir.LTR, emptyList(), "en-US", "en-US", listOf(
            DictionaryLink("Cambridge", "https://dictionary.cambridge.org/dictionary/english/{word}"),
            DictionaryLink("B-amooz", "https://dic.b-amooz.com/en/dictionary/w?word={word}", setOf("fa")),
            DictionaryLink("Glosbe", "https://glosbe.com/en/fa/{word}", setOf("fa")),
            DictionaryLink("Wiktionary", "https://en.wiktionary.org/wiki/{word}"),
            DictionaryLink("Merriam-Webster", "https://www.merriam-webster.com/dictionary/{word}"),
        ),
    )
    val PERSIAN = LanguageSpec(
        "fa", "Persian", "فارسی", Script.ARABIC, TextDir.RTL, emptyList(), "fa-IR", "fa-IR", listOf(
            DictionaryLink("Dehkhoda / Vajehyab", "https://vajehyab.com/?q={word}"),
            DictionaryLink("Glosbe", "https://glosbe.com/fa/en/{word}", setOf("en")),
            DictionaryLink("Abadis", "https://abadis.ir/fatofa/{word}/"),
        ),
    )
    val all: List<LanguageSpec> = listOf(FRENCH, ENGLISH, PERSIAN)
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
