package com.sockjaw.clipsy

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.sockjaw.clipsy.ui.components.TokenTransformation
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenPaddingTest {
    private val pad = ' '
    private val t = TokenTransformation(Color.Black).filter(AnnotatedString("on {date}!"))
    private val m = t.offsetMapping

    @Test fun padsEachTokenOnScreenOnly() = assertEquals("on $pad{date}$pad!", t.text.text)

    @Test fun cursorBeforeTokenSitsOutsideTheChip() = assertEquals(3, m.originalToTransformed(3))

    @Test fun cursorAfterTokenSitsOutsideTheChip() = assertEquals(11, m.originalToTransformed(9))

    @Test fun everyScreenOffsetMapsBackInsideTheText() {
        for (i in 0..t.text.length) {
            val o = m.transformedToOriginal(i)
            assert(o in 0.."on {date}!".length) { "offset $i -> $o" }
        }
        assertEquals(3, m.transformedToOriginal(4)) // between pad and token
        assertEquals(9, m.transformedToOriginal(10)) // between token and pad
        assertEquals(10, m.transformedToOriginal(t.text.length))
    }

    @Test fun roundTripsPlainOffsets() {
        for (o in listOf(0, 1, 2, 10)) assertEquals(o, m.transformedToOriginal(m.originalToTransformed(o)))
    }

    @Test fun newPlaceholdersGetChipsToo() {
        val shown = TokenTransformation(Color.Black).filter(AnnotatedString("{greeting} {snippet:sig} {counter:4}")).text.text
        assertEquals("$pad{greeting}$pad $pad{snippet:sig}$pad $pad{counter:4}$pad", shown)
    }
}
