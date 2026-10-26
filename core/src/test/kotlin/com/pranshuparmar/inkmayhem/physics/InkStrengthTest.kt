package com.pranshuparmar.inkmayhem.physics

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verified POC behaviours: what each ink holds across a 5 m gap. Crates are 0.5 m and dropped 2 m
 * onto the bridge (the setup that reproduces these results on the POC's Planck.js engine; see
 * reference/harness). Each case runs at light, middle and heavy pressure within the band.
 */
class InkStrengthTest {

    @ParameterizedTest(name = "rope p={0} holds 0.25 kg")
    @ValueSource(floats = [0.12f, 0.2f, 0.28f])
    fun `rope holds a light crate`(p: Float) {
        val r = Bridges.crate(p, 5f, 0.25f)
        assertEquals(2, r.anchors, "both ends should pin to the ground")
        assertTrue(r.held, "crate fell to y=${r.crateY} after ${r.snaps} snaps")
    }

    @ParameterizedTest(name = "rope p={0} snaps under 1.9 kg")
    @ValueSource(floats = [0.12f, 0.2f, 0.28f])
    fun `rope snaps under a medium crate`(p: Float) {
        val r = Bridges.crate(p, 5f, 1.9f)
        assertFalse(r.held, "crate stayed at y=${r.crateY}")
        assertTrue(r.snaps > 0)
    }

    @ParameterizedTest(name = "spring p={0} holds 1.9 kg")
    @ValueSource(floats = [0.38f, 0.47f, 0.56f])
    fun `spring holds a medium crate`(p: Float) {
        val r = Bridges.crate(p, 5f, 1.9f)
        assertTrue(r.held, "crate fell to y=${r.crateY} after ${r.snaps} snaps")
    }

    @ParameterizedTest(name = "spring p={0} snaps under 9.7 kg")
    @ValueSource(floats = [0.38f, 0.47f, 0.56f])
    fun `spring snaps under a heavy crate`(p: Float) {
        val r = Bridges.crate(p, 5f, 9.7f)
        assertFalse(r.held, "crate stayed at y=${r.crateY}")
    }

    @ParameterizedTest(name = "beam p={0} holds 9.7 kg")
    @ValueSource(floats = [0.68f, 0.8f, 0.95f])
    fun `beam holds a heavy crate`(p: Float) {
        val r = Bridges.crate(p, 5f, 9.7f)
        assertEquals(1, r.bodies, "consecutive beam segments merge into one body")
        assertTrue(r.held, "crate fell to y=${r.crateY} after ${r.snaps} snaps")
    }
}
