package com.ziaee.zabankhan.ui.reader

import io.ktor.http.encodeURLParameter

/** A find argument belongs to this reader entry, rather than leaking into later opens. */
fun readerRoute(id: Long, find: String = ""): String =
    "reader/$id" + if (find.isEmpty()) "" else "?find=${find.encodeURLParameter()}"
