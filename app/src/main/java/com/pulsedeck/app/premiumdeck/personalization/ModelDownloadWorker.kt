package com.pulsedeck.app.premiumdeck.personalization

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val manager = ModelAssetManager(applicationContext)
        val manifest = manager.preferredModelManifest()
        return when {
            manager.downloadedModelExists() -> Result.success(
                workDataOf(
                    "status" to modelStatus("Downloaded", manifest),
                    "model_asset" to PremiumDeckTinyRecConfig.ModelAssetName,
                    "model_bootstrap_sample" to (manifest?.bootstrapSample ?: false),
                    "model_production_ready" to (manifest?.productionReady ?: false),
                ),
            )
            manager.bundledAssetExists() -> Result.success(
                workDataOf(
                    "status" to modelStatus("Bundled", manifest),
                    "model_asset" to PremiumDeckTinyRecConfig.ModelAssetName,
                    "model_bootstrap_sample" to (manifest?.bootstrapSample ?: false),
                    "model_production_ready" to (manifest?.productionReady ?: false),
                ),
            )
            else -> Result.success(
                workDataOf(
                    "status" to "No ${PremiumDeckTinyRecConfig.ModelAssetName} model configured; heuristic fallback remains active.",
                    "model_asset" to PremiumDeckTinyRecConfig.ModelAssetName,
                    "model_bootstrap_sample" to false,
                    "model_production_ready" to false,
                ),
            )
        }
    }

    private fun modelStatus(prefix: String, manifest: TinyRecModelManifest?): String =
        if (manifest == null) {
            "$prefix ${PremiumDeckTinyRecConfig.ModelAssetName} is available; manifest metadata is missing."
        } else {
            "$prefix ${manifest.assetName} is available as a ${manifest.readinessLabel}."
        }
}
