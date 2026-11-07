package com.ziaee.zabankhan.data

/** FrenchReader VocabPrefs.AudioAutoplay. */
enum class AudioAutoplay(val front: Boolean, val back: Boolean) {
    OFF(false, false), FRONT(true, false), BACK(false, true), BOTH(true, true);
    companion object {
        fun restored(value: String?, legacyFront: Boolean): AudioAutoplay =
            entries.firstOrNull { it.name == value } ?: if (legacyFront) FRONT else OFF
    }
}
