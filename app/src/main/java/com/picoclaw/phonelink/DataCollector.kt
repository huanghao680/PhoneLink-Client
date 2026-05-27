package com.picoclaw.phonelink

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.DisplayMetrics
import android.view.WindowManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 数据收集器 - 汇总所有数据
 */
class DataCollector(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)

    suspend fun collectAll(): PhoneData = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()
            val tz = TimeZone.getDefault()

            val activityInfo = try {
                ActivityCollector.getForegroundActivity(context)
            } catch (e: Exception) { null }

            val batteryInfo = try {
                BatteryCollector.getBatteryInfo(context)
            } catch (e: Exception) { null }

            val locationInfo = try {
                LocationCollector.getCurrentLocation(context)
            } catch (e: Exception) { null }

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val screenRes = "${metrics.widthPixels}x${metrics.heightPixels}"

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val mobileType = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
                caps.getLinkDownstreamBandwidthKbps().toString() + "kbps"
            } else "none"

            PhoneData(
                timestamp = now,
                timestampReadable = dateFormat.format(Date(now)),
                timezone = tz.id,
                foregroundPackage = activityInfo?.packageName ?: "unknown",
                foregroundActivity = activityInfo?.activityName ?: "unknown",
                foregroundLabel = activityInfo?.label ?: "unknown",
                batteryLevel = batteryInfo?.percentage ?: -1,
                batteryStatus = batteryInfo?.status ?: "unknown",
                batteryHealth = batteryInfo?.health ?: "unknown",
                batteryTemperature = batteryInfo?.temperature ?: 0f,
                batteryVoltage = batteryInfo?.voltage ?: 0,
                batteryTechnology = batteryInfo?.technology ?: "unknown",
                isCharging = batteryInfo?.isCharging ?: false,
                chargeSource = batteryInfo?.chargeSource ?: "none",
                latitude = locationInfo?.latitude ?: 0.0,
                longitude = locationInfo?.longitude ?: 0.0,
                altitude = locationInfo?.altitude ?: 0.0,
                accuracy = locationInfo?.accuracy ?: 0f,
                locationProvider = locationInfo?.provider ?: "unavailable",
                locationAddress = locationInfo?.address ?: "",
                screenResolution = screenRes,
                isWifiConnected = isWifi,
                mobileNetworkType = mobileType,
                collectSuccess = true,
                errorMessage = ""
            )
        } catch (e: Exception) {
            PhoneData(
                timestamp = System.currentTimeMillis(),
                timestampReadable = dateFormat.format(Date()),
                collectSuccess = false,
                errorMessage = e.message ?: "unknown error"
            )
        }
    }
}

/**
 * WebSocket 数据发送器
 * 保持长连接，自动重连，发送数据后等待确认
 */
class WsDataSender {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // WebSocket 不需要读超时
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val lastError = AtomicReference<String>("")
    private var currentUrl = ""

    // 连接状态回调
    var onStatusChanged: ((connected: Boolean, message: String) -> Unit)? = null

    // 发送结果等待
    private var pendingResult: CompletableDeferred<SendResult>? = null

    data class SendResult(
        val success: Boolean,
        val message: String,
        val code: Int = 0
    )

    /**
     * 连接到 WebSocket 服务器
     * 自动将 http:// 转换为 ws://，https:// 转换为 wss://
     */
    fun connect(serverUrl: String) {
        // 从 HTTP URL 推导 WebSocket URL
        val wsUrl = serverUrl
            .replace(Regex("^https://"), "wss://")
            .replace(Regex("^http://"), "ws://")
            // 如果是纯 IP:port 格式，加上 ws:// 前缀
            .let { if (!it.startsWith("ws://") && !it.startsWith("wss://")) "ws://$it" else it }
            // 去掉路径，只保留 host:port
            .let { url ->
                val protocolEnd = url.indexOf("://")
                if (protocolEnd >= 0) {
                    val rest = url.substring(protocolEnd + 3)
                    val pathStart = rest.indexOf("/")
                    if (pathStart >= 0) url.substring(0, protocolEnd + 3 + pathStart)
                    else url
                } else url
            }

        if (wsUrl == currentUrl && isConnected.get()) {
            return  // 已连接
        }

        disconnect()
        currentUrl = wsUrl

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                lastError.set("")
                onStatusChanged?.invoke(true, "已连接")
                println("🔌 WebSocket 已连接: $wsUrl")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                println("📨 服务器回复: $text")
                try {
                    val json = gson.fromJson(text, Map::class.java)
                    val ok = json["ok"] as? Boolean ?: false
                    val received = json["received"] as? String ?: ""
                    val error = json["error"] as? String ?: ""

                    pendingResult?.complete(
                        if (ok) {
                            SendResult(true, "✅ 发送成功 ($received)")
                        } else {
                            SendResult(false, "❌ 服务器错误: $error")
                        }
                    )
                } catch (e: Exception) {
                    pendingResult?.complete(
                        SendResult(false, "❌ 解析回复失败: ${e.message}")
                    )
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                onDisconnected("连接关闭: $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onDisconnected("已断开: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onDisconnected("连接失败: ${t.message}")
            }

            private fun onDisconnected(message: String) {
                isConnected.set(false)
                lastError.set(message)
                onStatusChanged?.invoke(false, message)
                println("🔌 WebSocket 断开: $message")
            }
        })
    }

    /**
     * 发送数据（等待服务器确认，最多等 10 秒）
     */
    suspend fun send(data: PhoneData): SendResult = withContext(Dispatchers.IO) {
        if (!isConnected.get()) {
            return@withContext SendResult(false, "❌ 未连接服务器")
        }

        pendingResult = CompletableDeferred()

        val json = gson.toJson(data)
        val sent = webSocket?.send(json) ?: false

        if (!sent) {
            return@withContext SendResult(false, "❌ 发送失败（WebSocket 已断开）")
        }

        // 等待服务器回复，最多 10 秒
        return@withContext try {
            withTimeout(10_000) {
                pendingResult!!.await()
            }
        } catch (e: Exception) {
            SendResult(false, "❌ 等待回复超时")
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        try {
            webSocket?.close(1000, "Bye")
        } catch (e: Exception) { }
        webSocket = null
        isConnected.set(false)
        currentUrl = ""
    }

    fun isConnected() = isConnected.get()
    fun getLastError() = lastError.get()
}

/**
 * HTTP 数据发送器（备用）
 * 当 WebSocket 不可用时使用
 */
class HttpDataSender {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    data class SendResult(
        val success: Boolean,
        val message: String,
        val code: Int = 0
    )

    suspend fun send(url: String, data: PhoneData): SendResult = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(data)
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

            // 自动转换为 HTTP URL
            val httpUrl = url
                .replace(Regex("^wss://"), "https://")
                .replace(Regex("^ws://"), "http://")
                .let { if (!it.startsWith("http://") && !it.startsWith("https://")) "http://$it" else it }

            val request = Request.Builder()
                .url(httpUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            SendResult(
                success = response.isSuccessful,
                message = if (response.isSuccessful) "✅ 发送成功" else "服务器返回 ${response.code}: $responseBody",
                code = response.code
            )
        } catch (e: Exception) {
            SendResult(false, "❌ 发送失败: ${e.message}")
        }
    }
}

// 简单的 CompletableDeferred 实现
class CompletableDeferred<T> {
    @Volatile private var completed = false
    @Volatile private var result: T? = null
    private val lock = Object()

    fun complete(value: T) {
        synchronized(lock) {
            result = value
            completed = true
            lock.notifyAll()
        }
    }

    suspend fun await(): T {
        while (!completed) {
            synchronized(lock) {
                if (!completed) {
                    lock.wait(100)
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
