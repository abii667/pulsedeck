package com.pulsedeck.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.pulsedeck.app.beta.BetaFirebaseInitializer

class PulseDeckApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.PULSEDECK_BETA_GATE_ENABLED) {
            BetaFirebaseInitializer.initialize(this)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AlbumArtworkRuntime.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            CacheBudgetManager.scheduleTrimCleanup(this, CacheCleanupReason.TrimMemory)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AlbumArtworkRuntime.onLowMemory()
        CacheBudgetManager.scheduleTrimCleanup(this, CacheCleanupReason.LowStorage)
    }
}
