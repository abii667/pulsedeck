package com.pulsedeck.app.beta

import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Locale

object BetaLicenseCodec {
    fun encode(license: BetaLicense, includeSignature: Boolean = true): JSONObject =
        JSONObject()
            .put("licenseId", license.licenseId)
            .put("testerIdHash", license.testerIdHash)
            .put("buildId", license.buildId)
            .put("deviceKeyId", license.deviceKeyId)
            .put("activatedAtEpochMs", license.activatedAtEpochMs)
            .put("expiresAtEpochMs", license.expiresAtEpochMs)
            .put("serverIssuedAtEpochMs", license.serverIssuedAtEpochMs)
            .put("maxOfflineGraceMs", license.maxOfflineGraceMs)
            .put("status", license.status.name)
            .put("testerTier", license.testerTier.name)
            .apply {
                if (includeSignature) put("signature", license.signature)
            }

    fun decode(raw: String): BetaLicense {
        val json = JSONObject(raw)
        return decode(json)
    }

    fun decode(json: JSONObject): BetaLicense =
        BetaLicense(
            licenseId = json.optString("licenseId"),
            testerIdHash = json.optString("testerIdHash"),
            buildId = json.optString("buildId"),
            deviceKeyId = json.optString("deviceKeyId"),
            activatedAtEpochMs = json.optLong("activatedAtEpochMs"),
            expiresAtEpochMs = json.optLong("expiresAtEpochMs"),
            serverIssuedAtEpochMs = json.optLong("serverIssuedAtEpochMs"),
            maxOfflineGraceMs = json.optLong("maxOfflineGraceMs"),
            status = enumValue(json.optString("status"), BetaLicenseStatus.Unknown),
            testerTier = enumValue(json.optString("testerTier"), BetaTesterTier.UNKNOWN),
            signature = json.optString("signature"),
        )

    fun canonicalPayload(license: BetaLicense): String =
        canonicalJson(encode(license, includeSignature = false))

    fun canonicalJson(value: Any?): String =
        when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(
                prefix = "{",
                postfix = "}",
                separator = ",",
            ) { key ->
                "${JSONObject.quote(key)}:${canonicalJson(value.get(key))}"
            }
            is JSONArray -> (0 until value.length()).joinToString(
                prefix = "[",
                postfix = "]",
                separator = ",",
            ) { index -> canonicalJson(value.get(index)) }
            is Number, is Boolean -> value.toString()
            else -> JSONObject.quote(value.toString())
        }

    fun publicKeyFromPem(pem: String): PublicKey {
        val base64 = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val encoded = Base64.getDecoder().decode(base64)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
    }

    fun verifySignature(publicKey: PublicKey, license: BetaLicense): Boolean {
        if (license.signature.isBlank()) return false
        return runCatching {
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(publicKey)
            signature.update(canonicalPayload(license).toByteArray(Charsets.UTF_8))
            signature.verify(Base64.getDecoder().decode(license.signature))
        }.getOrDefault(false)
    }

    fun sha256Hex(raw: String): String =
        sha256Hex(raw.toByteArray(Charsets.UTF_8))

    fun sha256Hex(raw: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw)
            .joinToString("") { "%02x".format(Locale.US, it) }

    fun base64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    fun redactedFeedbackText(raw: String): String =
        raw
            .replace("""(?i)(lyrics?|syncedLyrics|plainLyrics)\s*[:=]\s*[^\r\n]+""".toRegex(), "$1=[redacted-lyrics]")
            .replace("""https?://[^\s)>\]]+""".toRegex(RegexOption.IGNORE_CASE), "[redacted-url]")
            .replace("""(?i)googlevideo[^\s)>\]]*""".toRegex(), "[redacted-googlevideo]")
            .replace("""(?i)(signature|sig|token|key)=([^&\s]+)""".toRegex(), "$1=[redacted]")
            .replace("""(?i)(invite|code)\s*[:=]\s*[A-Z0-9-]{6,}""".toRegex(), "$1=[redacted]")
            .replace("""(?i)(license|private[_ -]?key|bearer)\s*[:=]\s*[A-Za-z0-9+/=._-]{10,}""".toRegex(), "$1=[redacted]")
            .replace("""(/[^\s]+)+""".toRegex(), "[redacted-path]")
            .replace("""[A-Za-z]:\\[^\r\n\t ]+""".toRegex(), "[redacted-path]")

    inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}

object BetaSensitiveInfoAudit {
    val forbiddenApkNeedles = listOf(
        "LICENSE_SIGNING_PRIVATE_KEY_PEM",
        "BEGIN PRIVATE KEY",
        "BEGIN EC PRIVATE KEY",
        "firebase-adminsdk",
        "service_account",
        "database_password",
        "resolver_secret",
        "PD-TEST-",
    )

    val allowedApkNeedles = listOf(
        "BEGIN PUBLIC KEY",
        "phase29_beta_001",
        "phase35_beta_001",
        "com.pulsedeck.app",
    )
}
