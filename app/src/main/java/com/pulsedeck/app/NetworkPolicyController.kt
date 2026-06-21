package com.pulsedeck.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.pulsedeck.app.settings.model.OnlineFeatureSettings
import com.pulsedeck.app.settings.model.StreamPreviewNetworkPolicy
import com.pulsedeck.app.settings.model.StreamingQualityPolicy

internal enum class PulseNetworkType {
    Wifi,
    Cellular,
    Ethernet,
    Other,
    Unknown,
}

internal data class NetworkPolicySnapshot(
    val hasActiveNetwork: Boolean = false,
    val networkType: PulseNetworkType = PulseNetworkType.Unknown,
    val isMetered: Boolean = false,
    val isRoaming: Boolean = false,
    val androidDataSaverEnabled: Boolean = false,
    val downstreamKbps: Int? = null,
) {
    val isCellularOrMetered: Boolean
        get() = networkType == PulseNetworkType.Cellular || isMetered || isRoaming
    val isNoNetwork: Boolean
        get() = !hasActiveNetwork
}

internal data class StreamingDataPolicy(
    val network: NetworkPolicySnapshot = NetworkPolicySnapshot(),
    val userDataSaverEnabled: Boolean = false,
    val effectiveDataSaver: Boolean = false,
    val quality: StreamingQualityPolicy = StreamingQualityPolicy.High,
    val maxAudioBitrateKbps: Int? = null,
    val allowMuxedFallback: Boolean = true,
    val allowPreviewPreparation: Boolean = true,
    val policyLabel: String = "unrestricted",
) {
    val shouldBypassCachedStreamUrl: Boolean
        get() = maxAudioBitrateKbps != null || !allowMuxedFallback

    companion object {
        val Unrestricted = StreamingDataPolicy()
    }
}

internal object NetworkPolicyController {
    fun currentSnapshot(context: Context): NetworkPolicySnapshot {
        val connectivityManager = context.applicationContext
            .getSystemService(ConnectivityManager::class.java)
            ?: return NetworkPolicySnapshot()
        return runCatching {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let(connectivityManager::getNetworkCapabilities)
            val networkType = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> PulseNetworkType.Wifi
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> PulseNetworkType.Cellular
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> PulseNetworkType.Ethernet
                capabilities != null -> PulseNetworkType.Other
                else -> PulseNetworkType.Unknown
            }
            val isMetered = runCatching { connectivityManager.isActiveNetworkMetered }
                .getOrElse { networkType == PulseNetworkType.Cellular }
            val isRoaming = networkType == PulseNetworkType.Cellular &&
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) == false
            val androidDataSaverEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                runCatching {
                    connectivityManager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
                }.getOrDefault(false)
            val hasInternetCapability = activeNetwork != null &&
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            NetworkPolicySnapshot(
                hasActiveNetwork = hasInternetCapability,
                networkType = networkType,
                isMetered = isMetered,
                isRoaming = isRoaming,
                androidDataSaverEnabled = androidDataSaverEnabled,
                downstreamKbps = capabilities?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
            )
        }.getOrDefault(NetworkPolicySnapshot())
    }

    fun currentPolicy(
        context: Context,
        settings: OnlineFeatureSettings = PulseOnlineRuntime.settings,
    ): StreamingDataPolicy =
        policyForSnapshot(currentSnapshot(context), settings)

    fun policyForSnapshot(
        snapshot: NetworkPolicySnapshot,
        settings: OnlineFeatureSettings,
    ): StreamingDataPolicy {
        val effectiveDataSaver = settings.pulseDataSaver || snapshot.androidDataSaverEnabled
        val quality = when {
            effectiveDataSaver -> settings.dataSaverStreamingQuality
            snapshot.isRoaming -> settings.roamingStreamingQuality
            snapshot.isCellularOrMetered -> settings.cellularStreamingQuality
            else -> settings.wifiStreamingQuality
        }
        val allowMuxedFallback = when {
            effectiveDataSaver -> settings.allowMuxedStreamFallbackInDataSaver
            snapshot.isCellularOrMetered -> settings.allowMuxedStreamFallbackOnCellular
            else -> true
        }
        val allowPreviewPreparation = when (settings.streamPreviewNetworkPolicy) {
            StreamPreviewNetworkPolicy.AnyNetwork -> true
            StreamPreviewNetworkPolicy.WifiAndUnmetered -> !effectiveDataSaver && !snapshot.isCellularOrMetered
            StreamPreviewNetworkPolicy.Off -> false
        }
        val label = when {
            snapshot.isNoNetwork -> "no_network"
            effectiveDataSaver -> "data_saver"
            snapshot.isRoaming -> "roaming"
            snapshot.isCellularOrMetered -> "cellular"
            else -> "wifi"
        }
        return StreamingDataPolicy(
            network = snapshot,
            userDataSaverEnabled = settings.pulseDataSaver,
            effectiveDataSaver = effectiveDataSaver,
            quality = quality,
            maxAudioBitrateKbps = quality.maxAudioBitrateKbps,
            allowMuxedFallback = allowMuxedFallback,
            allowPreviewPreparation = allowPreviewPreparation,
            policyLabel = label,
        )
    }
}

internal val OnlineFeatureSettings.premiumDeckOfflineActive: Boolean
    get() = offlineMode || premiumDeckOfflineMode

internal fun StreamingDataPolicy.premiumDeckOfflineRecommendationText(): String? =
    when {
        network.isNoNetwork -> "No network detected. Turn on Offline Deck to show only saved PremiumDeck music."
        network.networkType == PulseNetworkType.Cellular || network.isMetered || network.isRoaming || effectiveDataSaver ->
            "You're using mobile data.\nTurn on Offline Deck to play only saved music and avoid PremiumDeck data use."
        else -> null
    }
