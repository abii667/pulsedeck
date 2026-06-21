package com.pulsedeck.app.beta

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class BetaDeviceKeyStore {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun getOrCreatePublicKeyBase64(): String {
        ensureKeyPair()
        val cert = keyStore.getCertificate(KEY_ALIAS)
        return Base64.getEncoder().encodeToString(cert.publicKey.encoded)
    }

    fun getDeviceKeyId(): String =
        BetaLicenseCodec.sha256Hex(Base64.getDecoder().decode(getOrCreatePublicKeyBase64()))

    fun signNonce(nonce: ByteArray): String {
        ensureKeyPair()
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(nonce)
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    fun getSecurityLevelSummary(): String {
        ensureKeyPair()
        return runCatching {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
            val keyFactory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
            val keyInfo = keyFactory.getKeySpec(privateKey, KeyInfo::class.java)
            val backing = when {
                Build.VERSION.SDK_INT >= 31 && keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX -> "strongbox"
                Build.VERSION.SDK_INT >= 31 && keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "tee"
                keyInfo.isInsideSecureHardware -> "hardware"
                else -> "software"
            }
            "ec_p256_$backing"
        }.getOrElse {
            "ec_p256_security_unknown"
        }
    }

    private fun ensureKeyPair() {
        if (keyStore.containsAlias(KEY_ALIAS)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    companion object {
        const val KEY_ALIAS = "pulsedeck_beta_device_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
