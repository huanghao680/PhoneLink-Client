package com.picoclaw.phonelink

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private enum class PhoneLinkTheme { MATERIAL3, MIUIX }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoneLinkApp() }
    }
}

@Composable
private fun PhoneLinkApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("PhoneLinkPrefs", 0) }
    var theme by rememberSaveable {
        mutableStateOf(
            if (prefs.getString("ui_theme", "material3") == "miuix")
                PhoneLinkTheme.MIUIX else PhoneLinkTheme.MATERIAL3
        )
    }
    val colors = if (theme == PhoneLinkTheme.MIUIX) {
        darkColorScheme(
            primary = Color(0xFFFF8A00), secondary = Color(0xFF3D9DFF),
            surface = Color(0xFF17181C), background = Color(0xFF101114)
        )
    } else dynamicLightColorScheme(context)

    MaterialTheme(colorScheme = colors, shapes = if (theme == PhoneLinkTheme.MIUIX) {
        androidx.compose.material3.Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp)
        )
    } else androidx.compose.material3.Shapes()) {
        PhoneLinkScreen(theme) {
            theme = it
            prefs.edit().putString("ui_theme", if (it == PhoneLinkTheme.MIUIX) "miuix" else "material3").apply()
        }
    }
}

@Composable
private fun PhoneLinkScreen(theme: PhoneLinkTheme, onThemeChange: (PhoneLinkTheme) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("PhoneLinkPrefs", 0) }
    var url by rememberSaveable { mutableStateOf(prefs.getString("server_url", "") ?: "") }
    var status by rememberSaveable { mutableStateOf("等待采集") }
    var payload by rememberSaveable { mutableStateOf("尚未采集数据") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var automatic by rememberSaveable { mutableStateOf(prefs.getBoolean("auto_collect", false)) }
    val collector = remember { DataCollector(context) }
    val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.POST_NOTIFICATIONS
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { status = "权限状态已更新" }

    fun collectAndSend() {
        if (url.isBlank()) {
            status = "请先填写服务器地址"
            return
        }
        busy = true
        scope.launch {
            val result = runCatching {
                val data = collector.collectAll()
                val json = GsonBuilder().setPrettyPrinting().create().toJson(data)
                val code = postJson(url, json)
                json to code
            }
            result.onSuccess {
                payload = it.first
                status = if (it.second in 200..299) "发送成功（HTTP ${it.second}）" else "服务器返回 HTTP ${it.second}"
            }.onFailure { status = "发送失败：${it.message ?: "未知错误"}" }
            busy = false
        }
    }

    LaunchedEffect(automatic) {
        if (automatic) {
            while (true) {
                collectAndSend()
                delay(5 * 60 * 1000L)
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text("PhoneLink", style = MaterialTheme.typography.titleLarge)
                Text(if (theme == PhoneLinkTheme.MIUIX) "MiuiX 风格" else "Material 3 风格",
                    style = MaterialTheme.typography.labelSmall)
            }
        }, actions = {
            Icon(Icons.Default.Settings, contentDescription = "设置", modifier = Modifier.padding(end = 16.dp))
        })
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("手机状态同步", style = MaterialTheme.typography.headlineSmall)
                    Text("采集设备、网络、电量和位置，并发送到你的 PhoneLink Server。",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("服务器", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = url, onValueChange = { url = it; prefs.edit().putString("server_url", it).apply() },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("HTTP POST 地址") },
                        placeholder = { Text("http://192.168.1.2:8765/api/phone-data") }
                    )
                    Text(status, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = ::collectAndSend, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.padding(3.dp))
                    Text("立即采集")
                }
                Button(onClick = ::collectAndSend, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.padding(3.dp))
                    Text("重新发送")
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("自动采集（每 5 分钟）")
                        Switch(checked = automatic, onCheckedChange = {
                            automatic = it
                            prefs.edit().putBoolean("auto_collect", it).apply()
                        })
                    }
                    Text("权限", style = MaterialTheme.typography.titleSmall)
                    Button(onClick = { permissionLauncher.launch(permissions) }) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.padding(3.dp))
                        Text("授权所需权限")
                    }
                }
            }
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("外观", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        FilterChip(selected = theme == PhoneLinkTheme.MATERIAL3,
                            onClick = { onThemeChange(PhoneLinkTheme.MATERIAL3) }, label = { Text("Material 3") })
                        FilterChip(selected = theme == PhoneLinkTheme.MIUIX,
                            onClick = { onThemeChange(PhoneLinkTheme.MIUIX) }, label = { Text("MiuiX") })
                    }
                }
            }
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("最近采集数据", style = MaterialTheme.typography.titleMedium)
                    Text(payload, modifier = Modifier.padding(top = 8.dp),
                        fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private suspend fun postJson(url: String, payload: String): Int = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    val request = Request.Builder().url(url).post(payload.toRequestBody("application/json".toMediaType())).build()
    client.newCall(request).execute().use { it.code }
}
