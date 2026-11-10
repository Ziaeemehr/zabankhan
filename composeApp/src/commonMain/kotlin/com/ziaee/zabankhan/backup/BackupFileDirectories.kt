package com.ziaee.zabankhan.backup

internal val REQUIRED_FILE_DIRECTORIES = listOf("text_bodies", "text_images", "videos")

/** A text-only/older archive does not own the live PDF directory. */
internal fun restoreFileDirectories(): List<String> = REQUIRED_FILE_DIRECTORIES
