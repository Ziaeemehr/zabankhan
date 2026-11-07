package com.ziaee.zabankhan.images

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleImageStoreTest {
    @Test fun scalingRetainsAspectRatioAndNeverEnlarges() {
        assertEquals(1080 to 540, scaledDimensions(2160, 1080, 1080))
        assertEquals(640 to 480, scaledDimensions(640, 480, 1080))
        assertEquals(4, sampleSizeFor(4320, 1080))
    }
    @Test fun ownedPathsCannotEscapeImageDirectory() {
        assertTrue(isOwnedArticleImagePath("text_images/en/dataset/story.jpg"))
        listOf("text_images/../database.db", "/text_images/x.jpg", "text_images/", "text_images/a\\b.jpg").forEach {
            assertFalse(isOwnedArticleImagePath(it))
        }
    }
}
