package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.lang.isRtlText
import com.ziaee.zabankhan.translate.MeaningOption
import com.ziaee.zabankhan.translate.normalizePersian

internal data class MeaningReplacement(val meaning: String, val previousLine: String?)

/** Replace one line in the option's script, retaining the deck's other translations and its "— Leçon N" tag. */
internal fun replaceMeaningLine(meaning: String?, option: String, trailingTag: Regex? = null): MeaningReplacement {
    val tag = meaning?.let { trailingTag?.find(it) }
    val body = (if (tag != null) meaning.removeRange(tag.range) else meaning.orEmpty()).trimEnd()
    val lines = body.takeIf { it.isNotBlank() }?.lines()?.toMutableList() ?: mutableListOf()
    val index = lines.indexOfFirst { it.isNotBlank() && isRtlText(it) == isRtlText(option) }
    val previous = lines.getOrNull(index)
    if (index >= 0) lines[index] = option else lines.add(option)
    val suffix = tag?.value.orEmpty()
    return MeaningReplacement(lines.joinToString("\n") + suffix, previous)
}

internal fun reviewMeaningOptions(
    options: List<MeaningOption>, meaning: String?, applied: String?, previousLine: String?
): List<MeaningOption> {
    val current = (listOfNotNull(meaning) + meaning.orEmpty().lines()).map(::normalizePersian).toSet()
    val previous = previousLine?.let(::normalizePersian)
    val appliedText = applied?.let(::normalizePersian)
    return (options.filter {
        val text = normalizePersian(it.text)
        (text !in current || text == appliedText) && text != previous
    } + listOfNotNull(applied?.takeIf { appliedText in current && appliedText != previous }
        ?.let { MeaningOption(it, null, null) }, previousLine?.let { MeaningOption(it, null, null) }))
        .distinctBy { normalizePersian(it.text) }
}
