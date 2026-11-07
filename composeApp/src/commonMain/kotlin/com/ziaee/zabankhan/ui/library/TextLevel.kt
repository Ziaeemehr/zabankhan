package com.ziaee.zabankhan.ui


val TEXT_LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

fun normalizeTextLevel(value: String?): String? =
    value?.trim()?.uppercase()?.takeIf { it in TEXT_LEVELS }

fun levelFromFolderNames(namesFromRootToLeaf: List<String>): String? =
    namesFromRootToLeaf.asReversed().firstNotNullOfOrNull(::normalizeTextLevel)

internal suspend fun com.ziaee.zabankhan.data.LibraryOrganizerDao.levelForFolder(lang: String, folderId: Long?): String? {
    if (folderId == null) return null
    val folders = getFoldersOnce(lang).associateBy { it.id }
    val names = mutableListOf<String>()
    val visited = mutableSetOf<Long>()
    var id: Long? = folderId
    while (id != null && visited.add(id)) {
        val folder = folders[id] ?: break
        names += folder.name
        id = folder.parentId
    }
    return levelFromFolderNames(names.asReversed())
}
