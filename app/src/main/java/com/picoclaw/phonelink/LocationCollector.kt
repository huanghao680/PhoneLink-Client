package com.picoclaw.phonelink

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

/**
 * 获取GPS定位信息
 * 需要权限：ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION
 */
object LocationCollector {

    data class LocationInfo(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val accuracy: Float,
        val provider: String,
        val address: String
    )

    /**
     * 获取当前位置（一次性）
     * @param context 上下文
     * @param maxAgeSeconds 最大接受的缓存位置年龄（秒），默认60秒
     * @param timeoutSeconds 超时时间（秒），默认10秒
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(
        context: Context,
        maxAgeSeconds: Long = 60,
        timeoutSeconds: Long = 10
    ): LocationInfo {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 先尝试获取最近的缓存位置
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.FUSED_PROVIDER
        ).filter { locationManager.isProviderEnabled(it) }

        // 尝试缓存位置
        for (provider in providers) {
            val lastKnown = locationManager.getLastKnownLocation(provider)
            if (lastKnown != null) {
                val age = (System.currentTimeMillis() - lastKnown.time) / 1000
                if (age <= maxAgeSeconds) {
                    return buildLocationInfo(context, lastKnown, provider)
                }
            }
        }

        // 没有合适的缓存位置，请求新位置
        return try {
            val location = requestFreshLocation(locationManager, providers, timeoutSeconds * 1000)
            buildLocationInfo(context, location, location.provider ?: "unknown")
        } catch (e: Exception) {
            // 最后尝试任何缓存位置
            for (provider in providers) {
                val lastKnown = locationManager.getLastKnownLocation(provider)
                if (lastKnown != null) {
                    return buildLocationInfo(context, lastKnown, provider)
                }
            }
            LocationInfo(0.0, 0.0, 0.0, 0f, "unavailable", "无法获取位置")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(
        locationManager: LocationManager,
        providers: List<String>,
        timeoutMs: Long
    ): Location = suspendCancellableCoroutine { cont ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                if (cont.isActive) cont.resume(location)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // 注册监听，请求位置更新
        for (provider in providers) {
            try {
                locationManager.requestLocationUpdates(
                    provider, 0L, 0f, listener, Looper.getMainLooper()
                )
            } catch (_: Exception) {}
        }

        // 超时取消
        cont.invokeOnCancellation {
            locationManager.removeUpdates(listener)
        }

        // 设置超时
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            locationManager.removeUpdates(listener)
            if (cont.isActive) {
                // 超时，尝试返回缓存位置
                for (provider in providers) {
                    val cached = locationManager.getLastKnownLocation(provider)
                    if (cached != null) {
                        cont.resume(cached)
                        return@postDelayed
                    }
                }
                cont.resume(Location("fallback").apply {
                    latitude = 0.0
                    longitude = 0.0
                })
            }
        }, timeoutMs)
    }

    private fun buildLocationInfo(context: Context, location: Location, provider: String): LocationInfo {
        // 尝试反向地理编码获取地址
        val address = try {
            val geocoder = Geocoder(context, Locale.CHINA)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                "${addr.adminArea ?: ""}${addr.locality ?: ""}${addr.thoroughfare ?: ""}".trim()
            } else ""
        } catch (e: Exception) {
            ""
        }

        return LocationInfo(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracy = location.accuracy,
            provider = provider,
            address = address
        )
    }
}
