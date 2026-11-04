package com.ziaee.zabankhan.lang

interface LemmaLexicon {
    val wordForms: Set<String> get() = emptySet()
    val commonLemmas: Set<String>
    fun lemmas(form: String): Set<String>
    fun containsForm(form: String): Boolean
}

class InMemoryLemmaLexicon(
    private val forms: Map<String, Set<String>>,
    override val commonLemmas: Set<String>,
    override val wordForms: Set<String> = forms.keys
) : LemmaLexicon {
    override fun lemmas(form: String): Set<String> = forms[form] ?: setOf(form)
    override fun containsForm(form: String): Boolean = form in forms
}

/** TSV rows contain a form, a tab, and comma-separated lemmas. */
fun loadLemmaLexicon(tsv: String, commonLemmaText: String, lang: LanguageSpec): LemmaLexicon {
    val forms = linkedMapOf<String, MutableSet<String>>()
    val rawForms = linkedSetOf<String>()
    for (line in tsv.lineSequence()) {
        val separator = line.indexOf('\t')
        if (separator <= 0 || separator == line.lastIndex) continue
        val rawForm = line.substring(0, separator).trim()
        val rawLemmas = line.substring(separator + 1).split(',').map(String::trim).filter(String::isNotEmpty)
        if (rawForm.isEmpty() || rawLemmas.isEmpty()) continue
        rawForms += rawForm.lowercase()
        rawForms += rawLemmas.map(String::lowercase)
        forms.getOrPut(normalizeForMatch(rawForm, lang)) { linkedSetOf() }
            .addAll(rawLemmas.map { normalizeForMatch(it, lang) })
    }
    val common = commonLemmaText.lineSequence().map { normalizeForMatch(it.trim(), lang) }
        .filter(String::isNotBlank).toSet()
    return InMemoryLemmaLexicon(forms, common, rawForms)
}
