package com.mnnu.ereport.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.mnnu.ereport.location.CoordinateTransform
import kotlin.math.abs

class EReportNavigationModule(
  reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "EReportNavigation"

  @ReactMethod
  fun openNavigationChooser(
    longitude: Double,
    latitude: Double,
    destinationName: String,
    promise: Promise
  ) {
    if (!isValidCoordinate(longitude, latitude)) {
      promise.reject("E_NAVIGATION_COORDINATE", "工单GPS坐标无效，请补录正确的经度、纬度后重试。")
      return
    }

    val activity = reactApplicationContext.currentActivity
    if (activity == null) {
      promise.reject("E_NAVIGATION_ACTIVITY", "当前页面暂时无法打开地图，请返回工单详情后重试。")
      return
    }

    val safeDestinationName = destinationName.trim().ifEmpty { "工单现场" }
    val installedMapIntents = buildInstalledMapIntents(longitude, latitude, safeDestinationName)
    val launchIntent = when (installedMapIntents.size) {
      0 -> buildWebNavigationIntent(longitude, latitude, safeDestinationName)
      1 -> installedMapIntents.first()
      else -> Intent.createChooser(installedMapIntents.first(), "选择地图应用").apply {
        putExtra(Intent.EXTRA_INITIAL_INTENTS, installedMapIntents.drop(1).toTypedArray())
      }
    }

    activity.runOnUiThread {
      try {
        activity.startActivity(launchIntent)
        promise.resolve(true)
      } catch (_: ActivityNotFoundException) {
        promise.reject("E_NAVIGATION_APP_NOT_FOUND", "设备上没有可用的地图或浏览器，请安装高德地图、百度地图后重试。")
      } catch (error: Exception) {
        promise.reject("E_NAVIGATION_START", "地图应用启动失败，请稍后重试。", error)
      }
    }
  }

  private fun buildInstalledMapIntents(
    longitude: Double,
    latitude: Double,
    destinationName: String
  ): List<Intent> {
    val intents = mutableListOf<Intent>()
    val gcj02 = CoordinateTransform.wgs84ToGcj02(longitude, latitude)

    if (isPackageInstalled(AMAP_PACKAGE)) {
      intents += Intent(
        Intent.ACTION_VIEW,
        Uri.Builder()
          .scheme("androidamap")
          .authority("navi")
          .appendQueryParameter("sourceApplication", APP_NAME)
          .appendQueryParameter("poiname", destinationName)
          .appendQueryParameter("lat", gcj02.latitude.toString())
          .appendQueryParameter("lon", gcj02.longitude.toString())
          .appendQueryParameter("dev", "0")
          .build()
      ).setPackage(AMAP_PACKAGE)
    }

    if (isPackageInstalled(BAIDU_MAP_PACKAGE)) {
      intents += Intent(
        Intent.ACTION_VIEW,
        Uri.Builder()
          .scheme("baidumap")
          .authority("map")
          .appendPath("navi")
          .appendQueryParameter("location", "${gcj02.latitude},${gcj02.longitude}")
          .appendQueryParameter("query", destinationName)
          .appendQueryParameter("coord_type", "gcj02")
          .appendQueryParameter("src", "andr.mnnu.ereport")
          .build()
      ).setPackage(BAIDU_MAP_PACKAGE)
    }

    if (isPackageInstalled(GOOGLE_MAPS_PACKAGE)) {
      intents += Intent(
        Intent.ACTION_VIEW,
        Uri.Builder()
          .scheme("google.navigation")
          .appendQueryParameter("q", "$latitude,$longitude")
          .appendQueryParameter("mode", "d")
          .build()
      ).setPackage(GOOGLE_MAPS_PACKAGE)
    }

    return intents
  }

  private fun buildWebNavigationIntent(
    longitude: Double,
    latitude: Double,
    destinationName: String
  ): Intent {
    val destination = "$longitude,$latitude,$destinationName"
    val uri = Uri.Builder()
      .scheme("https")
      .authority("uri.amap.com")
      .appendPath("navigation")
      .appendQueryParameter("to", destination)
      .appendQueryParameter("mode", "car")
      .appendQueryParameter("coordinate", "wgs84")
      .appendQueryParameter("callnative", "1")
      .appendQueryParameter("src", "e-report-app")
      .build()
    return Intent(Intent.ACTION_VIEW, uri)
  }

  private fun isPackageInstalled(packageName: String): Boolean {
    return try {
      val packageManager = reactApplicationContext.packageManager
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
      }
      true
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
      false
    }
  }

  private fun isValidCoordinate(longitude: Double, latitude: Double): Boolean {
    return longitude.isFinite()
      && latitude.isFinite()
      && abs(longitude) <= 180.0
      && abs(latitude) <= 90.0
      && !(longitude == 0.0 && latitude == 0.0)
  }

  private companion object {
    const val APP_NAME = "网络运维管理"
    const val AMAP_PACKAGE = "com.autonavi.minimap"
    const val BAIDU_MAP_PACKAGE = "com.baidu.BaiduMap"
    const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
  }
}
