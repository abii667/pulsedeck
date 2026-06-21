package com.pulsedeck.app.settings.runtime

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import com.pulsedeck.app.OutputBitDepth
import com.pulsedeck.app.OutputMode
import com.pulsedeck.app.OutputSettings

data class AudioOutputRuntimeInspection(
    val deviceDiagnostics: OutputDeviceDiagnosticsSnapshot,
    val capabilities: DeviceCapabilities,
    val preferredDevice: AudioDeviceInfo?,
)

fun inspectAudioOutputRuntime(
    context: Context,
    output: OutputSettings,
): AudioOutputRuntimeInspection {
    val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    val requestedRoute = outputDeviceTypeForProfile(output.deviceProfile)
    val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList().orEmpty()
    } else {
        emptyList()
    }
    val availableRoutes = devices.mapNotNull(::deviceRouteType).distinct()
    val preferredDevice = devices.firstOrNull { deviceRouteType(it) == requestedRoute }
    val supportedSampleRates = preferredDevice
        ?.sampleRates
        ?.filter { it > 0 }
        ?.distinct()
        ?.sorted()
        .orEmpty()
    val supportedEncodings = preferredDevice
        ?.encodings
        ?.map(::audioEncodingLabel)
        ?.distinct()
        .orEmpty()
    val usbDetected = devices.any { deviceRouteType(it) == OutputDeviceType.UsbDac }
    val hiResCapable = supportedSampleRates.any { it > 48_000 } ||
        preferredDevice?.encodings?.toList().orEmpty().any(::isHiResPcmEncoding)
    val requestedHiRes = output.mode == OutputMode.HiRes ||
        output.hiResEnabled ||
        output.bitDepth.isHiResDepth() ||
        output.sampleRate.toSampleRateHzOrNull()?.let { it > 48_000 } == true
    val preferredRouteApplied = requestedRoute == OutputDeviceType.UsbDac && preferredDevice != null
    val routeVerified = when (requestedRoute) {
        OutputDeviceType.UsbDac -> preferredRouteApplied
        OutputDeviceType.Speaker,
        OutputDeviceType.WiredHeadsetAux,
        OutputDeviceType.Bluetooth,
        OutputDeviceType.Other -> preferredDevice != null
        OutputDeviceType.Chromecast -> false
    }
    val diagnostics = OutputDeviceDiagnosticsSnapshot(
        requestedRoute = requestedRoute,
        activeRoute = if (routeVerified) requestedRoute else null,
        preferredRoute = requestedRoute.takeIf { preferredDevice != null },
        preferredDeviceName = preferredDevice?.productName?.toString()?.takeIf { it.isNotBlank() },
        preferredDeviceApplied = preferredRouteApplied,
        routeVerified = routeVerified,
        usbDacDetected = usbDetected,
        supportedSampleRatesHz = supportedSampleRates,
        supportedEncodings = supportedEncodings,
        availableRoutes = availableRoutes,
        reason = when {
            devices.isEmpty() -> "Android output-device list is unavailable."
            requestedRoute == OutputDeviceType.UsbDac && !usbDetected -> "No USB DAC output device is currently reported by Android."
            requestedRoute == OutputDeviceType.UsbDac && preferredDevice != null -> "USB DAC output device is present; PulseDeck requested it as the preferred Media3 output device."
            requestedHiRes && !hiResCapable -> "Requested hi-res output, but Android did not report hi-res sample-rate or PCM encoding support for the selected profile."
            requestedHiRes -> "Android reports hi-res-capable output format options for the selected profile; final active AudioTrack format still requires playback verification."
            routeVerified -> "Android reports an output device matching the selected profile."
            else -> "Selected output profile is not currently reported by Android."
        },
    )
    val capabilities = DeviceCapabilities(
        supportsAaudio = false,
        supportsHiRes = hiResCapable,
        supportsCast = false,
        supportsUsbDac = usbDetected,
    )
    return AudioOutputRuntimeInspection(
        deviceDiagnostics = diagnostics,
        capabilities = capabilities,
        preferredDevice = preferredDevice.takeIf { requestedRoute == OutputDeviceType.UsbDac },
    )
}

fun deviceRouteTypeForAndroidType(type: Int): OutputDeviceType? =
    when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> OutputDeviceType.Speaker
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> OutputDeviceType.WiredHeadsetAux
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> OutputDeviceType.Bluetooth
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_DOCK -> OutputDeviceType.UsbDac
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC,
        AudioDeviceInfo.TYPE_AUX_LINE -> OutputDeviceType.Other
        else -> null
    }

private fun deviceRouteType(device: AudioDeviceInfo): OutputDeviceType? =
    deviceRouteTypeForAndroidType(device.type)

fun audioEncodingLabel(encoding: Int): String =
    when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> "PCM16"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM24"
        AudioFormat.ENCODING_PCM_32BIT -> "PCM32"
        AudioFormat.ENCODING_PCM_FLOAT -> "PCM float"
        AudioFormat.ENCODING_AC3 -> "AC3"
        AudioFormat.ENCODING_E_AC3 -> "E-AC3"
        AudioFormat.ENCODING_DTS -> "DTS"
        AudioFormat.ENCODING_DTS_HD -> "DTS-HD"
        else -> "encoding $encoding"
    }

fun isHiResPcmEncoding(encoding: Int): Boolean =
    when (encoding) {
        AudioFormat.ENCODING_PCM_24BIT_PACKED,
        AudioFormat.ENCODING_PCM_32BIT,
        AudioFormat.ENCODING_PCM_FLOAT -> true
        else -> false
    }
