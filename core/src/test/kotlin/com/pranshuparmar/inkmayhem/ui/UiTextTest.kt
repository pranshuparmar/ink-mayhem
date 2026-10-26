package com.pranshuparmar.inkmayhem.ui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UiTextTest {
    @Test
    fun `numbers group like toLocaleString`() {
        assertEquals("0", Format.thousands(0))
        assertEquals("999", Format.thousands(999))
        assertEquals("1,000", Format.thousands(1000))
        assertEquals("12,345", Format.thousands(12345))
        assertEquals("1,234,567", Format.thousands(1234567))
        assertEquals("-12,345", Format.thousands(-12345))
    }

    @Test
    fun `a repeated toast within 3 s is ignored unless forced`() {
        val t = Toast()
        t.show("a", 0.0)
        t.show("b", 0.5)
        assertEquals("b", t.message)
        t.show("b", 1.0) // same message, 0.5 s later: ignored, keeps the original timing
        assertEquals(0f, t.visibility(2.9, reducedMotion = true)) // 2.4 s after 0.5: hidden
        t.show("b", 3.0, force = true)
        assertEquals(1f, t.visibility(3.5, reducedMotion = true))
    }
}
