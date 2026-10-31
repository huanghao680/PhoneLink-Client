package com.picoclaw.phonelink

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
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

private val MiuiBackground = Color(0xFFF5F5F5)
private val MiuiSurface = Color.White
private val MiuiText = Color(0xFF1F1F1F)
private val MiuiSecondaryText = Color(0xFF8A8A8A)
private val MiuiOrange = Color(0xFFFF6900)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoneLinkApp() }
    }
}

@Composable
@androidx.compose.material3.ExperimentalMaterial3Api
private fun PhoneLinkApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("PhoneLinkPrefs", 0) }
    var theme by rememberSaveable {
        mutableStateOf(
            if (prefs.getString("ui_theme", "material3") == "miuix") {
                PhoneLinkTheme.MIUIX
            } else {
                PhoneLinkTheme.MATERIAL3
            }
        )
    }

    if (theme == PhoneLinkTheme.MIUIX) {
        MaterialTheme(
            colorScheme = lightColorSchemeForMiui(),
            shapes = androidx.compose.material3.Shapes(
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(14.dp),
                large = RoundedCornerShape(18.dp)
            )
        ) {
            PhoneLinkMiuixScreen {
                theme = it
                prefs.edit().putString("ui_theme", "miuix").apply()
            }
        }
    } else {
        MaterialTheme(colorScheme = dynamicLightColorScheme(context)) {
            PhoneLinkMaterialScreen {
                theme = it
                prefs.edit().putString("ui_theme", "material3").apply()
            }
        }
    }
}

private fun lightColorSchemeForMiui() = androidx.compose.material3.lightColorScheme(
    primary = MiuiOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE7D7),
    onPrimaryContainer = Color(0xFF5C2500),
    secondary = Color(0xFF5B8DEF),
    background = MiuiBackground,
    surface = MiuiSurface,
    onSurface = MiuiText,
    onSurfaceVariant = MiuiSecondaryText
)

@Composable
@androidx.compose.material3.ExperimentalMaterial3Api
private fun PhoneLinkMaterialScreen(onThemeChange: (PhoneLinkTheme) -> Unit) {
    PhoneLinkScreenState { state ->
        MaterialTheme {
            Scaffold(topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("PhoneLink", style = MaterialTheme.typography.titleLarge)
                            Text("手机状态同步", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                )
            }) { padding ->
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        Modifier.fillMaxWidth().widthIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        MaterialHeader()
                        MaterialServerSection(state)
                        MaterialActionRow(state)
                        MaterialAutomationSection(state)
                        MaterialAppearanceSection(onThemeChange)
                        MaterialPayloadSection(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialHeader() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("手机状态同步", style = MaterialTheme.typography.headlineSmall)
            Text(
                "采集设备、网络、电量和位置，并发送到你的 PhoneLink Server。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MaterialServerSection(state: ScreenState) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("服务器", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.url,
                onValueChange = state.setUrl,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("HTTP POST 地址") },
                placeholder = { Text("http://192.168.1.2:8765/api/phone-data") }
            )
            Text(state.status, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MaterialActionRow(state: ScreenState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = state.collectAndSend,
            enabled = !state.busy,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("立即采集")
        }
        Button(
            onClick = state.collectAndSend,
            enabled = !state.busy,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("重新发送")
        }
    }
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
}

@Composable
private fun MaterialAutomationSection(state: ScreenState) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("自动采集（每 5 分钟）")
                Switch(checked = state.automatic, onCheckedChange = state.setAutomatic)
            }
            Button(onClick = state.requestPermissions) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("授权所需权限")
            }
        }
    }
}

@Composable
private fun MaterialAppearanceSection(onThemeChange: (PhoneLinkTheme) -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("外观", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(selected = true, onClick = {}, label = { Text("Material 3") })
                FilterChip(
                    selected = false,
                    onClick = { onThemeChange(PhoneLinkTheme.MIUIX) },
                    label = { Text("MiuiX") }
                )
            }
        }
    }
}

@Composable
private fun MaterialPayloadSection(state: ScreenState) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("最近采集数据", style = MaterialTheme.typography.titleMedium)
            Text(
                state.payload,
                modifier = Modifier.padding(top = 8.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PhoneLinkMiuixScreen(onThemeChange: (PhoneLinkTheme) -> Unit) {
    PhoneLinkScreenState { state ->
        Surface(color = MiuiBackground) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 600.dp).verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(
                        "PhoneLink",
                        color = MiuiText,
                        style = MaterialTheme.typography.displaySmall
                    )
                    Text(
                        "设备连接",
                        color = MiuiSecondaryText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    MiuixGroup(title = "服务器") {
                        MiuixInputRow("地址", state.url, state.setUrl)
                        MiuixDivider()
                        MiuixStatusRow("连接状态", state.status)
                    }
                    MiuixGroup(title = "数据同步") {
                        MiuixActionRow(
                            "立即采集",
                            "采集当前设备状态并发送",
                            state.collectAndSend,
                            state.busy
                        )
                        MiuixDivider()
                        MiuixActionRow(
                            "重新发送",
                            "发送最近一次采集的数据",
                            state.collectAndSend,
                            state.busy
                        )
                        MiuixDivider()
                        MiuixSwitchRow(
                            "自动采集",
                            "每 5 分钟同步一次",
                            state.automatic,
                            state.setAutomatic
                        )
                    }
                    MiuixGroup(title = "权限与外观") {
                        MiuixActionRow(
                            "授权所需权限",
                            "定位、通知和设备信息",
                            state.requestPermissions,
                            false
                        )
                        MiuixDivider()
                        MiuixThemeRow(onThemeChange)
                    }
                    MiuixGroup(title = "最近数据") {
                        Text(
                            state.payload,
                            Modifier.fillMaxWidth().padding(16.dp),
                            color = MiuiSecondaryText,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (state.busy) {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth(),
                            color = MiuiOrange,
                            trackColor = Color(0xFFFFD8C2)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            title.uppercase(),
            Modifier.padding(start = 16.dp),
            color = MiuiSecondaryText,
            style = MaterialTheme.typography.labelMedium
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MiuiSurface,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 1.dp
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun MiuixInputRow(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(10.dp),
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text("http://服务器地址:8765/api/phone-data") },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MiuiOrange,
            focusedLabelColor = MiuiOrange
        )
    )
}

@Composable
private fun MiuixStatusRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MiuiText)
        Text(value, color = MiuiSecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MiuixActionRow(label: String, summary: String, onClick: () -> Unit, disabled: Boolean) {
    Button(
        onClick = onClick,
        enabled = !disabled,
        modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MiuiText,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MiuiSecondaryText
        ),
        elevation = null
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
            Text(summary, Modifier.fillMaxWidth(), color = MiuiSecondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MiuixSwitchRow(label: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = MiuiText, style = MaterialTheme.typography.bodyLarge)
            Text(summary, color = MiuiSecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MiuiOrange
            )
        )
    }
}

@Composable
private fun MiuixThemeRow(onThemeChange: (PhoneLinkTheme) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("界面风格", color = MiuiText, style = MaterialTheme.typography.bodyLarge)
            Text("当前使用 MiuiX", color = MiuiSecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { onThemeChange(PhoneLinkTheme.MATERIAL3) },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFE7D7),
                contentColor = MiuiOrange
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            Text("Material 3")
        }
    }
}

@Composable
private fun MiuixDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Color(0xFFF0F0F0)))
}

private class ScreenState(
    val url: String,
    val status: String,
    val payload: String,
    val busy: Boolean,
    val automatic: Boolean,
    val setUrl: (String) -> Unit,
    val setAutomatic: (Boolean) -> Unit,
    val collectAndSend: () -> Unit,
    val requestPermissions: () -> Unit
)

@Composable
private fun PhoneLinkScreenState(content: @Composable (ScreenState) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("PhoneLinkPrefs", 0) }
    val scope = rememberCoroutineScope()
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
            runCatching {
                val data = collector.collectAll()
                val json = GsonBuilder().setPrettyPrinting().create().toJson(data)
                json to postJson(url, json)
            }.onSuccess {
                payload = it.first
                status = if (it.second in 200..299) "发送成功（HTTP ${it.second}）" else "服务器返回 HTTP ${it.second}"
            }.onFailure {
                status = "发送失败：${it.message ?: "未知错误"}"
            }
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

    content(
        ScreenState(
            url = url,
            status = status,
            payload = payload,
            busy = busy,
            automatic = automatic,
            setUrl = {
                url = it
                prefs.edit().putString("server_url", it).apply()
            },
            setAutomatic = {
                automatic = it
                prefs.edit().putBoolean("auto_collect", it).apply()
            },
            collectAndSend = ::collectAndSend,
            requestPermissions = { permissionLauncher.launch(permissions) }
        )
    )
}

private suspend fun postJson(url: String, payload: String): Int = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder()
        .url(url)
        .post(payload.toRequestBody("application/json".toMediaType()))
        .build()
    client.newCall(request).execute().use { it.code }
}
