package com.pulsedeck.app.beta

import java.security.PublicKey

class BetaLicenseVerifier(
    publicKeyPem: String = DEFAULT_PUBLIC_KEY_PEM,
    private val buildIdentity: BetaBuildIdentity = PulseDeckBetaBuild.identity,
) {
    private val publicKey: PublicKey = BetaLicenseCodec.publicKeyFromPem(publicKeyPem)

    fun verify(
        snapshot: StoredBetaLicenseSnapshot,
        deviceKeyId: String,
        nowEpochMs: Long,
        serverTimeEpochMs: Long? = null,
        online: Boolean = false,
    ): BetaAccessState {
        val license = snapshot.license ?: return missingState()
        return verifyLicense(
            license = license,
            deviceKeyId = deviceKeyId,
            nowEpochMs = nowEpochMs,
            serverTimeEpochMs = serverTimeEpochMs,
            lastVerifiedServerTimeEpochMs = snapshot.lastVerifiedServerTimeEpochMs,
            lastSuccessfulRefreshEpochMs = snapshot.lastSuccessfulRefreshEpochMs,
            online = online,
        )
    }

    fun verifyLicense(
        license: BetaLicense,
        deviceKeyId: String,
        nowEpochMs: Long,
        serverTimeEpochMs: Long? = null,
        lastVerifiedServerTimeEpochMs: Long? = null,
        lastSuccessfulRefreshEpochMs: Long? = null,
        online: Boolean = false,
    ): BetaAccessState {
        if (!BetaLicenseCodec.verifySignature(publicKey, license)) {
            return locked(BetaLicenseStatus.InvalidSignature, "Beta license signature is invalid.", license.expiresAtEpochMs)
        }
        if (license.buildId != buildIdentity.betaBuildId) {
            return locked(BetaLicenseStatus.BuildExpired, "This beta license is for a different build.", license.expiresAtEpochMs)
        }
        if (license.deviceKeyId != deviceKeyId) {
            return locked(BetaLicenseStatus.DeviceMismatch, "This beta license belongs to another device.", license.expiresAtEpochMs)
        }
        if (license.testerTier !in buildIdentity.allowedBetaTiers) {
            return locked(BetaLicenseStatus.TierBlocked, "This tester tier is not enabled for this beta build.", license.expiresAtEpochMs)
        }
        if (license.status != BetaLicenseStatus.Active) {
            return locked(license.status, statusMessage(license.status), license.expiresAtEpochMs)
        }

        val trustedNow = serverTimeEpochMs ?: estimatedTrustedTime(
            nowEpochMs = nowEpochMs,
            lastVerifiedServerTimeEpochMs = lastVerifiedServerTimeEpochMs,
            lastSuccessfulRefreshEpochMs = lastSuccessfulRefreshEpochMs,
        )
        if (trustedNow > license.expiresAtEpochMs) {
            return locked(BetaLicenseStatus.Expired, "PulseDeck beta access ended.", license.expiresAtEpochMs)
        }
        if (!online && lastVerifiedServerTimeEpochMs != null && nowEpochMs + CLOCK_ROLLBACK_TOLERANCE_MS < lastVerifiedServerTimeEpochMs) {
            return locked(
                BetaLicenseStatus.OfflineGraceExpired,
                "Online validation is required because the device clock changed.",
                license.expiresAtEpochMs,
            )
        }

        if (!online) {
            val lastRefresh = lastSuccessfulRefreshEpochMs ?: license.serverIssuedAtEpochMs
            val offlineElapsed = (nowEpochMs - lastRefresh).coerceAtLeast(0L)
            if (offlineElapsed > license.maxOfflineGraceMs) {
                return locked(
                    BetaLicenseStatus.OfflineGraceExpired,
                    "Online validation is required to continue this beta.",
                    license.expiresAtEpochMs,
                )
            }
            val remaining = (license.maxOfflineGraceMs - offlineElapsed).coerceAtLeast(0L)
            return BetaAccessState(
                allowed = true,
                status = BetaLicenseStatus.Active,
                message = "PulseDeck beta is available in offline grace.",
                expiresAtEpochMs = license.expiresAtEpochMs,
                requiresActivation = false,
                offlineGraceRemainingMs = remaining,
            )
        }

        return BetaAccessState(
            allowed = true,
            status = BetaLicenseStatus.Active,
            message = "PulseDeck beta access active.",
            expiresAtEpochMs = license.expiresAtEpochMs,
            requiresActivation = false,
            offlineGraceRemainingMs = null,
        )
    }

    private fun estimatedTrustedTime(
        nowEpochMs: Long,
        lastVerifiedServerTimeEpochMs: Long?,
        lastSuccessfulRefreshEpochMs: Long?,
    ): Long {
        if (lastVerifiedServerTimeEpochMs == null || lastSuccessfulRefreshEpochMs == null) return nowEpochMs
        val elapsed = (nowEpochMs - lastSuccessfulRefreshEpochMs).coerceAtLeast(0L)
        return lastVerifiedServerTimeEpochMs + elapsed
    }

    private fun missingState(): BetaAccessState =
        BetaAccessState(
            allowed = false,
            status = BetaLicenseStatus.Missing,
            message = "Enter your PulseDeck beta invite code.",
            expiresAtEpochMs = null,
            requiresActivation = true,
            offlineGraceRemainingMs = null,
        )

    private fun locked(status: BetaLicenseStatus, message: String, expiresAtEpochMs: Long?): BetaAccessState =
        BetaAccessState(
            allowed = false,
            status = status,
            message = message,
            expiresAtEpochMs = expiresAtEpochMs,
            requiresActivation = status == BetaLicenseStatus.Missing,
            offlineGraceRemainingMs = null,
        )

    private fun statusMessage(status: BetaLicenseStatus): String =
        when (status) {
            BetaLicenseStatus.Active -> "PulseDeck beta access active."
            BetaLicenseStatus.Expired -> "PulseDeck beta access ended."
            BetaLicenseStatus.Revoked -> "This beta license was revoked."
            BetaLicenseStatus.DeviceMismatch -> "This beta license belongs to another device."
            BetaLicenseStatus.BuildExpired -> "This beta build is no longer enabled."
            BetaLicenseStatus.TierBlocked -> "This tester tier is not enabled for this beta build."
            BetaLicenseStatus.IntegrityFailed -> "Device integrity validation failed."
            BetaLicenseStatus.OfflineGraceExpired -> "Online validation is required to continue this beta."
            BetaLicenseStatus.InvalidSignature -> "Beta license signature is invalid."
            BetaLicenseStatus.Missing -> "Enter your PulseDeck beta invite code."
            BetaLicenseStatus.Unknown -> "Beta license status is unknown."
        }

    companion object {
        const val DEFAULT_PUBLIC_KEY_PEM = """
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEodxawiOFfPpXbFt6j4rec+xS/D3b
R2NQxsU+tRR+LhBmqv/J9vplAqAi/Ic0pW/CAGjmhDvWEoalMB7Ft2ve2A==
-----END PUBLIC KEY-----
"""
        const val CLOCK_ROLLBACK_TOLERANCE_MS = 5L * 60L * 1000L
    }
}
