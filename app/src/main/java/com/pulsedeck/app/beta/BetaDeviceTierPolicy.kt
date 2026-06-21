package com.pulsedeck.app.beta

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object BetaDeviceTierPolicy {
    fun estimateTesterTier(context: Context): BetaTesterTier {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamGb = memoryInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return when {
            totalRamGb >= 11.5 -> BetaTesterTier.HIGH_END
            totalRamGb >= 5.5 -> BetaTesterTier.SIX_GB_MONITORED
            totalRamGb >= 3.5 -> BetaTesterTier.FOUR_GB_STAGED
            totalRamGb > 0.0 -> BetaTesterTier.LOW_END
            else -> BetaTesterTier.UNKNOWN
        }
    }

    fun deviceSummary(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamMb = if (memoryInfo.totalMem > 0L) memoryInfo.totalMem / (1024L * 1024L) else 0L
        return "model=${Build.MODEL}; android=${Build.VERSION.RELEASE}; sdk=${Build.VERSION.SDK_INT}; ramMb=$totalRamMb"
    }
}
