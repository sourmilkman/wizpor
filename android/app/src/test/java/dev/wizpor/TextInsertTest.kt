package dev.wizpor

import org.junit.Assert.assertEquals
import org.junit.Test

class TextInsertTest {
    @Test fun emptyField() {
        assertEquals(TextInsert.Result("Hello.", 6), TextInsert.insert("", -1, -1, "Hello."))
    }

    @Test fun appendAddsSpace() {
        assertEquals(TextInsert.Result("Fix this. Then that.", 20), TextInsert.insert("Fix this.", 9, 9, "Then that."))
    }

    @Test fun middleInsertSpacesBothSides() {
        val r = TextInsert.insert("ab cd", 2, 2, "XY")
        assertEquals(TextInsert.Result("ab XY cd", 5), r)
    }

    @Test fun replacesSelection() {
        assertEquals(TextInsert.Result("make it blue", 12), TextInsert.insert("make it red", 8, 11, "blue"))
    }

    @Test fun noSpaceAfterNewline() {
        assertEquals(TextInsert.Result("Line\nNew", 8), TextInsert.insert("Line\n", 5, 5, "New"))
    }

    @Test fun outOfRangeSelectionClamped() {
        assertEquals(TextInsert.Result("ab c", 4), TextInsert.insert("ab", 99, 99, "c"))
    }
}
