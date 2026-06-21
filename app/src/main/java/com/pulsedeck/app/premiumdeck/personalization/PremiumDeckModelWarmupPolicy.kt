package com.pulsedeck.app.premiumdeck.personalization

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

internal fun shouldWarmPremiumDeckModel(context: Context): Boolean =
    runCatching {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return@runCatching true
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 100
        plugged || percent >= 20
    }.getOrDefault(true)
