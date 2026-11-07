package com.ziaee.zabankhan.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfPathRebaseTest {
    @Test fun oldContainerIsRebased() = assertEquals("/new/Documents/pdfs/a.pdf",
        rebasePdfPath("/old/UUID/Documents/pdfs/a.pdf", "/new/Documents"))
    @Test fun relativeIsResolved() = assertEquals("/new/Documents/pdfs/a.pdf", rebasePdfPath("pdfs/a.pdf", "/new/Documents"))
    @Test fun foreignAbsoluteIsKept() = assertEquals("/other/a.pdf", rebasePdfPath("/other/a.pdf", "/new/Documents"))
}
