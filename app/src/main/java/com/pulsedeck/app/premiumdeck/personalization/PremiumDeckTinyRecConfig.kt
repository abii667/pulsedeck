package com.pulsedeck.app.premiumdeck.personalization

object PremiumDeckTinyRecConfig {
    const val ModelName = "PremiumDeck TinyRec v1"
    const val ModelAssetName = "premiumdeck_tinyrec_v1.tflite"
    const val ModelManifestAssetName = "premiumdeck_tinyrec_v1.manifest.json"
    const val UserVectorDim = 64
    const val ItemVectorDim = 64
    const val HistoryLength = 64
    const val CandidateLimitDefault = 1_000
    const val CandidateLimitMax = 2_000
    const val NumCodebooks = 4
    const val CodebookSize = 256
    const val CandidateCodeBytesPerItem = 4
    const val ReplayBufferMaxEvents = 512
    const val ProfileDecayHalfLifeDays = 21
    const val BaseModelAssetMaxMb = 2
    const val BaseMlAssetsPreferredMaxMb = 5
    const val BaseMlAssetsHardCapMb = 10
    const val PeakRamMaxMb = 64
    const val TargetLatencyMsFor1000Candidates = 50

    val eventWeights = mapOf(
        BehaviorEventType.TrackCompleted to 1.00f,
        BehaviorEventType.LikeFavorite to 1.20f,
        BehaviorEventType.TrackAddedToPlaylist to 1.10f,
        BehaviorEventType.RepeatPlay to 0.90f,
        BehaviorEventType.SearchResultClicked to 0.70f,
        BehaviorEventType.AlbumOpened to 0.30f,
        BehaviorEventType.ArtistOpened to 0.30f,
        BehaviorEventType.ArtistFollowed to 1.35f,
        BehaviorEventType.ArtistUnfollowed to -0.65f,
        BehaviorEventType.SkipUnder30Seconds to -0.90f,
        BehaviorEventType.Skip30To60Seconds to -0.50f,
        BehaviorEventType.DislikeHide to -1.20f,
        BehaviorEventType.TrackRemovedFromPlaylist to -0.70f,
    )
}

enum class TinyRecModelHealth {
    MODEL_READY,
    MODEL_UNAVAILABLE,
    USING_HEURISTIC_FALLBACK,
    MODEL_LOAD_FAILED,
}
