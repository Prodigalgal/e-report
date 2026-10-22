package com.mnnu.ereport.lite

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

class LiteAppUpdater(private val activity: Activity) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private var busy = false
    private var pendingInstall: File? = null
    private data class Release(val code: Int, val version: String, val url: String, val size: Long, val sha256: String, val force: Boolean)

    fun check(origin: String, silent: Boolean = false) {
        if (busy) return
        busy = true
        executor.execute {
            try {
                val response = connection("$origin/api/app-updates/latest?edition=LITE&platform=ANDROID&channel=stable&currentVersionCode=${BuildConfig.VERSION_CODE}&runtimeVersion=${BuildConfig.RUNTIME_VERSION}")
                val json = try {
                    require(response.contentLengthLong <= 128 * 1024) { "更新响应过大" }
                    response.inputStream.use { input ->
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(4096)
                        while (true) {
                            val count = input.read(chunk)
                            if (count < 0) break
                            require(buffer.size() + count <= 128 * 1024) { "更新响应过大" }
                            buffer.write(chunk, 0, count)
                        }
                        JSONObject(buffer.toString("UTF-8"))
                    }
                } finally { response.disconnect() }
                require(json.getString("edition") == "LITE") { "Lite 仅支持 Lite 升级；切换原生版请先卸载，再下载安装原生版" }
                if (!json.optBoolean("updateAvailable")) {
                    if (!silent) showMessage("当前 Lite 壳已是最新版本，业务页面会自动保持更新。")
                } else {
                    val release = Release(json.getInt("versionCode"), json.getString("versionName"), json.getString("apkUrl"),
                        json.getLong("apkSize"), json.getString("sha256"), json.optBoolean("forceUpdate"))
                    require(release.code > BuildConfig.VERSION_CODE && release.size in 1..ExportSession.MAX_BYTES
                        && release.sha256.matches(Regex("[a-f0-9]{64}")) && EndpointPolicy.isTrusted(release.url, origin)) { "更新信息无效" }
                    activity.runOnUiThread {
                        if (!activity.isFinishing) AlertDialog.Builder(activity).setTitle("Lite 壳更新 ${release.version}")
                            .setMessage(json.optString("releaseNotes").ifBlank { "本次更新需要安装新的 Lite APK。" })
                            .setPositiveButton("下载并安装") { _, _ -> download(release) }
                            .apply { if (!release.force) setNegativeButton("稍后", null) }
                            .setCancelable(!release.force).show()
                    }
                }
            } catch (exception: Exception) {
                if (!silent) showMessage("检查更新失败：${exception.message ?: "请检查网络"}")
            } finally { activity.runOnUiThread { busy = false } }
        }
    }

    private fun download(release: Release) {
        if (busy) return
        busy = true
        val dialog = AlertDialog.Builder(activity).setTitle("正在下载 Lite ${release.version}")
            .setMessage("下载后将校验文件完整性和应用签名，请稍候。")
            .setCancelable(false).show()
        executor.execute {
            val directory = File(activity.cacheDir, "updates")
            val target = File(directory, "lite-${release.code}.apk")
            try {
                check(directory.isDirectory || directory.mkdirs()) { "无法创建下载目录" }
                val response = connection(release.url)
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                try {
                    response.inputStream.use { input -> target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            written += count
                            require(written <= release.size) { "更新包大小不一致" }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    } }
                } finally { response.disconnect() }
                require(written == release.size && digest.digest().joinToString("") { "%02x".format(it) } == release.sha256) { "更新包校验失败" }
                verifyPackage(target, release.code)
                activity.runOnUiThread { pendingInstall = target; installPending() }
            } catch (exception: Exception) {
                target.delete()
                showMessage("更新失败：${exception.message ?: "请稍后重试"}")
            } finally { activity.runOnUiThread { dialog.dismiss(); busy = false } }
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File, expectedCode: Int) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = activity.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("无法读取安装包")
        val installed = activity.packageManager.getPackageInfo(activity.packageName, flags)
        require(archive.packageName == activity.packageName) { "安装包不属于 Lite 应用" }
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(code == expectedCode.toLong()) { "安装包版本不一致" }
        val archiveSigners = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo!!.apkContentsSigners else archive.signatures!!
        val installedSigners = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo!!.apkContentsSigners else installed.signatures!!
        require(archiveSigners.map { it.toCharsString() }.toSet() == installedSigners.map { it.toCharsString() }.toSet()) { "安装包签名不一致" }
    }

    fun installPending() {
        val file = pendingInstall ?: return
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity).setTitle("允许安装 Lite 更新")
                .setMessage("请在系统设置中允许本应用安装更新，返回后会继续打开安装器。")
                .setNegativeButton("稍后", null).setPositiveButton("打开设置") { _, _ ->
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                }.show()
            return
        }
        pendingInstall = null
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: android.content.ActivityNotFoundException) { showMessage("未找到系统安装器，请通过系统文件管理器安装更新。") }
    }

    private fun connection(url: String, depth: Int = 0): HttpURLConnection {
        require(depth <= 5 && URL(url).protocol == "https") { "更新地址或跳转无效" }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "e-report-lite/${BuildConfig.VERSION_NAME}")
        return when (connection.responseCode) {
            in 200..299 -> connection
            in 300..399 -> {
                val location = connection.getHeaderField("Location") ?: error("更新跳转缺少地址")
                val next = URL(URL(url), location).toString()
                connection.disconnect()
                connection(next, depth + 1)
            }
            else -> { val status = connection.responseCode; connection.disconnect(); error("服务返回 $status") }
        }
    }

    private fun showMessage(message: String) = activity.runOnUiThread {
        if (!activity.isFinishing) Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
    override fun close() { executor.shutdownNow() }
}
