package com.voyarina.caliedu

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.voyarina.caliedu.ui.SymEntry
import com.voyarina.caliedu.ui.SymItem
import com.voyarina.caliedu.ui.Symbols
import com.voyarina.caliedu.ui.insertSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 符号插入的落点。
 *
 * 按钮上看着对、插进去错位一格，是这类输入辅助最典型的 bug，
 * 所以这里把光标位置也一并钉住，不只看插完的文本。
 */
class SymbolPadTest {

    private fun value(text: String, caret: Int) = TextFieldValue(text, TextRange(caret))

    @Test
    fun insertsAtCaretNotAtEnd() {
        val out = "ab".let { value(it, 1) }.insertSymbol(SymItem("+"), focused = true)
        assertEquals("a+b", out.text)
        assertEquals(2, out.selection.start)
    }

    @Test
    fun replacesSelectedRange() {
        val out = value("abc", 1).copy(selection = TextRange(1, 2)).insertSymbol(SymItem("+"), true)
        assertEquals("a+c", out.text)
        assertEquals(2, out.selection.start)
    }

    @Test
    fun bracketPairLeavesCaretInside() {
        val out = value("", 0).insertSymbol(SymItem("( )", "()", back = 1), true)
        assertEquals("()", out.text)
        assertEquals(1, out.selection.start)
    }

    @Test
    fun caretFallsBackToEndWhenNotFocused() {
        // 没焦点时拿到的选区是 0，插到开头会很意外；接着往下写才符合预期。
        val out = value("x", 0).insertSymbol(SymItem("x²", "²"), focused = false)
        assertEquals("x²", out.text)
        assertEquals(2, out.selection.start)
    }

    @Test
    fun backIsClampedToInsertedText() {
        val out = value("", 0).insertSymbol(SymItem("越界", "ab", back = 9), true)
        assertEquals("ab", out.text)
        assertEquals(0, out.selection.start)
    }

    @Test
    fun superscriptLeavesCaretInsideBraces() {
        val out = value("x", 1).insertSymbol(SymItem("x²", "^{}", back = 1), focused = true)
        assertEquals("x^{}", out.text)
        // 光标停在 { 和 } 之间（x ^ { | }），接着打数字就是 x^{2}
        assertEquals(3, out.selection.start)
    }

    @Test
    fun superscriptAndSubscriptAreTemplates() {
        val inserts = Symbols.ENTRIES.filterIsInstance<SymEntry.Key>().map { it.item.insert }
        assertTrue("符号条里应有上标模板", inserts.contains("^{}"))
        assertTrue("符号条里应有下标模板", inserts.contains("_{}"))
    }

    @Test
    fun everyEntryIsUsable() {
        assertTrue(Symbols.ENTRIES.isNotEmpty())
        Symbols.ENTRIES.forEach { entry ->
            when (entry) {
                is SymEntry.Group -> {
                    assertTrue("组「${entry.face}」没有内容", entry.items.isNotEmpty())
                    entry.items.forEach { assertTrue("「${entry.face}」里有空标签", it.label.isNotBlank()) }
                }

                is SymEntry.Key -> assertTrue(entry.item.label.isNotBlank())
            }
        }
    }
}
