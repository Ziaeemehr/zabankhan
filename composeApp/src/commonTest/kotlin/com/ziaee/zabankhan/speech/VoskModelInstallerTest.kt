package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.content.ZipArchive
import kotlin.test.*

class VoskModelInstallerTest {
    private val model = mapOf("vosk-en/am/final.mdl" to byteArrayOf(1), "vosk-en/conf/model.conf" to byteArrayOf(2))
    @Test fun stripsModelRootAndRetainsContents() {
        val files = voskModelEntries(ZipArchive.writeStored(model))
        assertContentEquals(byteArrayOf(1), files["am/final.mdl"])
        assertContentEquals(byteArrayOf(2), files["conf/model.conf"])
    }
    @Test fun rejectsEmptyMixedRootAndNonModelArchives() {
        for (entries in listOf(emptyMap(), mapOf("model/readme" to byteArrayOf(1)), model + ("other/readme" to byteArrayOf(1)))) {
            assertFailsWith<IllegalArgumentException> { voskModelEntries(ZipArchive.writeStored(entries)) }
        }
    }
    @Test fun rejectsTraversalWithoutWritingAnything() {
        assertFailsWith<IllegalArgumentException> { voskModelEntries(ZipArchive.writeStored(model + ("vosk-en/../escape" to byteArrayOf(1)))) }
    }
}
