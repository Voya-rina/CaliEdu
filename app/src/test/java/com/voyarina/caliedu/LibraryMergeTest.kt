package com.voyarina.caliedu

import com.voyarina.caliedu.data.PdfItem
import com.voyarina.caliedu.data.PdfLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件夹扫描结果和单独收的文件要在同一个列表里，且互不覆盖。
 */
class LibraryMergeTest {

    private fun item(name: String, uri: String = "content://doc/$name") = PdfItem(name = name, uri = uri, size = 10)

    @Test
    fun markdownIsRecognisedAndStillCountsAsText() {
        assertTrue(item("讲义.md").isMarkdown)
        assertTrue(item("讲义.MARKDOWN").isMarkdown)
        assertTrue("md 也能当素材出题，isText 要留着", item("讲义.md").isText)
        assertFalse(item("论文.pdf").isMarkdown)
        assertFalse(item("notes.txt").isMarkdown)
    }

    @Test
    fun singleFilesSitNextToScannedOnes() {
        val lib = PdfLibrary(items = listOf(item("a.pdf")))
            .plus(item("b.md"))
        assertEquals(listOf("a.pdf", "b.md"), lib.allItems.map { it.name })
        assertEquals(2, lib.allItems.size)
    }

    @Test
    fun rescanDoesNotDropSingleFiles() {
        // 重新扫描时只替换 items，extraItems 原样带过来。
        val scanned = PdfLibrary(items = listOf(item("c.pdf")))
        val resaved = scanned.copy(extraItems = pdfLibraryWith("b.md").extraItems)
        assertEquals(listOf("c.pdf", "b.md"), resaved.allItems.map { it.name })
    }

    @Test
    fun sameFileIsNotListedTwice() {
        val file = item("a.pdf")
        val lib = PdfLibrary(items = listOf(file)).plus(file)
        assertEquals(1, lib.allItems.size)
        assertEquals("已经在文件夹里扫到的，不该再记一份", 0, lib.extraItems.size)
    }

    @Test
    fun pickingTwiceKeepsOneRow() {
        val lib = pdfLibraryWith("b.md").plus(item("b.md"))
        assertEquals(1, lib.allItems.size)
    }

    @Test
    fun removeOnlyDropsTheReference() {
        val kept = item("a.pdf")
        val dropped = item("b.md")
        val lib = PdfLibrary(items = listOf(kept), extraItems = listOf(dropped))
        val next = lib.minus(dropped)
        assertEquals(listOf("a.pdf"), next.allItems.map { it.name })
        assertEquals("文件夹扫到的不能被 minus 动", 1, next.items.size)
    }

    @Test
    fun countsFollowTheMergedList() {
        val lib = PdfLibrary(items = listOf(item("a.pdf")))
            .plus(item("b.md"))
            .plus(item("c.txt"))
        assertEquals(1, lib.pdfCount)
        assertEquals(2, lib.usableCount)
        assertTrue(lib.hasAny)
        assertEquals(30L, lib.totalSize)
    }

    @Test
    fun emptyLibraryHasNothing() {
        assertFalse(PdfLibrary().hasAny)
    }

    private fun pdfLibraryWith(name: String) = PdfLibrary().plus(item(name))
}
