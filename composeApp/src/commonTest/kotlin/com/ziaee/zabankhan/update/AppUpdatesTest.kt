package com.ziaee.zabankhan.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppUpdatesTest {
    @Test
    fun offersOnlyNewerStableReleaseWithMatchingApk() {
        fun release(tag: String, asset: String, prerelease: Boolean = false) =
            """{"tag_name":"$tag","prerelease":$prerelease,"assets":[{"name":"$asset","state":"uploaded"}]}"""

        assertEquals("1.10.0", newerAndroidRelease(release("v1.10.0", "zabankhan-1.10.0-play.apk"), "1.9.0"))
        assertNull(newerAndroidRelease(release("v1.9.0", "zabankhan-1.9.0-play.apk"), "1.9.0"))
        assertNull(newerAndroidRelease(release("v1.10.0", "zabankhan-1.10.0-play.aab"), "1.9.0"))
        assertNull(newerAndroidRelease(release("v1.10.0", "zabankhan-1.10.0-play.apk", true), "1.9.0"))
    }
}
