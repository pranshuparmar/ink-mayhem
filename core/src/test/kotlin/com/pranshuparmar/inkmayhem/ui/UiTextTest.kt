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
        t.update(2.9, reducedMotion = true) // 2.4 s after 0.5: hidden
        assertEquals(0f, t.visibility)
        t.show("b", 3.0, force = true)
        t.update(3.5, reducedMotion = true)
        assertEquals(1f, t.visibility)
    }

    @Test
    fun `replacing a visible toast keeps it on screen`() {
        val t = Toast()
        for (i in 0..30) t.update(i / 60.0, reducedMotion = false)
        t.show("first", 0.5)
        var now = 0.5
        while (now < 1.0) { now += 1 / 60.0; t.update(now, reducedMotion = false) }
        assertEquals(1f, t.visibility)
        t.show("second", now)
        t.update(now + 1 / 60.0, reducedMotion = false)
        assertEquals(1f, t.visibility, "no fade-out/in when the text changes")
        assertEquals("second", t.message)
    }
}
