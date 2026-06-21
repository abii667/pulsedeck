package com.pulsedeck.app.beta

import com.pulsedeck.app.BuildConfig

data class BetaLicense(
    val licenseId: String,
    val testerIdHash: String,
    val buildId: String,
    val deviceKeyId: String,
    val activatedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val serverIssuedAtEpochMs: Long,
    val maxOfflineGraceMs: Long,
    val status: BetaLicenseStatus,
    val testerTier: BetaTesterTier,
    val signature: String,
)

enum class BetaTesterTier {
    HIGH_END,
    SIX_GB_MONITORED,
    FOUR_GB_STAGED,
    LOW_END,
    UNKNOWN,
}

enum class BetaLicenseStatus {
    Active,
    Expired,
    Revoked,
    DeviceMismatch,
    BuildExpired,
    TierBlocked,
    IntegrityFailed,
    OfflineGraceExpired,
    InvalidSignature,
    Missing,
    Unknown,
}

data class BetaAccessState(
    val allowed: Boolean,
    val status: BetaLicenseStatus,
    val message: String,
    val expiresAtEpochMs: Long?,
    val requiresActivation: Boolean,
    val offlineGraceRemainingMs: Long?,
)

data class BetaBuildIdentity(
    val betaBuildId: String,
    val versionCode: Long,
    val versionName: String,
    val packageName: String,
    val allowedBetaTiers: Set<BetaTesterTier>,
)

data class StoredBetaLicenseSnapshot(
    val license: BetaLicense?,
    val lastVerifiedServerTimeEpochMs: Long?,
    val lastSuccessfulRefreshEpochMs: Long?,
    val lastRefreshStatus: BetaLicenseStatus,
)

data class BetaActivationResult(
    val accessState: BetaAccessState,
    val serverTimeEpochMs: Long?,
)

data class BetaRefreshResult(
    val accessState: BetaAccessState,
    val serverTimeEpochMs: Long?,
)

data class BetaFeedbackPayload(
    val issueCategory: String,
    val testerMessage: String,
    val deviceModel: String,
    val androidVersion: String,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val appVersionName: String = PulseDeckBetaBuild.identity.versionName,
    val appVersionCode: Long = PulseDeckBetaBuild.identity.versionCode,
    val packageName: String = PulseDeckBetaBuild.identity.packageName,
    val outputRoute: String? = null,
    val sourceType: String? = null,
    val redactedDiagnostics: String? = null,
)

object PulseDeckBetaBuild {
    val identity = BetaBuildIdentity(
        betaBuildId = BuildConfig.PULSEDECK_BETA_BUILD_ID,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        versionName = BuildConfig.VERSION_NAME,
        packageName = BuildConfig.APPLICATION_ID,
        allowedBetaTiers = setOf(
            BetaTesterTier.HIGH_END,
            BetaTesterTier.SIX_GB_MONITORED,
        ),
    )

    val blockedTiers = setOf(
        BetaTesterTier.FOUR_GB_STAGED,
        BetaTesterTier.LOW_END,
    )
}

fun BetaTesterTier.isAllowedForCurrentBuild(): Boolean =
    this in PulseDeckBetaBuild.identity.allowedBetaTiers

fun BetaLicenseStatus.isTerminalLock(): Boolean =
    this in setOf(
        BetaLicenseStatus.Expired,
        BetaLicenseStatus.Revoked,
        BetaLicenseStatus.DeviceMismatch,
        BetaLicenseStatus.BuildExpired,
        BetaLicenseStatus.TierBlocked,
        BetaLicenseStatus.IntegrityFailed,
        BetaLicenseStatus.OfflineGraceExpired,
        BetaLicenseStatus.InvalidSignature,
    )
