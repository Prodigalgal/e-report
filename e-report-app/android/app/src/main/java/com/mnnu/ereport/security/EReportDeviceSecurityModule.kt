package com.mnnu.ereport.security

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class EReportDeviceSecurityModule(
  private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

  override fun getName() = "EReportDeviceSecurity"

  @ReactMethod
  fun getOrCreateIdentity(promise: Promise) {
    try {
      val entry = keyEntry()
      val publicKeyBytes = entry.certificate.publicKey.encoded
      val publicKey = base64Url(publicKeyBytes)
      val keyId = base64Url(MessageDigest.getInstance("SHA-256").digest(publicKeyBytes))
      val result = Arguments.createMap()
      result.putString("installationId", installationId())
      result.putString("keyId", keyId)
      result.putString("publicKey", publicKey)
      result.putBoolean("hardwareBacked", isHardwareBacked(entry))
      result.putMap("context", deviceContext(isHardwareBacked(entry)))
      promise.resolve(result)
    } catch (exception: Exception) {
      promise.reject("DEVICE_IDENTITY_FAILED", "无法建立设备安全身份。", exception)
    }
  }

  @ReactMethod
  fun sign(payload: String, promise: Promise) {
    try {
      require(payload.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) { "签名内容过长。" }
      val signature = Signature.getInstance("SHA256withECDSA")
      signature.initSign(keyEntry().privateKey)
      signature.update(payload.toByteArray(Charsets.UTF_8))
      promise.resolve(base64Url(signature.sign()))
    } catch (exception: Exception) {
      promise.reject("DEVICE_SIGNATURE_FAILED", "设备签名失败。", exception)
    }
  }

  private fun keyEntry(): KeyStore.PrivateKeyEntry {
    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    if (!keyStore.containsAlias(KEY_ALIAS)) {
      val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER)
      val specification = KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
      )
        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setUserAuthenticationRequired(false)
        .build()
      generator.initialize(specification)
      generator.generateKeyPair()
    }
    return keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
      ?: error("设备密钥不存在。")
  }

  @Suppress("DEPRECATION")
  private fun isHardwareBacked(entry: KeyStore.PrivateKeyEntry): Boolean {
    return try {
      val factory = KeyFactory.getInstance(entry.privateKey.algorithm, KEYSTORE_PROVIDER)
      factory.getKeySpec(entry.privateKey, KeyInfo::class.java).isInsideSecureHardware
    } catch (_: Exception) {
      false
    }
  }

  private fun deviceContext(hardwareBacked: Boolean) = Arguments.createMap().apply {
    val metrics = reactContext.resources.displayMetrics
    putString("installationId", installationId())
    putString("manufacturer", Build.MANUFACTURER)
    putString("brand", Build.BRAND)
    putString("model", Build.MODEL)
    putString("deviceName", Build.DEVICE)
    putString("productName", Build.PRODUCT)
    putString("hardwareName", Build.HARDWARE)
    putString("boardName", Build.BOARD)
    putArray("supportedAbis", Arguments.fromList(Build.SUPPORTED_ABIS.toList()))
    putArray("supported32BitAbis", Arguments.fromList(Build.SUPPORTED_32_BIT_ABIS.toList()))
    putArray("supported64BitAbis", Arguments.fromList(Build.SUPPORTED_64_BIT_ABIS.toList()))
    putString("socManufacturer", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else "")
    putString("socModel", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "")
    putInt("cpuCoreCount", Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
    putBoolean("lowRamDevice", activityManager().isLowRamDevice)
    putString("osRelease", Build.VERSION.RELEASE)
    putInt("sdkInt", Build.VERSION.SDK_INT)
    putString("securityPatch", Build.VERSION.SECURITY_PATCH.orEmpty())
    putString("buildFingerprint", Build.FINGERPRINT)
    putInt("screenWidth", metrics.widthPixels)
    putInt("screenHeight", metrics.heightPixels)
    putInt("screenDensity", metrics.densityDpi)
    putInt("memoryMbBucket", memoryMbBucket())
    putInt("storageMbBucket", storageMbBucket())
    putString("localeTag", Locale.getDefault().toLanguageTag())
    putString("timeZone", TimeZone.getDefault().id)
    putString("appVersion", appVersionName())
    putString("appBuild", appVersionCode().toString())
    putString("installerPackage", installerPackage())
    putString("appSignatureSha256", appSignatureSha256())
    putBoolean("hardwareBacked", hardwareBacked)
    putArray("securitySignals", Arguments.fromList(securitySignals().toList()))
    putInt("environmentSchemaVersion", ENVIRONMENT_SCHEMA_VERSION)
    putString("environmentFingerprintHash", environmentFingerprint(hardwareBacked))
  }

  private fun securitySignals(): Set<String> = linkedSetOf<String>().apply {
    if (isProbablyRooted()) add("ROOTED")
    if (isProbablyEmulator()) add("EMULATOR")
    if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) add("DEBUGGER")
    if (isApplicationDebuggable()) add("APP_DEBUGGABLE")
    if (hasHookFramework()) add("HOOK_FRAMEWORK")
    if (Build.TAGS?.contains("test-keys") == true) add("TEST_KEYS")
    if (settingEnabled(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)) add("DEVELOPER_OPTIONS")
    if (settingEnabled(Settings.Global.ADB_ENABLED)) add("ADB_ENABLED")
    add("PLAY_INTEGRITY_UNAVAILABLE")
  }

  private fun isProbablyRooted(): Boolean {
    val rootPaths = listOf(
      "/system/app/Superuser.apk",
      "/system/bin/su",
      "/system/xbin/su",
      "/sbin/su",
      "/vendor/bin/su",
      "/data/local/bin/su",
      "/data/local/xbin/su",
      "/system/bin/busybox",
      "/system/xbin/busybox",
      "/sbin/magisk",
    )
    return rootPaths.any { File(it).exists() }
      || Build.TAGS?.contains("test-keys") == true
      || ROOT_MANAGEMENT_PACKAGES.any(::isPackageInstalled)
  }

  private fun isProbablyEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase(Locale.ROOT)
    val model = Build.MODEL.lowercase(Locale.ROOT)
    val hardware = Build.HARDWARE.lowercase(Locale.ROOT)
    val brand = Build.BRAND.lowercase(Locale.ROOT)
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    val product = Build.PRODUCT.lowercase(Locale.ROOT)
    return fingerprint.startsWith("generic")
      || fingerprint.contains("emulator")
      || model.contains("sdk_gphone")
      || model.contains("emulator")
      || hardware.contains("goldfish")
      || hardware.contains("ranchu")
      || brand.startsWith("generic")
      || manufacturer.contains("genymotion")
      || product.contains("sdk_gphone")
      || product.contains("vbox")
  }

  private fun hasHookFramework(): Boolean {
    return HOOK_FRAMEWORK_CLASSES.any { className ->
      try {
        Class.forName(className)
        true
      } catch (_: ClassNotFoundException) {
        false
      }
    } || HOOK_MANAGEMENT_PACKAGES.any(::isPackageInstalled)
  }

  private fun isApplicationDebuggable(): Boolean {
    return reactContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
  }

  @Suppress("DEPRECATION")
  private fun isPackageInstalled(packageName: String): Boolean {
    return try {
      reactContext.packageManager.getApplicationInfo(packageName, 0)
      true
    } catch (_: PackageManager.NameNotFoundException) {
      false
    }
  }

  private fun settingEnabled(setting: String): Boolean {
    return try {
      Settings.Global.getInt(reactContext.contentResolver, setting, 0) == 1
    } catch (_: Exception) {
      false
    }
  }

  private fun installationId(): String {
    val preferences = reactContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val existing = preferences.getString(INSTALLATION_ID_KEY, null)
    if (!existing.isNullOrBlank()) return existing
    val created = UUID.randomUUID().toString()
    check(preferences.edit().putString(INSTALLATION_ID_KEY, created).commit()) {
      "无法保存安装实例标识。"
    }
    return created
  }

  private fun memoryMbBucket(): Int {
    val manager = activityManager()
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    return bucketMegabytes(info.totalMem)
  }

  private fun storageMbBucket(): Int {
    val stats = StatFs(Environment.getDataDirectory().absolutePath)
    return bucketMegabytes(stats.totalBytes)
  }

  private fun bucketMegabytes(bytes: Long): Int {
    val megabytes = (bytes / (1024L * 1024L)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return ((megabytes + 255) / 256) * 256
  }

  private fun activityManager(): ActivityManager {
    return reactContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
  }

  private fun environmentFingerprint(hardwareBacked: Boolean): String {
    val metrics = reactContext.resources.displayMetrics
    val stableContext = listOf(
      "android-env-v$ENVIRONMENT_SCHEMA_VERSION",
      Build.MANUFACTURER,
      Build.BRAND,
      Build.MODEL,
      Build.DEVICE,
      Build.PRODUCT,
      Build.HARDWARE,
      Build.BOARD,
      Build.FINGERPRINT,
      Build.VERSION.RELEASE,
      Build.VERSION.SDK_INT.toString(),
      Build.VERSION.SECURITY_PATCH.orEmpty(),
      Build.SUPPORTED_ABIS.joinToString(","),
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else "",
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "",
      "${metrics.widthPixels}x${metrics.heightPixels}@${metrics.densityDpi}",
      memoryMbBucket().toString(),
      storageMbBucket().toString(),
      appSignatureSha256(),
      hardwareBacked.toString(),
    ).joinToString("\n")
    return MessageDigest.getInstance("SHA-256")
      .digest(stableContext.toByteArray(Charsets.UTF_8))
      .joinToString("") { byte -> "%02x".format(byte) }
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

  @Suppress("DEPRECATION")
  private fun installerPackage(): String {
    val packageManager = reactContext.packageManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      packageManager.getInstallSourceInfo(reactContext.packageName).installingPackageName.orEmpty()
    } else {
      packageManager.getInstallerPackageName(reactContext.packageName).orEmpty()
    }
  }

  @Suppress("DEPRECATION")
  private fun appSignatureSha256(): String {
    val packageManager = reactContext.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      packageManager.getPackageInfo(reactContext.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    } else {
      packageManager.getPackageInfo(reactContext.packageName, PackageManager.GET_SIGNATURES)
    }
    val certificate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
    } else {
      info.signatures?.firstOrNull()?.toByteArray()
    } ?: return ""
    return MessageDigest.getInstance("SHA-256")
      .digest(certificate)
      .joinToString("") { byte -> "%02x".format(byte) }
  }

  private fun base64Url(bytes: ByteArray): String {
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
  }

  companion object {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "e_report_device_proof_v1"
    private const val PREFERENCES_NAME = "e_report_device_security"
    private const val INSTALLATION_ID_KEY = "installation_id"
    private const val MAX_PAYLOAD_BYTES = 8192
    private const val ENVIRONMENT_SCHEMA_VERSION = 2
    private val ROOT_MANAGEMENT_PACKAGES = setOf(
      "com.topjohnwu.magisk",
      "eu.chainfire.supersu",
      "com.noshufou.android.su",
    )
    private val HOOK_MANAGEMENT_PACKAGES = setOf(
      "de.robv.android.xposed.installer",
      "org.meowcat.edxposed.manager",
      "com.saurik.substrate",
      "me.weishu.exp",
    )
    private val HOOK_FRAMEWORK_CLASSES = setOf(
      "de.robv.android.xposed.XposedBridge",
      "com.saurik.substrate.MS\$2",
    )
  }
}
