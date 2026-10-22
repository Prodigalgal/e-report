package com.mnnu.ereport.updates

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.io.File

class AppPackageVerificationModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
    override fun getName() = "EReportAppPackage"

    @ReactMethod
    @Suppress("DEPRECATION")
    fun verify(uri: String, expectedCode: Double, promise: Promise) {
        try {
            val parsed = Uri.parse(uri)
            require(parsed.scheme == "file") { "安装包地址无效" }
            val file = File(requireNotNull(parsed.path)).canonicalFile
            require(file.isFile && file.path.startsWith(reactApplicationContext.cacheDir.canonicalPath + File.separator)) { "安装包不在应用下载目录" }
            val manager = reactApplicationContext.packageManager
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val archive = manager.getPackageArchiveInfo(file.path, flags) ?: error("无法读取安装包")
            val installed = manager.getPackageInfo(reactApplicationContext.packageName, flags)
            require(archive.packageName == installed.packageName) { "仅允许升级当前原生版；切换 Lite 请先卸载，再下载安装 Lite" }
            val archiveCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
            val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
            require(expectedCode == archiveCode.toDouble() && archiveCode > installedCode) { "安装包版本不一致或未递增" }
            val archiveSigners = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo!!.apkContentsSigners else archive.signatures!!
            val installedSigners = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo!!.apkContentsSigners else installed.signatures!!
            require(archiveSigners.map { it.toCharsString() }.toSet() == installedSigners.map { it.toCharsString() }.toSet()) { "安装包签名与当前原生版不一致" }
            promise.resolve(null)
        } catch (exception: Exception) {
            promise.reject("APK_IDENTITY_MISMATCH", exception.message, exception)
        }
    }
}
