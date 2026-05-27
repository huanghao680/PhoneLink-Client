# 🦞 PicoClaw PhoneLink Client

Android 手机数据采集 App，实时采集手机状态并发送给 PicoClaw 服务器。

## 📱 功能

| 功能 | 说明 | 需要权限 |
|------|------|----------|
| 🔍 当前 Activity | 前台应用包名、Activity名 | 使用情况访问 |
| 🔋 电池状态 | 电量、充电状态、温度 | 无 |
| 📍 GPS 定位 | 经纬度、海拔、精度 | 定位权限 |
| 📲 设备信息 | 品牌、型号、系统版本 | 无 |
| 📶 网络状态 | WiFi/移动网络 | 网络状态 |
| 🔄 后台采集 | 前台服务，每5分钟自动采集 | 通知权限 |

## 🚀 编译安装

### Android Studio
用 Android Studio 打开项目目录，编译安装到手机。

### 命令行
```bash
./gradlew assembleDebug
# APK 在 app/build/outputs/apk/debug/app-debug.apk
```

## ⚙️ 配置

1. 打开 App，点击「🔐 授权所有权限」
2. 填写服务器地址：`http://你的服务器IP:8765/api/phone-data`
3. 点击「📤 采集并发送」

## 📦 技术栈

- Kotlin + Android SDK
- OkHttp + Gson
- AndroidX + Material Design
- Min SDK 26 (Android 8.0)

## 📄 License

MIT
