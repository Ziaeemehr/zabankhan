package com.ziaee.zabankhan.lang

/** The first letter determines direction; digits, punctuation and emoji are neutral. */
fun isRtlText(text: String): Boolean {
    for (char in text) {
        if (!char.isLetter()) continue
        return char.code in 0x0590..0x08FF || char.code in 0xFB1D..0xFDFF ||
            char.code in 0xFE70..0xFEFF
    }
    return false
}
