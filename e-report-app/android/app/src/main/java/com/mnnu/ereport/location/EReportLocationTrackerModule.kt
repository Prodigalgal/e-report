package com.mnnu.ereport.location

import android.content.Context
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * 登录态位置跟踪的原生接口。
 *
 * 只暴露“启停采集”与“读取最新位置”，网络上报由 JS 层完成——
 * Android 请求需要设备签名与 nonce 防重放，在原生层重建签名逻辑会形成第二套安全实现。
 */
class EReportLocationTrackerModule(
  private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "EReportLocationTracker"

  @ReactMethod
  fun isTracking(promise: Promise) {
    promise.resolve(LatestLocationHolder.current() != null)
  }

  @ReactMethod
  fun startTracking(promise: Promise) {
    try {
      EReportLocationTrackingService.start(reactContext)
      promise.resolve(true)
    } catch (error: Exception) {
      promise.reject("E_LOCATION_TRACKING_START_FAILED", error.message ?: "无法启动位置服务。", error)
    }
  }

  @ReactMethod
  fun stopTracking(promise: Promise) {
    try {
      EReportLocationTrackingService.stop(reactContext)
      LatestLocationHolder.clear()
      promise.resolve(true)
    } catch (error: Exception) {
      promise.reject("E_LOCATION_TRACKING_STOP_FAILED", error.message ?: "无法停止位置服务。", error)
    }
  }

  /**
   * 读取当前最新位置。返回 null 表示尚无可用定位，调用方不应据此上报。
   */
  @ReactMethod
  fun getLatestFix(promise: Promise) {
    val fix = LatestLocationHolder.current()
    if (fix == null) {
      promise.resolve(null)
      return
    }
    promise.resolve(Arguments.createMap().apply {
      putDouble("latitude", fix.latitude)
      putDouble("longitude", fix.longitude)
      if (fix.accuracyMeters != null) {
        putDouble("accuracyMeters", fix.accuracyMeters)
      }
      putString("provider", fix.provider)
      putDouble("fixedAtMillis", fix.fixedAtMillis.toDouble())
      if (fix.gcjLatitude != null && fix.gcjLongitude != null) {
        putDouble("gcjLatitude", fix.gcjLatitude)
        putDouble("gcjLongitude", fix.gcjLongitude)
      }
    })
  }

  private fun appContext(): Context = reactContext.applicationContext
}
