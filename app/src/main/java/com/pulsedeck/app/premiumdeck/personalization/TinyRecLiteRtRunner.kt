package com.pulsedeck.app.premiumdeck.personalization

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.gpu.support.TfLiteGpu
import com.google.android.gms.tflite.java.TfLite
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import org.tensorflow.lite.gpu.GpuDelegateFactory

interface TinyRecScorer {
    val health: TinyRecModelHealth
    val productionReady: Boolean

    suspend fun initialize(): TinyRecModelHealth

    suspend fun score(
        userVector: List<Float>,
        candidates: List<TrackCandidate>,
    ): List<Float>?
}

class TinyRecLiteRtRunner(
    private val context: Context,
    private val modelAssetManager: ModelAssetManager = ModelAssetManager(context),
) : TinyRecScorer {
    @Volatile override var health: TinyRecModelHealth = TinyRecModelHealth.MODEL_UNAVAILABLE
        private set

    @Volatile private var interpreter: InterpreterApi? = null

    override val productionReady: Boolean
        get() = modelAssetManager.preferredModelManifest()?.productionReady == true

    override suspend fun initialize(): TinyRecModelHealth = withContext(Dispatchers.IO) {
        val location = modelAssetManager.preferredModelLocation()
        if (location == null) {
            health = TinyRecModelHealth.USING_HEURISTIC_FALLBACK
            return@withContext health
        }
        val manifest = modelAssetManager.preferredModelManifest()
        if (manifest?.productionReady != true) {
            interpreter?.close()
            interpreter = null
            health = TinyRecModelHealth.USING_HEURISTIC_FALLBACK
            return@withContext health
        }
        runCatching {
            val gpuAvailable = runCatching { Tasks.await(TfLiteGpu.isGpuDelegateAvailable(context)) }.getOrDefault(false)
            val initOptions = TfLiteInitializationOptions.builder()
                .setEnableGpuDelegateSupport(gpuAvailable)
                .build()
            Tasks.await(TfLite.initialize(context, initOptions))
            val options = InterpreterApi.Options()
                .setRuntime(TfLiteRuntime.PREFER_SYSTEM_OVER_APPLICATION)
            if (gpuAvailable) options.addDelegateFactory(GpuDelegateFactory())
            interpreter = InterpreterApi.create(loadModel(location), options)
            health = TinyRecModelHealth.MODEL_READY
        }.getOrElse {
            interpreter = null
            health = TinyRecModelHealth.MODEL_LOAD_FAILED
        }
        health
    }

    override suspend fun score(
        userVector: List<Float>,
        candidates: List<TrackCandidate>,
    ): List<Float>? = withContext(Dispatchers.Default) {
        val active = interpreter ?: return@withContext null
        if (health != TinyRecModelHealth.MODEL_READY) return@withContext null
        if (!productionReady) return@withContext null
        runCatching {
            candidates.map { candidate ->
                val inputs = arrayOf(
                    arrayOf(
                        candidate.codebookIds.take(PremiumDeckTinyRecConfig.NumCodebooks)
                            .let { ids -> (ids + List(PremiumDeckTinyRecConfig.NumCodebooks - ids.size) { 0 }).toIntArray() },
                    ),
                    arrayOf(userVector.normalizedVector(PremiumDeckTinyRecConfig.UserVectorDim).toFloatArray()),
                )
                val output = Array(1) { FloatArray(1) }
                active.runForMultipleInputsOutputs(inputs, mapOf(0 to output))
                output[0][0]
            }
        }.getOrNull()
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        health = TinyRecModelHealth.MODEL_UNAVAILABLE
    }

    private fun loadModel(location: ModelLocation): MappedByteBuffer =
        when (location) {
            is ModelLocation.Asset -> context.assets.openFd(location.name).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
                }
            }
            is ModelLocation.Downloaded -> FileInputStream(location.file).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, 0, location.file.length())
            }
        }
}
