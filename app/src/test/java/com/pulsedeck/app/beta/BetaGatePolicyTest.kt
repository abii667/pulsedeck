package com.pulsedeck.app.beta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BetaGatePolicyTest {
    @Test
    fun lockedStateBlocksNormalAppSurfaces() {
        val locked = lockedState()

        assertFalse(BetaGatePolicy.isSurfaceAllowed(locked, BetaGatePolicy.Surface.LocalLibrary))
        assertFalse(BetaGatePolicy.isSurfaceAllowed(locked, BetaGatePolicy.Surface.LocalPlayback))
        assertFalse(BetaGatePolicy.isSurfaceAllowed(locked, BetaGatePolicy.Surface.PremiumDeck))
        assertFalse(BetaGatePolicy.isSurfaceAllowed(locked, BetaGatePolicy.Surface.PulseRadio))
        assertFalse(BetaGatePolicy.isSurfaceAllowed(locked, BetaGatePolicy.Surface.AudioDeck))
        assertFalse(BetaGatePolicy.isSurfaceAllowed(locked, BetaGatePolicy.Surface.Settings))
    }

    @Test
    fun lockedStateStillAllowsFeedbackAndContactOnly() {
        val locked = lockedState()

        assertTrue(BetaGatePolicy.isSurfaceAllowed(locked, BetaGatePolicy.Surface.Feedback))
        assertTrue(BetaGatePolicy.isSurfaceAllowed(locked, BetaGatePolicy.Surface.ContactDeveloper))
        assertFalse(BetaGatePolicy.isSurfaceAllowed(locked, BetaGatePolicy.Surface.CheckForUpdate))
    }

    @Test
    fun activeStateAllowsNormalSurfaces() {
        val active = BetaAccessState(
            allowed = true,
            status = BetaLicenseStatus.Active,
            message = "Active",
            expiresAtEpochMs = 1L,
            requiresActivation = false,
            offlineGraceRemainingMs = null,
        )

        assertTrue(BetaGatePolicy.isSurfaceAllowed(active, BetaGatePolicy.Surface.LocalLibrary))
        assertTrue(BetaGatePolicy.isSurfaceAllowed(active, BetaGatePolicy.Surface.PremiumDeck))
        assertTrue(BetaGatePolicy.isSurfaceAllowed(active, BetaGatePolicy.Surface.PulseRadio))
    }

    @Test
    fun feedbackRedactionStripsUrlsPathsCodesAndSecrets() {
        val redacted = BetaLicenseCodec.redactedFeedbackText(
                "url=https://rr.googlevideo.com/videoplayback?signature=abc token=secret " +
                "path=/storage/emulated/0/Music/private.flac invite: PD-TEST-AB12-CD34 " +
                "lyrics: these are private lyric words " +
                "C:\\Users\\tester\\Music\\song.flac",
        )

        assertFalse(redacted.contains("googlevideo", ignoreCase = true))
        assertFalse(redacted.contains("/storage/emulated"))
        assertFalse(redacted.contains("PD-TEST-AB12-CD34"))
        assertFalse(redacted.contains("private lyric words"))
        assertFalse(redacted.contains("C:\\Users"))
        assertTrue(redacted.contains("[redacted"))
    }

    @Test
    fun currentBuildAllowsOnlyHighEndAndSixGbMonitored() {
        assertTrue(BetaTesterTier.HIGH_END.isAllowedForCurrentBuild())
        assertTrue(BetaTesterTier.SIX_GB_MONITORED.isAllowedForCurrentBuild())
        assertFalse(BetaTesterTier.FOUR_GB_STAGED.isAllowedForCurrentBuild())
        assertFalse(BetaTesterTier.LOW_END.isAllowedForCurrentBuild())
    }

    @Test
    fun sensitiveAuditDoesNotAllowPrivateKeyNeedles() {
        assertTrue("BEGIN PRIVATE KEY" in BetaSensitiveInfoAudit.forbiddenApkNeedles)
        assertTrue("BEGIN PUBLIC KEY" in BetaSensitiveInfoAudit.allowedApkNeedles)
        assertFalse(BetaSensitiveInfoAudit.allowedApkNeedles.any { it.contains("PRIVATE KEY") })
    }

    @Test
    fun inviteCodesAreNotPartOfAllowedApkContent() {
        assertFalse(BetaSensitiveInfoAudit.allowedApkNeedles.any { it.startsWith("PD-TEST-") })
        assertTrue(BetaSensitiveInfoAudit.forbiddenApkNeedles.any { it.startsWith("PD-TEST-") })
    }

    @Test
    fun betaGatePolicyPreventsPlaybackInitializationWhenLocked() {
        val locked = lockedState()

        assertFalse(BetaGatePolicy.isSurfaceAllowed(locked, BetaGatePolicy.Surface.LocalPlayback))
        assertFalse(BetaGatePolicy.isSurfaceAllowed(locked, BetaGatePolicy.Surface.PremiumDeck))
        assertFalse(BetaGatePolicy.isSurfaceAllowed(locked, BetaGatePolicy.Surface.PulseRadio))
    }

    private fun lockedState(): BetaAccessState =
        BetaAccessState(
            allowed = false,
            status = BetaLicenseStatus.Expired,
            message = "Locked",
            expiresAtEpochMs = 1L,
            requiresActivation = false,
            offlineGraceRemainingMs = null,
        )
}
