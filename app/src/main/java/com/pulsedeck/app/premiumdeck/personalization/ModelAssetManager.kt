package com.pulsedeck.app.premiumdeck.personalization

import android.content.Context
import java.io.File
import org.json.JSONObject

class ModelAssetManager(
    private val context: Context,
) {
    fun bundledAssetExists(): Boolean =
        runCatching {
            context.assets.open(PremiumDeckTinyRecConfig.ModelAssetName).use { true }
        }.getOrDefault(false)

    fun downloadedModelFile(): File =
        File(context.filesDir, "premiumdeck/models/${PremiumDeckTinyRecConfig.ModelAssetName}")

    fun downloadedManifestFile(): File =
        File(context.filesDir, "premiumdeck/models/${PremiumDeckTinyRecConfig.ModelManifestAssetName}")

    fun downloadedModelExists(): Boolean =
        downloadedModelFile().let { it.exists() && it.length() > 0L }

    fun bundledManifest(): TinyRecModelManifest? =
        readBundledManifest(PremiumDeckTinyRecConfig.ModelManifestAssetName)

    fun downloadedManifest(): TinyRecModelManifest? =
        downloadedManifestFile()
            .takeIf { it.exists() && it.length() > 0L }
            ?.let { file -> runCatching { TinyRecModelManifest.fromJson(file.readText()) }.getOrNull() }

    fun preferredModelManifest(): TinyRecModelManifest? =
        when {
            downloadedModelExists() -> downloadedManifest()
            bundledAssetExists() -> bundledManifest()
            else -> null
        }

    fun preferredModelLocation(): ModelLocation? =
        when {
            downloadedModelExists() -> ModelLocation.Downloaded(downloadedModelFile())
            bundledAssetExists() -> ModelLocation.Asset(PremiumDeckTinyRecConfig.ModelAssetName)
            else -> null
        }

    fun baseMlAssetSizeBytes(): Long =
        (if (bundledAssetExists()) bundledAssetSizeBytes() else 0L) +
            (downloadedModelFile().takeIf { it.exists() }?.length() ?: 0L)

    private fun bundledAssetSizeBytes(): Long =
        runCatching {
            context.assets.openFd(PremiumDeckTinyRecConfig.ModelAssetName).use { it.length }
        }.getOrDefault(0L)

    private fun readBundledManifest(assetName: String): TinyRecModelManifest? =
        runCatching {
            context.assets.open(assetName).bufferedReader().use { reader ->
                TinyRecModelManifest.fromJson(reader.readText())
            }
        }.getOrNull()
}

sealed class ModelLocation {
    data class Asset(val name: String) : ModelLocation()
    data class Downloaded(val file: File) : ModelLocation()
}

data class TinyRecModelManifest(
    val modelName: String,
    val assetName: String,
    val createdAtMillis: Long,
    val bootstrapSample: Boolean,
    val candidateCount: Int,
    val eventCount: Int,
    val trainingExampleCount: Int,
    val sizeBytes: Long,
    val warning: String,
) {
    val productionReady: Boolean
        get() = !bootstrapSample && warning.isBlank()

    val readinessLabel: String
        get() = if (productionReady) "production candidate" else "bootstrap validation model"

    companion object {
        fun fromJson(rawJson: String): TinyRecModelManifest {
            val root = JSONObject(rawJson)
            return TinyRecModelManifest(
                modelName = root.optString("model_name", PremiumDeckTinyRecConfig.ModelName),
                assetName = root.optString("asset_name", PremiumDeckTinyRecConfig.ModelAssetName),
                createdAtMillis = root.optLong("created_at_ms", 0L),
                bootstrapSample = root.optBoolean("bootstrap_sample", false),
                candidateCount = root.optInt("candidate_count", 0),
                eventCount = root.optInt("event_count", 0),
                trainingExampleCount = root.optInt("training_example_count", 0),
                sizeBytes = root.optLong("size_bytes", 0L),
                warning = root.optString("warning", ""),
            )
        }
    }
}

data class TinyRecModelStatus(
    val title: String,
    val body: String,
)

fun describeTinyRecModelStatus(
    health: TinyRecModelHealth,
    manifest: TinyRecModelManifest?,
    modelAvailable: Boolean,
): TinyRecModelStatus {
    val title = when (health) {
        TinyRecModelHealth.MODEL_READY -> "PremiumDeck Model Ready"
        TinyRecModelHealth.MODEL_LOAD_FAILED -> "PremiumDeck Model Load Failed"
        TinyRecModelHealth.USING_HEURISTIC_FALLBACK -> "PremiumDeck Heuristic Fallback"
        TinyRecModelHealth.MODEL_UNAVAILABLE -> if (modelAvailable) "PremiumDeck Model Available" else "PremiumDeck Model Unavailable"
    }
    val modelLabel = when {
        manifest != null -> "${manifest.assetName} is available as a ${manifest.readinessLabel}"
        modelAvailable -> "${PremiumDeckTinyRecConfig.ModelAssetName} is available, but manifest metadata is missing"
        else -> "No ${PremiumDeckTinyRecConfig.ModelAssetName} model is configured"
    }
    val runtimeLabel = when (health) {
        TinyRecModelHealth.MODEL_READY -> "TinyRec runtime is initialized"
        TinyRecModelHealth.MODEL_LOAD_FAILED -> "TinyRec runtime could not load the model"
        TinyRecModelHealth.USING_HEURISTIC_FALLBACK -> "PremiumDeck is using heuristic fallback"
        TinyRecModelHealth.MODEL_UNAVAILABLE -> "TinyRec runtime has not been initialized"
    }
    val readinessLabel = when {
        !modelAvailable -> "Smart recommendations remain on the fast local fallback."
        manifest?.productionReady == true -> "Smart mode can use local TinyRec reranking after warmup."
        manifest?.bootstrapSample == true -> "This validates the pipeline only; Smart mode stays on heuristic fallback until a production manifest is available."
        manifest == null -> "Keep heuristic fallback available until a verified manifest is shipped with the model."
        else -> "Manifest metadata is present but not production-ready, so treat TinyRec output as experimental."
    }
    return TinyRecModelStatus(
        title = title,
        body = "$modelLabel. $runtimeLabel. $readinessLabel",
    )
}
