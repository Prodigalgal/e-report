package com.mnnu.ereport.observability

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class EReportObservabilityModule(
  private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

  override fun getName() = "EReportObservability"

  @ReactMethod
  fun getEnvironmentDiagnostics(promise: Promise) {
    try {
      val result = Arguments.createMap()
      result.putString("manufacturer", Build.MANUFACTURER)
      result.putString("brand", Build.BRAND)
      result.putString("model", Build.MODEL)
      result.putString("androidVersion", Build.VERSION.RELEASE)
      result.putInt("sdkInt", Build.VERSION.SDK_INT)
      result.putString("appVersion", appVersionName())
      result.putString("appBuild", appVersionCode().toString())
      result.putBoolean("powerSaveMode", powerManager().isPowerSaveMode)
      result.putMap("memory", memoryDiagnostics())
      result.putMap("storage", storageDiagnostics())
      result.putMap("battery", batteryDiagnostics())
      result.putMap("permissions", permissionDiagnostics())
      result.putMap("location", locationDiagnostics())
      promise.resolve(result)
    } catch (exception: Exception) {
      promise.reject("ENVIRONMENT_DIAGNOSTICS_FAILED", "无法读取应用运行环境。", exception)
    }
  }

  @ReactMethod
  fun consumeLastCrash(promise: Promise) {
    try {
      val preferences = reactContext.getSharedPreferences(
        EReportCrashRecorder.preferencesName(),
        Context.MODE_PRIVATE,
      )
      val timestamp = preferences.getLong("crash_timestamp", 0L)
      if (timestamp <= 0L) {
        promise.resolve(null)
        return
      }
      val result = Arguments.createMap()
      result.putDouble("timestamp", timestamp.toDouble())
      result.putString("thread", preferences.getString("crash_thread", ""))
      result.putString("exceptionType", preferences.getString("crash_type", ""))
      result.putString("message", preferences.getString("crash_message", ""))
      result.putString("stackTrace", preferences.getString("crash_stack", ""))
      preferences.edit().clear().apply()
      promise.resolve(result)
    } catch (exception: Exception) {
      promise.reject("CRASH_MARKER_READ_FAILED", "无法读取上次崩溃记录。", exception)
    }
  }

  private fun memoryDiagnostics() = Arguments.createMap().apply {
    val manager = reactContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    putDouble("availableBytes", info.availMem.toDouble())
    putDouble("totalBytes", info.totalMem.toDouble())
    putBoolean("lowMemory", info.lowMemory)
    putDouble("thresholdBytes", info.threshold.toDouble())
  }

  private fun storageDiagnostics() = Arguments.createMap().apply {
    val stats = StatFs(Environment.getDataDirectory().absolutePath)
    putDouble("availableBytes", stats.availableBytes.toDouble())
    putDouble("totalBytes", stats.totalBytes.toDouble())
  }

  private fun batteryDiagnostics() = Arguments.createMap().apply {
    val manager = reactContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    putInt("level", manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
    putBoolean("charging", manager.isCharging)
  }

  private fun permissionDiagnostics() = Arguments.createMap().apply {
    putBoolean("fineLocation", permissionGranted(Manifest.permission.ACCESS_FINE_LOCATION))
    putBoolean("coarseLocation", permissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION))
    putBoolean("camera", permissionGranted(Manifest.permission.CAMERA))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      putBoolean("readImages", permissionGranted(Manifest.permission.READ_MEDIA_IMAGES))
      putBoolean("readVideo", permissionGranted(Manifest.permission.READ_MEDIA_VIDEO))
    }
  }

  private fun locationDiagnostics() = Arguments.createMap().apply {
    val manager = reactContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    putBoolean("servicesEnabled", manager.isLocationEnabled)
    putBoolean("gpsProviderEnabled", providerEnabled(manager, LocationManager.GPS_PROVIDER))
    putBoolean("networkProviderEnabled", providerEnabled(manager, LocationManager.NETWORK_PROVIDER))
    val providers = Arguments.createArray()
    manager.getProviders(true).forEach(providers::pushString)
    putArray("enabledProviders", providers)
  }

  private fun providerEnabled(manager: LocationManager, provider: String): Boolean {
    return try {
      manager.isProviderEnabled(provider)
    } catch (_: Exception) {
      false
    }
  }

  private fun permissionGranted(permission: String): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
      || reactContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
  }

  @Suppress("DEPRECATION")
  private fun appVersionName(): String {
    return reactContext.packageManager.getPackageInfo(reactContext.packageName, 0).versionName.orEmpty()
  }

  @Suppress("DEPRECATION")
  private fun appVersionCode(): Long {
    val info = reactContext.packageManager.getPackageInfo(reactContext.packageName, 0)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
  }

  private fun powerManager(): PowerManager {
    return reactContext.getSystemService(Context.POWER_SERVICE) as PowerManager
  }
}
