package com.ziaee.zabankhan.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStepperTest {
    @Test fun dailyCardsCannotStepOutsideAllowedRange() {
        assertEquals(0, settingsStep(0, -1, 0, 100))
        assertEquals(100, settingsStep(100, 1, 0, 100))
        assertEquals(11, settingsStep(10, 1, 0, 100))
    }
    @Test fun goalMinutesKeepTheirExistingBounds() {
        assertEquals(1, settingsStep(1, -1, 1, 120))
        assertEquals(120, settingsStep(120, 1, 1, 120))
        assertEquals(9, settingsStep(10, -1, 1, 120))
    }
}
