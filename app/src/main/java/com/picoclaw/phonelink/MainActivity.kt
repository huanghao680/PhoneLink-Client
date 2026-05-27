package com.picoclaw.phonelink

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.LocationManager
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvPermStatus: TextView
    private lateinit var etServerUrl: EditText
    private lateinit var tvConnectionStatus: TextView
    private lateinit var btnCollect: Button
    private lateinit var btnSendWs: Button
    private lateinit var swAutoCollect: Switch
    private lateinit var tvDataDisplay: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var autoCollectRunnable: Runnable? = null
    private var isAutoCollecting = false
    private var lastCollectedData: JSONObject? = null

    companion object {
        private const val TAG = "PhoneLink"
        private const val PREFS_NAME = "PhoneLinkPrefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_AUTO_COLLECT = "auto_collect"
        private const val REQUEST_PERMISSIONS = 100
        private const val AUTO_COLLECT_INTERVAL = 5 * 60 * 1000L
        private const val DEFAULT_SERVER_URL = "http://frp-rib.com:63467/api/phone-data"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvPermStatus = findViewById(R.id.tvPermStatus)
        etServerUrl = findViewById(R.id.etServerUrl)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        btnCollect = findViewById(R.id.btnCollect)
        btnSendWs = findViewById(R.id.btnSendWs)
        swAutoCollect = findViewById(R.id.swAutoCollect)
        tvDataDisplay = findViewById(R.id.tvDataDisplay)

        tvStatus.text = "🦞 PicoClaw PhoneLink v2.0"

        loadSettings()
        requestAllPermissions()

        // 手动采集按钮（采集 + 发送）
        btnCollect.setOnClickListener {
            btnCollect.isEnabled = false
            btnCollect.text = "采集中..."
            thread {
                val data = collectAllData()
                lastCollectedData = data
                runOnUiThread {
                    tvDataDisplay.text = formatDataForDisplay(data)
                }
                sendDataToServer(data)
                runOnUiThread {
                    btnCollect.isEnabled = true
                    btnCollect.text = "立即采集"
                }
            }
        }

        // "发送"按钮改为"重新发送上次数据"
        btnSendWs.text = "重新发送"
        btnSendWs.setOnClickListener {
            val data = lastCollectedData
            if (data != null) {
                sendDataToServer(data)
            } else {
                Toast.makeText(this, "请先采集数据", Toast.LENGTH_SHORT).show()
            }
        }

        swAutoCollect.setOnCheckedChangeListener { _, isChecked ->
            isAutoCollecting = isChecked
            saveSettings()
            if (isChecked) startAutoCollect() else stopAutoCollect()
        }

        if (swAutoCollect.isChecked) startAutoCollect()
        handler.postDelayed({ updatePermStatus() }, 500)
    }

    // ============================================================
    // 权限管理
    // ============================================================

    private fun requestAllPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.POST_NOTIFICATIONS
        )
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermStatus()
    }

    private fun updatePermStatus() {
        val hasLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasPhone = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasNotif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        tvPermStatus.text = "权限: ${if (hasLoc) "✅" else "❌"}定位  ${if (hasPhone) "✅" else "❌"}手机  ${if (hasNotif) "✅" else "⚠️"}通知"
    }

    // ============================================================
    // 数据发送 — 纯 HTTP POST
    // ============================================================

    private fun sendDataToServer(data: JSONObject) {
        val serverUrl = etServerUrl.text.toString().trim()
        if (serverUrl.isEmpty()) {
            runOnUiThread { Toast.makeText(this, "❌ 请输入服务器地址", Toast.LENGTH_SHORT).show() }
            return
        }

        thread {
            runOnUiThread {
                tvConnectionStatus.text = "🔄 正在发送..."
                tvConnectionStatus.setTextColor(Color.parseColor("#FF9800"))
            }

            try {
                val url = URL(serverUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("User-Agent", "PicoClaw-PhoneLink/2.0")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val body = data.toString().toByteArray(Charsets.UTF_8)
                conn.outputStream.write(body)
                conn.outputStream.flush()

                val code = conn.responseCode
                val resp = if (code in 200..299) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                }
                conn.disconnect()

                if (code in 200..299) {
                    val ts = data.optString("timestamp", "")
                    runOnUiThread {
                        tvConnectionStatus.text = "✅ 发送成功 [$ts]"
                        tvConnectionStatus.setTextColor(Color.parseColor("#4CAF50"))
                    }
                    Log.d(TAG, "POST OK: $resp")
                } else {
                    runOnUiThread {
                        tvConnectionStatus.text = "❌ HTTP $code: $resp"
                        tvConnectionStatus.setTextColor(Color.parseColor("#F44336"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "POST error", e)
                runOnUiThread {
                    tvConnectionStatus.text = "❌ 连接失败: ${e.message}\n💡 确保服务器运行中 & 隧道类型为TCP"
                    tvConnectionStatus.setTextColor(Color.parseColor("#F44336"))
                }
            }
        }
    }

    // ============================================================
    // 数据采集
    // ============================================================

    private fun collectAllData(): JSONObject {
        val data = JSONObject()
        try {
            data.put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).format(Date()))
            data.put("timezone", TimeZone.getDefault().id)
            data.put("appId", packageName)
            data.put("foreground", getForegroundApp())
            data.put("device", getDeviceInfo())
            data.put("battery", getBatteryInfo())
            data.put("location", getLocation())
            data.put("network", getNetworkInfo())
        } catch (e: Exception) {
            data.put("error", e.message)
        }
        return data
    }

    private fun getForegroundApp(): JSONObject {
        val info = JSONObject()
        try {
            info.put("appName", "PhoneLink")
            info.put("packageName", packageName)
            info.put("activity", "MainActivity")
        } catch (e: Exception) { info.put("error", e.message) }
        return info
    }

    private fun getDeviceInfo(): JSONObject {
        val info = JSONObject()
        try {
            info.put("model", Build.MODEL)
            info.put("brand", Build.BRAND)
            info.put("device", Build.DEVICE)
            info.put("sdkVersion", Build.VERSION.SDK_INT)
            info.put("release", Build.VERSION.RELEASE)
            info.put("resolution", "${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}")
        } catch (e: Exception) { info.put("error", e.message) }
        return info
    }

    private fun getBatteryInfo(): JSONObject {
        val info = JSONObject()
        try {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, 0)
                val temp = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0)
                info.put("level", (level * 100.0 / scale).toInt())
                info.put("charging", status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL)
                info.put("temperature", temp / 10.0)
            }
        } catch (e: Exception) { info.put("error", e.message) }
        return info
    }

    private fun getLocation(): JSONObject {
        val info = JSONObject()
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val lm = getSystemService(LOCATION_SERVICE) as LocationManager
                val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc != null) {
                    info.put("latitude", loc.latitude)
                    info.put("longitude", loc.longitude)
                    info.put("accuracy", loc.accuracy)
                    try {
                        val addr = Geocoder(this, Locale.CHINA).getFromLocation(loc.latitude, loc.longitude, 1)
                        if (!addr.isNullOrEmpty()) info.put("address", addr[0].getAddressLine(0) ?: "")
                    } catch (_: Exception) {}
                } else {
                    info.put("error", "无定位数据")
                }
            } else {
                info.put("error", "无定位权限")
            }
        } catch (e: Exception) { info.put("error", e.message) }
        return info
    }

    private fun getNetworkInfo(): JSONObject {
        val info = JSONObject()
        try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            if (net != null) {
                val caps = cm.getNetworkCapabilities(net)
                if (caps != null) {
                    when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> info.put("type", "WiFi")
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> info.put("type", "Mobile")
                        else -> info.put("type", "Other")
                    }
                    info.put("wifiConnected", caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                }
            }
        } catch (e: Exception) { info.put("error", e.message) }
        return info
    }

    // ============================================================
    // 自动采集
    // ============================================================

    private fun startAutoCollect() {
        stopAutoCollect()
        autoCollectRunnable = object : Runnable {
            override fun run() {
                if (!isAutoCollecting) return
                thread {
                    val data = collectAllData()
                    lastCollectedData = data
                    runOnUiThread { tvDataDisplay.text = formatDataForDisplay(data) }
                    sendDataToServer(data)
                }
                handler.postDelayed(this, AUTO_COLLECT_INTERVAL)
            }
        }
        handler.postDelayed(autoCollectRunnable!!, 1000)
        Toast.makeText(this, "🔄 自动采集已开启 (每5分钟)", Toast.LENGTH_SHORT).show()
    }

    private fun stopAutoCollect() {
        autoCollectRunnable?.let { handler.removeCallbacks(it) }
        autoCollectRunnable = null
    }

    // ============================================================
    // 显示格式化
    // ============================================================

    private fun formatDataForDisplay(data: JSONObject): String {
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("📱 手机数据报告")
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine()
        sb.appendLine("⏰ ${data.optString("timestamp", "N/A")}")
        sb.appendLine()

        data.optJSONObject("foreground")?.let { fg ->
            sb.appendLine("📱 前台: ${fg.optString("appName", "?")} (${fg.optString("packageName", "?")})")
        }
        data.optJSONObject("battery")?.let { b ->
            sb.appendLine("🔋 电量: ${b.optInt("level", 0)}% (${if (b.optBoolean("charging")) "充电" else "放电"})")
            sb.appendLine("   温度: ${b.optDouble("temperature", 0.0)}°C")
        }
        data.optJSONObject("location")?.let { loc ->
            val lat = loc.optDouble("latitude", 0.0)
            val lon = loc.optDouble("longitude", 0.0)
            if (lat != 0.0 || lon != 0.0) {
                sb.appendLine("📍 位置: $lat, $lon (±${loc.optDouble("accuracy", 0.0)}m)")
            }
            loc.optString("address", "").takeIf { it.isNotEmpty() }?.let { sb.appendLine("   $it") }
        }
        data.optJSONObject("device")?.let { dev ->
            sb.appendLine("📱 设备: ${dev.optString("brand", "")} ${dev.optString("model", "")}")
            sb.appendLine("   Android ${dev.optString("release", "")} (API ${dev.optInt("sdkVersion", 0)})")
        }
        data.optJSONObject("network")?.let { net ->
            sb.appendLine("📶 网络: ${net.optString("type", "?")} / WiFi: ${if (net.optBoolean("wifiConnected")) "是" else "否"}")
        }
        sb.appendLine("═══════════════════════════════════")
        return sb.toString()
    }

    // ============================================================
    // 设置持久化
    // ============================================================

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        etServerUrl.setText(prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL))
        swAutoCollect.isChecked = prefs.getBoolean(KEY_AUTO_COLLECT, false)
    }

    private fun saveSettings() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_SERVER_URL, etServerUrl.text.toString())
            .putBoolean(KEY_AUTO_COLLECT, swAutoCollect.isChecked)
            .apply()
    }

    override fun onPause() { super.onPause(); saveSettings() }
    override fun onDestroy() { stopAutoCollect(); super.onDestroy() }
}
