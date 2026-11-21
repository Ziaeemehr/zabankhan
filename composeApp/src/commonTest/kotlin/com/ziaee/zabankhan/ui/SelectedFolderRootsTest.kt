package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.LibraryFolder
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectedFolderRootsTest {
    @Test
    fun skipsIdsWhoseAncestorIsSelected() {
        val folders = listOf(
            LibraryFolder(id = 1, lang = "fr", name = "a"),
            LibraryFolder(id = 2, lang = "fr", name = "b", parentId = 1),
            LibraryFolder(id = 3, lang = "fr", name = "c", parentId = 2),
            LibraryFolder(id = 4, lang = "fr", name = "d"),
        )
        assertEquals(listOf(1L, 4L), selectedFolderRoots(folders, listOf(1, 3, 4)))
        assertEquals(listOf(3L), selectedFolderRoots(folders, listOf(3)))
    }
}
