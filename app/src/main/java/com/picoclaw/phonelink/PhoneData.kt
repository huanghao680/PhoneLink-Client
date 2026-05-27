package com.picoclaw.phonelink

/**
 * 手机数据模型
 * 包含：当前Activity、电量、定位、时间等信息
 */
data class PhoneData(
    // 时间信息
    val timestamp: Long = System.currentTimeMillis(),
    val timestampReadable: String = "",
    val timezone: String = "",

    // 当前Activity/应用
    val foregroundPackage: String = "unknown",
    val foregroundActivity: String = "unknown",
    val foregroundLabel: String = "unknown",

    // 电量信息
    val batteryLevel: Int = -1,
    val batteryStatus: String = "unknown",      // charging/discharging/full/not-charging
    val batteryHealth: String = "unknown",       // good/overheat/dead/over-voltage
    val batteryTemperature: Float = 0f,          // 摄氏度
    val batteryVoltage: Int = 0,                 // 毫伏
    val batteryTechnology: String = "unknown",
    val isCharging: Boolean = false,
    val chargeSource: String = "none",           // ac/usb/wireless/none

    // 定位信息
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val locationProvider: String = "unknown",
    val locationAddress: String = "",            // 反向地理编码（可选）

    // 设备信息
    val deviceModel: String = android.os.Build.MODEL,
    val deviceBrand: String = android.os.Build.BRAND,
    val androidVersion: String = android.os.Build.VERSION.RELEASE,
    val sdkInt: Int = android.os.Build.VERSION.SDK_INT,
    val screenResolution: String = "",

    // 网络信息（可选）
    val isWifiConnected: Boolean = false,
    val mobileNetworkType: String = "unknown",

    // 状态
    val collectSuccess: Boolean = true,
    val errorMessage: String = ""
)
