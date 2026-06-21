package com.pulsedeck.app.beta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class BetaLicenseVerifierTest {
    private val keyPair = testKeyPair()
    private val publicPem = publicPem(keyPair)
    private val build = BetaBuildIdentity(
        betaBuildId = "phase29_beta_001",
        versionCode = 1,
        versionName = "0.1.0",
        packageName = "com.pulsedeck.app",
        allowedBetaTiers = setOf(BetaTesterTier.HIGH_END, BetaTesterTier.SIX_GB_MONITORED),
    )
    private val verifier = BetaLicenseVerifier(publicPem, build)

    @Test
    fun missingLicenseRequiresActivation() {
        val state = verifier.verify(
            snapshot = StoredBetaLicenseSnapshot(null, null, null, BetaLicenseStatus.Missing),
            deviceKeyId = "device",
            nowEpochMs = NOW,
        )

        assertFalse(state.allowed)
        assertTrue(state.requiresActivation)
        assertEquals(BetaLicenseStatus.Missing, state.status)
    }

    @Test
    fun validActiveSignedLicenseAllowsApp() {
        val license = signedLicense(deviceKeyId = "device")
        val state = verifier.verifyLicense(
            license = license,
            deviceKeyId = "device",
            nowEpochMs = NOW,
            serverTimeEpochMs = NOW,
            online = true,
        )

        assertTrue(state.allowed)
        assertEquals(BetaLicenseStatus.Active, state.status)
    }

    @Test
    fun expiredLicenseLocksApp() {
        val license = signedLicense(deviceKeyId = "device", expiresAt = NOW - 1)
        val state = verifier.verifyLicense(license, "device", NOW, serverTimeEpochMs = NOW, online = true)

        assertFalse(state.allowed)
        assertEquals(BetaLicenseStatus.Expired, state.status)
    }

    @Test
    fun revokedLicenseLocksApp() {
        val license = signedLicense(deviceKeyId = "device", status = BetaLicenseStatus.Revoked)
        val state = verifier.verifyLicense(license, "device", NOW, serverTimeEpochMs = NOW, online = true)

        assertFalse(state.allowed)
        assertEquals(BetaLicenseStatus.Revoked, state.status)
    }

    @Test
    fun invalidSignatureLocksApp() {
        val license = signedLicense(deviceKeyId = "device").copy(signature = "bad")
        val state = verifier.verifyLicense(license, "device", NOW, serverTimeEpochMs = NOW, online = true)

        assertFalse(state.allowed)
        assertEquals(BetaLicenseStatus.InvalidSignature, state.status)
    }

    @Test
    fun wrongBuildLocksApp() {
        val license = signedLicense(deviceKeyId = "device", buildId = "old_build")
        val state = verifier.verifyLicense(license, "device", NOW, serverTimeEpochMs = NOW, online = true)

        assertFalse(state.allowed)
        assertEquals(BetaLicenseStatus.BuildExpired, state.status)
    }

    @Test
    fun wrongDeviceLocksApp() {
        val license = signedLicense(deviceKeyId = "device-a")
        val state = verifier.verifyLicense(license, "device-b", NOW, serverTimeEpochMs = NOW, online = true)

        assertFalse(state.allowed)
        assertEquals(BetaLicenseStatus.DeviceMismatch, state.status)
    }

    @Test
    fun tierBlockedLocksApp() {
        val license = signedLicense(deviceKeyId = "device", testerTier = BetaTesterTier.LOW_END)
        val state = verifier.verifyLicense(license, "device", NOW, serverTimeEpochMs = NOW, online = true)

        assertFalse(state.allowed)
        assertEquals(BetaLicenseStatus.TierBlocked, state.status)
    }

    @Test
    fun offlineWithinGraceAllowsApp() {
        val license = signedLicense(deviceKeyId = "device", maxOfflineGrace = DAY_MS)
        val state = verifier.verify(
            snapshot = StoredBetaLicenseSnapshot(
                license = license,
                lastVerifiedServerTimeEpochMs = NOW,
                lastSuccessfulRefreshEpochMs = NOW,
                lastRefreshStatus = BetaLicenseStatus.Active,
            ),
            deviceKeyId = "device",
            nowEpochMs = NOW + 2_000L,
            online = false,
        )

        assertTrue(state.allowed)
        assertTrue((state.offlineGraceRemainingMs ?: 0L) > 0L)
    }

    @Test
    fun offlineBeyondGraceLocksApp() {
        val license = signedLicense(deviceKeyId = "device", maxOfflineGrace = 1_000L)
        val state = verifier.verify(
            snapshot = StoredBetaLicenseSnapshot(license, NOW, NOW, BetaLicenseStatus.Active),
            deviceKeyId = "device",
            nowEpochMs = NOW + 2_000L,
            online = false,
        )

        assertFalse(state.allowed)
        assertEquals(BetaLicenseStatus.OfflineGraceExpired, state.status)
    }

    @Test
    fun localClockRollbackLocksOfflineState() {
        val license = signedLicense(deviceKeyId = "device")
        val state = verifier.verify(
            snapshot = StoredBetaLicenseSnapshot(license, NOW, NOW, BetaLicenseStatus.Active),
            deviceKeyId = "device",
            nowEpochMs = NOW - BetaLicenseVerifier.CLOCK_ROLLBACK_TOLERANCE_MS - 1L,
            online = false,
        )

        assertFalse(state.allowed)
        assertEquals(BetaLicenseStatus.OfflineGraceExpired, state.status)
    }

    @Test
    fun canonicalSigningIsDeterministic() {
        val license = signedLicense(deviceKeyId = "device")

        assertEquals(
            BetaLicenseCodec.canonicalPayload(license),
            BetaLicenseCodec.canonicalPayload(BetaLicenseCodec.decode(BetaLicenseCodec.encode(license))),
        )
    }

    @Test
    fun clearingLocalStoreCannotCreateNewTrialLocally() {
        val state = verifier.verify(
            snapshot = StoredBetaLicenseSnapshot(null, null, null, BetaLicenseStatus.Unknown),
            deviceKeyId = "device",
            nowEpochMs = NOW,
        )

        assertFalse(state.allowed)
        assertEquals(BetaLicenseStatus.Missing, state.status)
    }

    private fun signedLicense(
        deviceKeyId: String,
        buildId: String = build.betaBuildId,
        status: BetaLicenseStatus = BetaLicenseStatus.Active,
        testerTier: BetaTesterTier = BetaTesterTier.HIGH_END,
        expiresAt: Long = NOW + 5 * DAY_MS,
        maxOfflineGrace: Long = DAY_MS,
    ): BetaLicense {
        val unsigned = BetaLicense(
            licenseId = "lic_1",
            testerIdHash = "tester_hash",
            buildId = buildId,
            deviceKeyId = deviceKeyId,
            activatedAtEpochMs = NOW,
            expiresAtEpochMs = expiresAt,
            serverIssuedAtEpochMs = NOW,
            maxOfflineGraceMs = maxOfflineGrace,
            status = status,
            testerTier = testerTier,
            signature = "",
        )
        return unsigned.copy(signature = sign(unsigned))
    }

    private fun sign(license: BetaLicense): String {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keyPair.private)
        signature.update(BetaLicenseCodec.canonicalPayload(license).toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    companion object {
        private const val NOW = 1_800_000_000_000L
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}

private fun testKeyPair(): KeyPair {
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(ECGenParameterSpec("secp256r1"))
    return generator.generateKeyPair()
}

private fun publicPem(keyPair: KeyPair): String {
    val body = Base64.getMimeEncoder(64, "\n".toByteArray())
        .encodeToString(keyPair.public.encoded)
    return "-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----"
}
