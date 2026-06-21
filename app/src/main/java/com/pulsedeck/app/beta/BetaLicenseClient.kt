package com.pulsedeck.app.beta

import android.content.Context
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.pulsedeck.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BetaLicenseClient(context: Context) {
    private val appContext = context.applicationContext
    private val functions: FirebaseFunctions? = runCatching {
        if (FirebaseApp.getApps(appContext).isEmpty()) null else FirebaseFunctions.getInstance()
    }.getOrNull()?.also { firebaseFunctions ->
        if (BuildConfig.DEBUG && BuildConfig.PULSEDECK_USE_FIREBASE_EMULATOR) {
            firebaseFunctions.useEmulator("10.0.2.2", 5001)
        }
    }

    suspend fun activateBeta(
        inviteCode: String,
        testerEmail: String,
        deviceKeyId: String,
        devicePublicKey: String,
        deviceSecuritySummary: String,
    ): Pair<BetaLicense?, BetaActivationResult> {
        val callable = functions ?: return null to unavailable(BetaLicenseStatus.Unknown, "Firebase is not configured yet.")
        val request = mapOf(
            "inviteCode" to inviteCode.trim(),
            "testerEmail" to testerEmail.trim(),
            "buildId" to PulseDeckBetaBuild.identity.betaBuildId,
            "versionCode" to PulseDeckBetaBuild.identity.versionCode,
            "versionName" to PulseDeckBetaBuild.identity.versionName,
            "packageName" to PulseDeckBetaBuild.identity.packageName,
            "deviceKeyId" to deviceKeyId,
            "devicePublicKey" to devicePublicKey,
            "deviceSecuritySummary" to deviceSecuritySummary,
            "appTier" to BetaDeviceTierPolicy.estimateTesterTier(appContext).name,
            "deviceSummary" to BetaDeviceTierPolicy.deviceSummary(appContext),
        )
        return runCatching {
            val result = callable.getHttpsCallable("activateBeta").call(request).awaitTask()
            val map = result.getData() as? Map<*, *> ?: emptyMap<String, Any?>()
            val license = map["license"]?.let(::jsonFromAny)?.let(BetaLicenseCodec::decode)
            val serverTime = (map["serverTimeEpochMs"] as? Number)?.toLong()
            val status = BetaLicenseCodec.enumValue((map["status"] as? String).orEmpty(), BetaLicenseStatus.Active)
            if (license != null && serverTime != null && status == BetaLicenseStatus.Active) {
                license to BetaActivationResult(
                    accessState = BetaAccessState(
                        allowed = true,
                        status = BetaLicenseStatus.Active,
                        message = "PulseDeck beta access active.",
                        expiresAtEpochMs = license.expiresAtEpochMs,
                        requiresActivation = false,
                        offlineGraceRemainingMs = null,
                    ),
                    serverTimeEpochMs = serverTime,
                )
            } else {
                null to BetaActivationResult(
                    accessState = BetaAccessState(
                        allowed = false,
                        status = status,
                        message = (map["message"] as? String) ?: "Activation was denied.",
                        expiresAtEpochMs = license?.expiresAtEpochMs,
                        requiresActivation = true,
                        offlineGraceRemainingMs = null,
                    ),
                    serverTimeEpochMs = serverTime,
                )
            }
        }.getOrElse { error ->
            null to BetaActivationResult(
                accessState = BetaAccessState(
                    allowed = false,
                    status = BetaLicenseStatus.Unknown,
                    message = cleanErrorMessage(error),
                    expiresAtEpochMs = null,
                    requiresActivation = true,
                    offlineGraceRemainingMs = null,
                ),
                serverTimeEpochMs = null,
            )
        }
    }

    suspend fun refreshBetaLicense(license: BetaLicense, deviceKeyStore: BetaDeviceKeyStore): Pair<BetaLicense?, BetaRefreshResult> {
        val callable = functions ?: return null to refreshUnavailable("Firebase is not configured yet.")
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val nonceBase64 = Base64.getEncoder().encodeToString(nonce)
        val request = mapOf(
            "licenseId" to license.licenseId,
            "buildId" to PulseDeckBetaBuild.identity.betaBuildId,
            "deviceKeyId" to deviceKeyStore.getDeviceKeyId(),
            "nonce" to nonceBase64,
            "nonceSignature" to deviceKeyStore.signNonce(nonce),
            "packageName" to PulseDeckBetaBuild.identity.packageName,
            "versionCode" to PulseDeckBetaBuild.identity.versionCode,
            "versionName" to PulseDeckBetaBuild.identity.versionName,
        )
        return runCatching {
            val result = callable.getHttpsCallable("refreshBetaLicense").call(request).awaitTask()
            val map = result.getData() as? Map<*, *> ?: emptyMap<String, Any?>()
            val refreshedLicense = map["license"]?.let(::jsonFromAny)?.let(BetaLicenseCodec::decode)
            val serverTime = (map["serverTimeEpochMs"] as? Number)?.toLong()
            val status = BetaLicenseCodec.enumValue((map["status"] as? String).orEmpty(), BetaLicenseStatus.Unknown)
            val access = BetaAccessState(
                allowed = status == BetaLicenseStatus.Active,
                status = status,
                message = (map["message"] as? String) ?: if (status == BetaLicenseStatus.Active) {
                    "PulseDeck beta access active."
                } else {
                    "PulseDeck beta access is locked."
                },
                expiresAtEpochMs = refreshedLicense?.expiresAtEpochMs ?: license.expiresAtEpochMs,
                requiresActivation = false,
                offlineGraceRemainingMs = null,
            )
            refreshedLicense to BetaRefreshResult(access, serverTime)
        }.getOrElse { error ->
            null to refreshUnavailable(cleanErrorMessage(error))
        }
    }

    suspend fun submitBetaFeedback(license: BetaLicense?, payload: BetaFeedbackPayload): Boolean {
        val callable = functions ?: return false
        val request = mapOf(
            "licenseId" to (license?.licenseId ?: ""),
            "anonymousBetaId" to (license?.testerIdHash ?: ""),
            "testerTier" to (license?.testerTier ?: BetaTesterTier.UNKNOWN).name,
            "deviceModel" to Build.MODEL,
            "androidVersion" to Build.VERSION.RELEASE,
            "appBuild" to PulseDeckBetaBuild.identity.betaBuildId,
            "versionName" to payload.appVersionName,
            "versionCode" to payload.appVersionCode,
            "packageName" to payload.packageName,
            "timestampEpochMs" to payload.timestampEpochMs,
            "outputRoute" to payload.outputRoute.orEmpty(),
            "sourceType" to payload.sourceType.orEmpty(),
            "issueCategory" to payload.issueCategory,
            "testerMessage" to BetaLicenseCodec.redactedFeedbackText(payload.testerMessage),
            "redactedDiagnostics" to BetaLicenseCodec.redactedFeedbackText(payload.redactedDiagnostics.orEmpty()),
        )
        return runCatching {
            callable.getHttpsCallable("submitBetaFeedback").call(request).awaitTask()
            true
        }.getOrDefault(false)
    }

    private fun unavailable(status: BetaLicenseStatus, message: String): BetaActivationResult =
        BetaActivationResult(
            accessState = BetaAccessState(
                allowed = false,
                status = status,
                message = message,
                expiresAtEpochMs = null,
                requiresActivation = true,
                offlineGraceRemainingMs = null,
            ),
            serverTimeEpochMs = null,
        )

    private fun refreshUnavailable(message: String): BetaRefreshResult =
        BetaRefreshResult(
            accessState = BetaAccessState(
                allowed = false,
                status = BetaLicenseStatus.Unknown,
                message = message,
                expiresAtEpochMs = null,
                requiresActivation = false,
                offlineGraceRemainingMs = null,
            ),
            serverTimeEpochMs = null,
        )

    private fun jsonFromAny(value: Any): JSONObject =
        when (value) {
            is JSONObject -> value
            is Map<*, *> -> JSONObject(value.mapKeys { it.key.toString() })
            else -> JSONObject(value.toString())
        }

    private fun cleanErrorMessage(error: Throwable): String =
        "Beta server unavailable: ${error.javaClass.simpleName}"
}

private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
