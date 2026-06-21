package com.pulsedeck.app

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics

private const val PERFORMANCE_TAG = "PulseDeckPerf"

internal interface PulseDeckPerformanceSession {
    fun markFirstUsefulUi()
    fun close()
}

internal object PerformanceDiagnostics {
    fun installDebugPolicies(context: Context) {
        if (!context.isDebuggable()) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
    }

    fun attach(activity: Activity): PulseDeckPerformanceSession {
        val createdAtMillis = SystemClock.uptimeMillis()
        return if (activity.isDebuggable()) {
            FrameMetricsPerformanceSession(activity, createdAtMillis)
        } else {
            ReleasePerformanceSession(activity)
        }
    }
}

private open class ReleasePerformanceSession(
    private val activity: Activity,
) : PulseDeckPerformanceSession {
    private var firstUsefulUiMarked = false

    open override fun markFirstUsefulUi() {
        if (firstUsefulUiMarked) return
        firstUsefulUiMarked = true
        runCatching { activity.reportFullyDrawn() }
    }

    open override fun close() = Unit
}

private class FrameMetricsPerformanceSession(
    private val activity: Activity,
    private val createdAtMillis: Long,
) : ReleasePerformanceSession(activity) {
    private val handlerThread = HandlerThread("PulseDeckFrameMetrics")
    private var listenerInstalled = false
    private var firstUsefulUiLogged = false
    private var frameCount = 0
    private var slowFrames = 0
    private var verySlowFrames = 0
    private var maxFrameMillis = 0L

    private val listener = android.view.Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
        val totalMillis = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000L
        frameCount += 1
        if (totalMillis > 16L) slowFrames += 1
        if (totalMillis > 32L) verySlowFrames += 1
        if (totalMillis > maxFrameMillis) maxFrameMillis = totalMillis
        if (frameCount % 120 == 0) {
            Log.d(
                PERFORMANCE_TAG,
                "frames=$frameCount slow=$slowFrames very_slow=$verySlowFrames max_ms=$maxFrameMillis",
            )
        }
    }

    override fun markFirstUsefulUi() {
        if (!firstUsefulUiLogged) {
            firstUsefulUiLogged = true
            val firstDrawMillis = SystemClock.uptimeMillis() - createdAtMillis
            Log.d(PERFORMANCE_TAG, "first_draw_ms=$firstDrawMillis activity=${activity.javaClass.simpleName}")
        }
        super.markFirstUsefulUi()
    }

    init {
        handlerThread.start()
        runCatching {
            activity.window.addOnFrameMetricsAvailableListener(listener, Handler(handlerThread.looper))
            listenerInstalled = true
        }.onFailure {
            Log.d(PERFORMANCE_TAG, "frame_metrics_unavailable=${it.message.orEmpty()}")
            handlerThread.quitSafely()
        }
    }

    override fun close() {
        if (listenerInstalled) {
            runCatching { activity.window.removeOnFrameMetricsAvailableListener(listener) }
        }
        if (frameCount > 0) {
            Log.d(
                PERFORMANCE_TAG,
                "frame_metrics_final frames=$frameCount slow=$slowFrames very_slow=$verySlowFrames max_ms=$maxFrameMillis",
            )
        }
        handlerThread.quitSafely()
    }
}

private fun Context.isDebuggable(): Boolean =
    (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
