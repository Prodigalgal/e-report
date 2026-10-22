package com.mnnu.ereport.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class EReportLocationModule(
  private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {
  override fun getName(): String = "EReportLocation"

  @ReactMethod
  fun isAmapConfigured(promise: Promise) {
    promise.resolve(readAmapApiKey().isNotBlank())
  }

  @ReactMethod
  fun getLocationDiagnostics(promise: Promise) {
    val locationManager = reactContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    promise.resolve(buildDiagnostics(locationManager))
  }

  @ReactMethod
  fun getGpsProviderCurrentPosition(options: ReadableMap?, promise: Promise) {
    getLocationFromProviders(
      options = options,
      promise = promise,
      requestedProviders = listOf(LocationManager.GPS_PROVIDER),
      providerLabel = "GPS_PROVIDER",
      source = "android-gps-provider",
      noProviderCode = "E_GPS_PROVIDER_DISABLED",
      noProviderMessage = "手机系统GPS_PROVIDER未开启，请打开系统定位和高精度定位后重试。",
      timeoutCode = "E_GPS_PROVIDER_TIMEOUT",
      timeoutMessagePrefix = "系统GPS_PROVIDER"
    )
  }

  @ReactMethod
  fun getNativeCurrentPosition(options: ReadableMap?, promise: Promise) {
    getLocationFromProviders(
      options = options,
      promise = promise,
      requestedProviders = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER),
      providerLabel = "GPS/NETWORK_PROVIDER",
      source = "android-native",
      noProviderCode = "E_LOCATION_PROVIDER_DISABLED",
      noProviderMessage = "GPS和网络定位供应器都不可用。",
      timeoutCode = "E_NATIVE_LOCATION_TIMEOUT",
      timeoutMessagePrefix = "手机原生综合定位"
    )
  }

  private fun getLocationFromProviders(
    options: ReadableMap?,
    promise: Promise,
    requestedProviders: List<String>,
    providerLabel: String,
    source: String,
    noProviderCode: String,
    noProviderMessage: String,
    timeoutCode: String,
    timeoutMessagePrefix: String
  ) {
    if (!hasFineLocationPermission()) {
      promise.reject("E_LOCATION_PERMISSION_DENIED", "定位权限未开启，请允许应用使用精确位置。")
      return
    }

    val locationManager = reactContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) {
      promise.reject("E_LOCATION_MANAGER_UNAVAILABLE", "手机系统定位服务不可用。")
      return
    }
    if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
      promise.reject("E_LOCATION_SERVICE_DISABLED", "手机系统定位总开关未开启。")
      return
    }

    val providers = requestedProviders.filter { isProviderEnabled(locationManager, it) }
    if (providers.isEmpty()) {
      promise.reject(noProviderCode, noProviderMessage)
      return
    }

    val timeoutMs = options.readLong("timeoutMs", 45000L).coerceIn(5000L, 120000L)
    val requiredAccuracyMeters = options.readDouble("requiredAccuracyMeters", 100.0).coerceIn(10.0, 1000.0)
    val cacheMaxAgeMs = options.readLong("cacheMaxAgeMs", 30000L).coerceIn(0L, 300000L)
    val finished = AtomicBoolean(false)
    val handler = Handler(Looper.getMainLooper())
    var bestLocation: Location? = providers
      .mapNotNull { getLastKnownLocation(locationManager, it) }
      .filter { isRecentLocation(it, cacheMaxAgeMs) }
      .minWithOrNull(::compareLocationQuality)

    fun cleanup(listener: LocationListener) {
      handler.removeCallbacksAndMessages(null)
      try {
        locationManager.removeUpdates(listener)
      } catch (_: SecurityException) {
      }
    }

    lateinit var listener: LocationListener
    fun resolveLocation(location: Location, source: String) {
      if (!finished.compareAndSet(false, true)) {
        return
      }
      cleanup(listener)
      promise.resolve(toNativeLocationMap(location, source))
    }

    fun rejectLocation(code: String, message: String) {
      if (!finished.compareAndSet(false, true)) {
        return
      }
      cleanup(listener)
      promise.reject(code, message)
    }

    listener = object : LocationListener {
      override fun onLocationChanged(location: Location) {
        if (!isValidCoordinate(location.longitude, location.latitude)) {
          return
        }
        val currentBest = bestLocation
        if (currentBest == null || compareLocationQuality(location, currentBest) < 0) {
          bestLocation = location
        }
        if (isAccurateEnough(location, requiredAccuracyMeters)) {
          resolveLocation(location, source)
        }
      }

      @Deprecated("Required for old Android API compatibility")
      override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
      }

      override fun onProviderEnabled(provider: String) {
      }

      override fun onProviderDisabled(provider: String) {
      }
    }

    bestLocation?.let { cached ->
      if (isAccurateEnough(cached, requiredAccuracyMeters)) {
        resolveLocation(cached, "$source-cache")
        return
      }
    }

    handler.postDelayed({
      val message = bestLocation?.let {
        val accuracyText = if (it.hasAccuracy()) "，当前最佳精度约${it.accuracy.toInt()}米" else ""
        "$timeoutMessagePrefix 在${timeoutMs / 1000}秒内未返回满足精度要求的坐标$accuracyText。"
      } ?: "$timeoutMessagePrefix 在${timeoutMs / 1000}秒内没有返回坐标。"
      rejectLocation(timeoutCode, message)
    }, timeoutMs)

    try {
      providers.forEach { provider ->
        locationManager.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper())
      }
    } catch (error: SecurityException) {
      rejectLocation("E_LOCATION_PERMISSION_DENIED", "定位权限未开启，请允许应用使用精确位置。")
    } catch (error: RuntimeException) {
      rejectLocation("E_NATIVE_LOCATION_FAILED", "$providerLabel 定位启动失败：${error.message ?: "未知错误"}")
    }
  }

  @ReactMethod
  fun getAmapCurrentPosition(options: ReadableMap?, promise: Promise) {
    if (!hasFineLocationPermission()) {
      promise.reject("E_LOCATION_PERMISSION_DENIED", "定位权限未开启，请允许应用使用精确位置。")
      return
    }

    if (readAmapApiKey().isBlank()) {
      promise.reject("E_AMAP_KEY_MISSING", "当前安装包未配置高德定位SDK Key。")
      return
    }

    val timeoutMs = options.readLong("timeoutMs", 45000L).coerceIn(5000L, 120000L)
    val finished = AtomicBoolean(false)
    val handler = Handler(Looper.getMainLooper())
    val appContext = reactContext.applicationContext
    val client = try {
      AMapLocationClient.updatePrivacyShow(appContext, true, true)
      AMapLocationClient.updatePrivacyAgree(appContext, true)
      AMapLocationClient(appContext)
    } catch (error: Exception) {
      promise.reject("E_AMAP_CLIENT_INIT_FAILED", "高德定位SDK初始化失败：${error.message ?: "未知错误"}")
      return
    }

    fun cleanup() {
      handler.removeCallbacksAndMessages(null)
      try {
        client.stopLocation()
        client.onDestroy()
      } catch (_: Exception) {
      }
    }

    fun resolveLocation(location: AMapLocation) {
      if (!finished.compareAndSet(false, true)) {
        return
      }
      cleanup()
      promise.resolve(toAmapLocationMap(location))
    }

    fun rejectLocation(code: String, message: String) {
      if (!finished.compareAndSet(false, true)) {
        return
      }
      cleanup()
      promise.reject(code, message)
    }

    val option = AMapLocationClientOption().apply {
      locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
      isOnceLocation = true
      isOnceLocationLatest = true
      isNeedAddress = true
      isLocationCacheEnable = false
      httpTimeOut = timeoutMs
      isMockEnable = false
    }

    client.setLocationOption(option)
    client.setLocationListener { location ->
      if (location == null) {
        rejectLocation("E_AMAP_LOCATION_EMPTY", "高德定位没有返回坐标。")
        return@setLocationListener
      }
      if (location.errorCode == AMapLocation.LOCATION_SUCCESS
        && isValidCoordinate(location.longitude, location.latitude)
      ) {
        resolveLocation(location)
        return@setLocationListener
      }
      val errorInfo = location.errorInfo?.takeIf { it.isNotBlank() } ?: "未知错误"
      rejectLocation("E_AMAP_LOCATION_FAILED", "高德定位失败：${location.errorCode}，$errorInfo")
    }

    handler.postDelayed({
      rejectLocation("E_AMAP_LOCATION_TIMEOUT", "高德定位在${timeoutMs / 1000}秒内没有返回坐标。")
    }, timeoutMs)

    try {
      client.startLocation()
    } catch (error: Exception) {
      rejectLocation("E_AMAP_LOCATION_START_FAILED", "高德定位启动失败：${error.message ?: "未知错误"}")
    }
  }

  private fun hasFineLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      reactContext,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
  }

  private fun hasCoarseLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      reactContext,
      Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
  }

  private fun isProviderEnabled(locationManager: LocationManager, provider: String): Boolean {
    return try {
      locationManager.isProviderEnabled(provider)
    } catch (_: RuntimeException) {
      false
    }
  }

  private fun getLastKnownLocation(locationManager: LocationManager, provider: String): Location? {
    return try {
      locationManager.getLastKnownLocation(provider)
    } catch (_: SecurityException) {
      null
    } catch (_: RuntimeException) {
      null
    }
  }

  private fun isRecentLocation(location: Location, maxAgeMs: Long): Boolean {
    if (maxAgeMs <= 0L) {
      return false
    }
    val locationTime = location.time
    if (locationTime <= 0L) {
      return false
    }
    return abs(System.currentTimeMillis() - locationTime) <= maxAgeMs
  }

  private fun isAccurateEnough(location: Location, requiredAccuracyMeters: Double): Boolean {
    return isValidCoordinate(location.longitude, location.latitude)
      && location.hasAccuracy()
      && location.accuracy <= requiredAccuracyMeters
  }

  private fun compareLocationQuality(left: Location, right: Location): Int {
    val leftAccuracy = if (left.hasAccuracy()) left.accuracy else Float.MAX_VALUE
    val rightAccuracy = if (right.hasAccuracy()) right.accuracy else Float.MAX_VALUE
    val accuracyCompare = leftAccuracy.compareTo(rightAccuracy)
    if (accuracyCompare != 0) {
      return accuracyCompare
    }
    return right.time.compareTo(left.time)
  }

  private fun toNativeLocationMap(location: Location, source: String): WritableMap {
    return Arguments.createMap().apply {
      putString("provider", "android-native")
      putString("source", source)
      putString("coordinateSystem", "wgs84")
      putString("rawProvider", location.provider)
      putDouble("longitude", location.longitude)
      putDouble("latitude", location.latitude)
      putDouble("timestamp", location.time.toDouble())
      if (location.hasAccuracy()) {
        putDouble("accuracy", location.accuracy.toDouble())
      }
      if (location.hasAltitude()) {
        putDouble("altitude", location.altitude)
      }
      if (location.hasBearing()) {
        putDouble("bearing", location.bearing.toDouble())
      }
      if (location.hasSpeed()) {
        putDouble("speed", location.speed.toDouble())
      }
    }
  }

  private fun buildDiagnostics(locationManager: LocationManager?): WritableMap {
    return Arguments.createMap().apply {
      putBoolean("finePermissionGranted", hasFineLocationPermission())
      putBoolean("coarsePermissionGranted", hasCoarseLocationPermission())
      putBoolean("locationManagerAvailable", locationManager != null)
      putBoolean("locationServicesEnabled", locationManager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false)
      putBoolean("gpsProviderEnabled", locationManager?.let { isProviderEnabled(it, LocationManager.GPS_PROVIDER) } ?: false)
      putBoolean("networkProviderEnabled", locationManager?.let { isProviderEnabled(it, LocationManager.NETWORK_PROVIDER) } ?: false)
      putBoolean("passiveProviderEnabled", locationManager?.let { isProviderEnabled(it, LocationManager.PASSIVE_PROVIDER) } ?: false)
      putBoolean("amapConfigured", readAmapApiKey().isNotBlank())
      putString("androidVersion", Build.VERSION.RELEASE ?: "")
      putDouble("sdkInt", Build.VERSION.SDK_INT.toDouble())
      putString("manufacturer", Build.MANUFACTURER ?: "")
      putString("model", Build.MODEL ?: "")
      val providerArray = Arguments.createArray()
      locationManager?.getProviders(true)?.forEach { providerArray.pushString(it) }
      putArray("enabledProviders", providerArray)
      putMap("lastKnownGps", locationManager?.let { toLastKnownLocationDiagnostics(it, LocationManager.GPS_PROVIDER) })
      putMap("lastKnownNetwork", locationManager?.let { toLastKnownLocationDiagnostics(it, LocationManager.NETWORK_PROVIDER) })
    }
  }

  private fun toLastKnownLocationDiagnostics(locationManager: LocationManager, provider: String): WritableMap {
    val location = getLastKnownLocation(locationManager, provider)
    return Arguments.createMap().apply {
      putString("provider", provider)
      putBoolean("available", location != null && isValidCoordinate(location.longitude, location.latitude))
      if (location != null) {
        putDouble("longitude", location.longitude)
        putDouble("latitude", location.latitude)
        putDouble("timestamp", location.time.toDouble())
        if (location.time > 0L) {
          putDouble("ageMs", abs(System.currentTimeMillis() - location.time).toDouble())
        }
        if (location.hasAccuracy()) {
          putDouble("accuracy", location.accuracy.toDouble())
        }
      }
    }
  }

  private fun toAmapLocationMap(location: AMapLocation): WritableMap {
    val wgs84 = CoordinateTransform.gcj02ToWgs84(location.longitude, location.latitude)
    return Arguments.createMap().apply {
      putString("provider", "amap")
      putString("source", "amap-sdk")
      putString("coordinateSystem", "wgs84")
      putString("rawProvider", location.provider)
      putDouble("longitude", wgs84.longitude)
      putDouble("latitude", wgs84.latitude)
      putDouble("rawLongitude", location.longitude)
      putDouble("rawLatitude", location.latitude)
      putString("rawCoordinateSystem", "gcj02")
      putDouble("timestamp", location.time.toDouble())
      putDouble("accuracy", location.accuracy.toDouble())
      putDouble("locationType", location.locationType.toDouble())
      putOptionalString("address", location.address)
      putOptionalString("province", location.province)
      putOptionalString("city", location.city)
      putOptionalString("district", location.district)
      putOptionalString("street", location.street)
      putOptionalString("streetNum", location.streetNum)
      putOptionalString("poiName", location.poiName)
      putOptionalString("aoiName", location.aoiName)
    }
  }

  private fun WritableMap.putOptionalString(name: String, value: String?) {
    if (!value.isNullOrBlank()) {
      putString(name, value)
    }
  }

  private fun ReadableMap?.readLong(name: String, fallback: Long): Long {
    if (this == null || !hasKey(name) || isNull(name)) {
      return fallback
    }
    return getDouble(name).toLong()
  }

  private fun ReadableMap?.readDouble(name: String, fallback: Double): Double {
    if (this == null || !hasKey(name) || isNull(name)) {
      return fallback
    }
    return getDouble(name)
  }

  private fun readAmapApiKey(): String {
    val applicationInfo = reactContext.packageManager.getApplicationInfo(
      reactContext.packageName,
      PackageManager.GET_META_DATA
    )
    return applicationInfo.metaData?.getString("com.amap.api.v2.apikey")?.trim().orEmpty()
  }

  private fun isValidCoordinate(longitude: Double, latitude: Double): Boolean {
    return longitude.isFinite()
      && latitude.isFinite()
      && abs(longitude) <= 180.0
      && abs(latitude) <= 90.0
      && !(longitude == 0.0 && latitude == 0.0)
  }
}

internal data class Coordinate(
  val longitude: Double,
  val latitude: Double
)

internal object CoordinateTransform {
  private const val PI = 3.1415926535897932384626
  private const val A = 6378245.0
  private const val EE = 0.00669342162296594323

  fun gcj02ToWgs84(longitude: Double, latitude: Double): Coordinate {
    if (isOutsideMainlandChina(longitude, latitude)) {
      return Coordinate(longitude, latitude)
    }
    val delta = delta(longitude, latitude)
    return Coordinate(
      longitude = longitude * 2 - delta.longitude,
      latitude = latitude * 2 - delta.latitude
    )
  }

  fun wgs84ToGcj02(longitude: Double, latitude: Double): Coordinate {
    if (isOutsideMainlandChina(longitude, latitude)) {
      return Coordinate(longitude, latitude)
    }
    return delta(longitude, latitude)
  }

  private fun delta(longitude: Double, latitude: Double): Coordinate {
    var transformedLatitude = transformLatitude(longitude - 105.0, latitude - 35.0)
    var transformedLongitude = transformLongitude(longitude - 105.0, latitude - 35.0)
    val radLatitude = latitude / 180.0 * PI
    var magic = sin(radLatitude)
    magic = 1 - EE * magic * magic
    val sqrtMagic = sqrt(magic)
    transformedLatitude = (transformedLatitude * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
    transformedLongitude = (transformedLongitude * 180.0) / (A / sqrtMagic * cos(radLatitude) * PI)
    return Coordinate(
      longitude = longitude + transformedLongitude,
      latitude = latitude + transformedLatitude
    )
  }

  private fun transformLatitude(x: Double, y: Double): Double {
    var result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
    result += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
    result += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
    result += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
    return result
  }

  private fun transformLongitude(x: Double, y: Double): Double {
    var result = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
    result += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
    result += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
    result += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
    return result
  }

  private fun isOutsideMainlandChina(longitude: Double, latitude: Double): Boolean {
    return longitude < 73.66 || longitude > 135.05 || latitude < 3.86 || latitude > 53.55
  }
}
