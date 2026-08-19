package com.example.scoutface.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutFaceSampleQualityTest {

    private val minFraction = 0.10f
    private val maxYaw = 45f

    private fun ok(faceHeightFraction: Float, yawDegrees: Float) =
        ScoutFaceSampleQuality.isGoodForAutomaticStorage(faceHeightFraction, yawDegrees, minFraction, maxYaw)

    @Test fun `normal frontal face is accepted`() {
        assertTrue(ok(faceHeightFraction = 0.25f, yawDegrees = 0f))
    }

    @Test fun `face exactly at minimum size is accepted -- boundary inclusive`() {
        assertTrue(ok(faceHeightFraction = 0.10f, yawDegrees = 0f))
    }

    @Test fun `face just below minimum size is rejected`() {
        assertFalse(ok(faceHeightFraction = 0.099f, yawDegrees = 0f))
    }

    @Test fun `yaw zero is accepted`() {
        assertTrue(ok(faceHeightFraction = 0.25f, yawDegrees = 0f))
    }

    @Test fun `yaw exactly at the positive bound is accepted -- boundary inclusive`() {
        assertTrue(ok(faceHeightFraction = 0.25f, yawDegrees = 45.0f))
    }

    @Test fun `yaw exactly at the negative bound is accepted -- boundary inclusive`() {
        assertTrue(ok(faceHeightFraction = 0.25f, yawDegrees = -45.0f))
    }

    @Test fun `yaw just outside the positive bound is rejected`() {
        assertFalse(ok(faceHeightFraction = 0.25f, yawDegrees = 45.1f))
    }

    @Test fun `yaw just outside the negative bound is rejected`() {
        assertFalse(ok(faceHeightFraction = 0.25f, yawDegrees = -45.1f))
    }

    @Test fun `good size with bad yaw is rejected`() {
        assertFalse(ok(faceHeightFraction = 0.25f, yawDegrees = 60f))
    }

    @Test fun `bad size with good yaw is rejected`() {
        assertFalse(ok(faceHeightFraction = 0.05f, yawDegrees = 0f))
    }
}
