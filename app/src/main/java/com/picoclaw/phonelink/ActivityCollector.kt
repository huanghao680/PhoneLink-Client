package com.picoclaw.phonelink

import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*

/**
 * 获取当前前台 Activity/应用信息
 *
 * 需要权限：PACKAGE_USAGE_STATS
 * 用户需要在 设置 > 安全 > 使用情况访问 中手动授权
 */
object ActivityCollector {

    data class ActivityInfo(
        val packageName: String,
        val activityName: String,
        val label: String,
        val lastEventTime: Long
    )

    /**
     * 获取当前前台 Activity
     */
    fun getForegroundActivity(context: Context): ActivityInfo {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return ActivityInfo("unknown", "unknown", "unknown", 0)

        val now = System.currentTimeMillis()
        // 查询最近10秒的使用情况
        val events = usageStatsManager.queryEvents(now - 10_000, now)

        var lastPackage = "unknown"
        var lastActivity = "unknown"
        var lastTime = 0L

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.packageName
                lastActivity = event.className
                lastTime = event.timeStamp
            }
        }

        // 如果没有最近的事件，查最近30秒
        if (lastPackage == "unknown") {
            val events2 = usageStatsManager.queryEvents(now - 30_000, now)
            while (events2.hasNextEvent()) {
                events2.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPackage = event.packageName
                    lastActivity = event.className
                    lastTime = event.timeStamp
                }
            }
        }

        // 尝试获取应用标签
        val label = getAppLabel(context, lastPackage)

        return ActivityInfo(
            packageName = lastPackage,
            activityName = lastActivity.substringAfterLast('.'),
            label = label,
            lastEventTime = lastTime
        )
    }

    /**
     * 获取最近使用的应用列表
     */
    fun getRecentApps(context: Context, count: Int = 10): List<Pair<String, Long>> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            cal.timeInMillis,
            now
        )

        return stats
            ?.filter { it.totalTimeInForeground > 0 }
            ?.sortedByDescending { it.lastTimeUsed }
            ?.take(count)
            ?.map { it.packageName to it.lastTimeUsed }
            ?: emptyList()
    }

    private fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
