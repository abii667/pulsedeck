package com.pulsedeck.app.beta

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BetaAccessController(context: Context) {
    private val appContext = context.applicationContext
    private val store = BetaLicenseStore(appContext)
    private val deviceKeyStore = BetaDeviceKeyStore()
    private val verifier = BetaLicenseVerifier()
    private val client = BetaLicenseClient(appContext)

    suspend fun loadInitialState(nowEpochMs: Long = System.currentTimeMillis()): Pair<BetaAccessState, BetaLicense?> =
        withContext(Dispatchers.IO) {
            val snapshot = store.load()
            val deviceKeyId = deviceKeyStore.getDeviceKeyId()
            val state = verifier.verify(
                snapshot = snapshot,
                deviceKeyId = deviceKeyId,
                nowEpochMs = nowEpochMs,
                online = false,
            )
            state to snapshot.license
        }

    suspend fun activate(inviteCode: String, testerEmail: String): Pair<BetaAccessState, BetaLicense?> =
        withContext(Dispatchers.IO) {
            val trimmed = inviteCode.trim()
            val email = testerEmail.trim()
            if (trimmed.isBlank() || email.isBlank()) {
                return@withContext BetaAccessState(
                    allowed = false,
                    status = BetaLicenseStatus.Missing,
                    message = "Enter your PulseDeck beta invite code and approved email.",
                    expiresAtEpochMs = null,
                    requiresActivation = true,
                    offlineGraceRemainingMs = null,
                ) to null
            }
            val (license, result) = client.activateBeta(
                inviteCode = trimmed,
                testerEmail = email,
                deviceKeyId = deviceKeyStore.getDeviceKeyId(),
                devicePublicKey = deviceKeyStore.getOrCreatePublicKeyBase64(),
                deviceSecuritySummary = deviceKeyStore.getSecurityLevelSummary(),
            )
            val serverTime = result.serverTimeEpochMs
            if (license != null && serverTime != null) {
                store.saveLicense(license, serverTime, refreshedAtEpochMs = System.currentTimeMillis())
                verifier.verifyLicense(
                    license = license,
                    deviceKeyId = deviceKeyStore.getDeviceKeyId(),
                    nowEpochMs = System.currentTimeMillis(),
                    serverTimeEpochMs = serverTime,
                    online = true,
                ) to license
            } else {
                result.accessState to null
            }
        }

    suspend fun refreshOnline(currentLicense: BetaLicense?): Pair<BetaAccessState, BetaLicense?> =
        withContext(Dispatchers.IO) {
            val license = currentLicense ?: store.load().license
            if (license == null) {
                return@withContext BetaAccessState(
                    allowed = false,
                    status = BetaLicenseStatus.Missing,
                    message = "Enter your PulseDeck beta invite code.",
                    expiresAtEpochMs = null,
                    requiresActivation = true,
                    offlineGraceRemainingMs = null,
                ) to null
            }
            val (refreshedLicense, result) = client.refreshBetaLicense(license, deviceKeyStore)
            val serverTime = result.serverTimeEpochMs
            if (refreshedLicense != null && serverTime != null) {
                store.saveLicense(refreshedLicense, serverTime, result.accessState.status, System.currentTimeMillis())
                val verified = verifier.verifyLicense(
                    license = refreshedLicense,
                    deviceKeyId = deviceKeyStore.getDeviceKeyId(),
                    nowEpochMs = System.currentTimeMillis(),
                    serverTimeEpochMs = serverTime,
                    online = true,
                )
                verified to refreshedLicense
            } else {
                if (serverTime != null) {
                    store.saveRefreshStatus(result.accessState.status, serverTime)
                }
                result.accessState to license
            }
        }

    suspend fun submitFeedback(license: BetaLicense?, message: String, category: String = "beta_lock"): Boolean =
        client.submitBetaFeedback(
            license = license,
            payload = BetaFeedbackPayload(
                issueCategory = category,
                testerMessage = message,
                deviceModel = android.os.Build.MODEL,
                androidVersion = android.os.Build.VERSION.RELEASE,
                redactedDiagnostics = BetaDeviceTierPolicy.deviceSummary(appContext),
            ),
        )
}

object BetaGatePolicy {
    enum class Surface {
        LocalLibrary,
        LocalPlayback,
        PremiumDeck,
        PulseRadio,
        AudioDeck,
        Settings,
        Feedback,
        CheckForUpdate,
        ContactDeveloper,
    }

    fun isSurfaceAllowed(accessState: BetaAccessState, surface: Surface): Boolean =
        accessState.allowed || surface in setOf(
            Surface.Feedback,
            Surface.ContactDeveloper,
        )
}
