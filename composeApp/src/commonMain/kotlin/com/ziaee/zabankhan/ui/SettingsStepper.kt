package com.ziaee.zabankhan.ui

/** Bounds shared by the copied settings stepper and its unit tests. */
internal fun settingsStep(value: Int, delta: Int, minimum: Int, maximum: Int): Int =
    (value.toLong() + delta).coerceIn(minimum.toLong(), maximum.toLong()).toInt()
