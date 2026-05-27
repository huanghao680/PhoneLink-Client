package com.picoclaw.phonelink

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * 获取电池信息
 * 无需额外权限
 */
object BatteryCollector {

    data class BatteryInfo(
        val level: Int,
        val scale: Int,
        val percentage: Int,
        val status: String,
        val health: String,
        val temperature: Float,
        val voltage: Int,
        val technology: String,
        val isCharging: Boolean,
        val chargeSource: String
    )

    fun getBatteryInfo(context: Context): BatteryInfo {
        val batteryStatusFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, batteryStatusFilter)

        if (batteryStatus == null) {
            return BatteryInfo(
                level = -1, scale = 100, percentage = -1,
                status = "unknown", health = "unknown",
                temperature = 0f, voltage = 0,
                technology = "unknown", isCharging = false,
                chargeSource = "none"
            )
        }

        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val statusInt = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val status = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not-charging"
            else -> "unknown"
        }

        val healthInt = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over-voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            else -> "unknown"
        }

        val temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "unknown"

        val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING

        val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val chargeSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "none"
        }

        return BatteryInfo(
            level = level, scale = scale, percentage = percentage,
            status = status, health = health,
            temperature = temperature, voltage = voltage,
            technology = technology, isCharging = isCharging,
            chargeSource = chargeSource
        )
    }
}
