package com.pulsedeck.app

internal enum class ProAudioInfoSectionId(val label: String) {
    Source("Source"),
    Decoder("Decoder"),
    Resampler("Resampler"),
    Dsp("DSP"),
    Headroom("Headroom"),
    Output("Output"),
    Device("Device"),
    Eligibility("Bit-perfect / Hi-res eligibility"),
    Warnings("Warnings"),
    DebugDetails("Debug details"),
}

internal fun proAudioInfoSectionOrder(): List<ProAudioInfoSectionId> =
    listOf(
        ProAudioInfoSectionId.Source,
        ProAudioInfoSectionId.Decoder,
        ProAudioInfoSectionId.Resampler,
        ProAudioInfoSectionId.Dsp,
        ProAudioInfoSectionId.Headroom,
        ProAudioInfoSectionId.Output,
        ProAudioInfoSectionId.Device,
        ProAudioInfoSectionId.Eligibility,
        ProAudioInfoSectionId.Warnings,
        ProAudioInfoSectionId.DebugDetails,
    )

internal fun proAudioInfoConservativeClaims(snapshot: AudioChainSnapshot): List<String> =
    buildList {
        if (snapshot.resamplerStrategy.resamplerStatus == ResamplerStatus.PulseDeckPlanned) {
            add("PulseDeck HQ resampler is planned only, not active.")
        }
        if (
            snapshot.outputCapability.hiResRequested &&
            snapshot.outputCapability.hiResOutputStatus !in setOf(
                HiResOutputStatus.HighSampleRateActive,
                HiResOutputStatus.HighBitDepthActive,
                HiResOutputStatus.FullHiResActive,
            )
        ) {
            add("Hi-res output is requested only, not verified.")
        }
        if (snapshot.outputCapability.hiResOutputStatus == HiResOutputStatus.HighSampleRateActive) {
            add("High sample-rate output is active, but full high-bit-depth hi-res is not verified.")
        }
        if (snapshot.outputCapability.hiResOutputStatus == HiResOutputStatus.HighBitDepthActive) {
            add("High bit-depth output is active, but high sample-rate output is not verified.")
        }
        if (snapshot.outputCapability.usbDacRequested && !snapshot.outputCapability.usbDacActive) {
            add("USB DAC route is requested only, not verified.")
        }
        if (snapshot.proOutput.enabledByUser && !snapshot.proOutput.active) {
            add("Pro output engine is ${snapshot.proOutput.currentStatus.name}, not verified active.")
        }
        if (!snapshot.bitPerfect.active) {
            add("Bit-perfect output is not active.")
        }
        if (snapshot.outputCapability.supportedSampleRatesHz.isEmpty()) {
            add("Hardware sample-rate capabilities are not reported.")
        }
    }
