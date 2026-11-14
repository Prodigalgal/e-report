package com.mnnu.ereport.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mnnu.ereport.MainActivity
import com.mnnu.ereport.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 登录态位置采集前台服务。
 *
 * 职责边界：服务只负责持续获取并持有最新位置，不做网络上报。
 * 上报由 JS 层通过已签名的 HTTP 客户端完成——Android 侧的每个请求都需要设备签名与
 * nonce 防重放，在原生层重复实现签名会形成第二套安全逻辑，是明确的重复与风险来源。
 *
 * 生命周期：登录后由 JS 启动，退出登录或撤销会话时停止；随进程结束而结束。
 */
class EReportLocationTrackingService : Service() {

  private val locationManager: LocationManager? by lazy {
    getSystemService(Context.LOCATION_SERVICE) as? LocationManager
  }

  private val mainHandler = Handler(Looper.getMainLooper())
  private var started = false

  private val listener = object : LocationListener {
    override fun onLocationChanged(location: Location) {
      publishFix(location)
    }

    @Deprecated("Android 的旧回调，仍需实现以兼容部分机型")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) {
      mainHandler.post { requestUpdates() }
    }

    override fun onProviderDisabled(provider: String) = Unit
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    LatestLocationHolder.clear()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopTracking()
      return START_NOT_STICKY
    }

    startForegroundNotification()
    if (!started) {
      started = true
      requestUpdates()
    }
    // 被系统回收后不自动重启：采集依赖登录态，重启时登录态可能已失效。
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    stopTracking()
    super.onDestroy()
  }

  private fun requestUpdates() {
    val manager = locationManager ?: return
    if (!hasFineLocationPermission()) {
      return
    }

    val providers = buildList {
      if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        add(LocationManager.GPS_PROVIDER)
      }
      if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
        add(LocationManager.NETWORK_PROVIDER)
      }
    }
    if (providers.isEmpty()) {
      return
    }

    // 先取一次缓存，让后台地图在刚登录时就有位置，而不是等到第一次回调。
    providers.forEach { provider ->
      runCatching {
        manager.getLastKnownLocation(provider)?.let { publishFix(it) }
      }
    }

    providers.forEach { provider ->
      runCatching {
        manager.requestLocationUpdates(
          provider,
          MIN_UPDATE_INTERVAL_MS,
          MIN_UPDATE_DISTANCE_METERS,
          listener,
          Looper.getMainLooper()
        )
      }
    }
  }

  private fun publishFix(location: Location) {
    if (!location.hasAccuracy()) {
      // 没有精度信息的定位结果可靠性低，不上报以免在地图上产生假点位。
      return
    }
    // 系统定位返回 WGS84，正是后端存储契约所需的坐标系。
    val gcjCoordinate = CoordinateTransform.wgs84ToGcj02(longitude = location.longitude, latitude = location.latitude)
    LatestLocationHolder.update(
      LatestLocationFix(
        latitude = location.latitude,
        longitude = location.longitude,
        accuracyMeters = location.accuracy.toDouble(),
        provider = location.provider ?: "android-native",
        fixedAtMillis = if (location.time > 0) location.time else System.currentTimeMillis(),
        gcjLatitude = gcjCoordinate.latitude,
        gcjLongitude = gcjCoordinate.longitude
      )
    )
  }

  private fun stopTracking() {
    if (started) {
      runCatching { locationManager?.removeUpdates(listener) }
      started = false
    }
    LatestLocationHolder.clear()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun hasFineLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
    this,
    Manifest.permission.ACCESS_FINE_LOCATION
  ) == PackageManager.PERMISSION_GRANTED

  private fun startForegroundNotification() {
    ensureNotificationChannel()
    val tapIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      },
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
      .setContentTitle(NOTIFICATION_TITLE)
      .setContentText(NOTIFICATION_TEXT)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setContentIntent(tapIntent)
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
          0
        }
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) {
      return
    }
    val channel = NotificationChannel(
      NOTIFICATION_CHANNEL_ID,
      NOTIFICATION_CHANNEL_NAME,
      NotificationManager.IMPORTANCE_LOW
    ).apply {
      description = NOTIFICATION_TEXT
      setShowBadge(false)
    }
    manager.createNotificationChannel(channel)
  }

  companion object {
    const val ACTION_START = "com.mnnu.ereport.location.action.START_TRACKING"
    const val ACTION_STOP = "com.mnnu.ereport.location.action.STOP_TRACKING"

    private const val NOTIFICATION_CHANNEL_ID = "ereport-location-tracking"
    private const val NOTIFICATION_CHANNEL_NAME = "位置服务"
    private const val NOTIFICATION_TITLE = "正在上报当前位置"
    private const val NOTIFICATION_TEXT = "网络运维管理在登录期间持续上报位置，用于工单派单与人员调度。"
    private const val NOTIFICATION_ID = 7101

    /** 位置更新间隔；与后台分钟级刷新配合，无需更高频率。 */
    private const val MIN_UPDATE_INTERVAL_MS = 60_000L
    private const val MIN_UPDATE_DISTANCE_METERS = 20f

    fun start(context: Context) {
      val intent = Intent(context, EReportLocationTrackingService::class.java).apply {
        action = ACTION_START
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
      val intent = Intent(context, EReportLocationTrackingService::class.java).apply {
        action = ACTION_STOP
      }
      runCatching { context.startService(intent) }
        .onFailure { context.stopService(Intent(context, EReportLocationTrackingService::class.java)) }
    }
  }
}

/** 最新位置快照，由前台服务写入、原生模块读取。 */
data class LatestLocationFix(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Double?,
  val provider: String,
  val fixedAtMillis: Long,
  val gcjLatitude: Double?,
  val gcjLongitude: Double?
)

/**
 * 进程内最新位置持有者。
 *
 * 只保留一条最新位置：需求只要求展示当前在岗人员分布，不做轨迹回放，
 * 因此不落本地历史，避免无必要的定位数据堆积。
 */
object LatestLocationHolder {
  @Volatile
  private var fix: LatestLocationFix? = null

  fun update(next: LatestLocationFix) {
    fix = next
  }

  fun current(): LatestLocationFix? = fix

  fun clear() {
    fix = null
  }
}
