package com.pulsedeck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.pulsedeck.app.beta.PulseDeckBetaGate

class MainActivity : ComponentActivity() {
    private var performanceSession: PulseDeckPerformanceSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        PerformanceDiagnostics.installDebugPolicies(this)
        YouTubeNetworkDiagnostics.configure(this)
        super.onCreate(savedInstanceState)
        performanceSession = PerformanceDiagnostics.attach(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            PulseDeckTheme {
                if (BuildConfig.PULSEDECK_BETA_GATE_ENABLED) {
                    PulseDeckBetaGate(
                        onFirstUsefulUi = {
                            performanceSession?.markFirstUsefulUi()
                        },
                    ) {
                        PulseDeckApp(onFirstUsefulUi = {})
                    }
                } else {
                    PulseDeckApp(
                        onFirstUsefulUi = {
                            performanceSession?.markFirstUsefulUi()
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        performanceSession?.close()
        performanceSession = null
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AlbumArtworkRuntime.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            CacheBudgetManager.scheduleTrimCleanup(this, CacheCleanupReason.TrimMemory)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AlbumArtworkRuntime.onLowMemory()
        CacheBudgetManager.scheduleTrimCleanup(this, CacheCleanupReason.LowStorage)
    }
}
