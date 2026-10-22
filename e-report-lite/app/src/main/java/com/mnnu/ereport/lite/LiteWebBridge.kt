package com.mnnu.ereport.lite

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LiteWebBridge(private val activity: Activity, private val origin: String, exportDirectory: File,
    private val onExport: (ExportSession.Completed) -> Unit) : AutoCloseable {
    private val exports = ExportSession(exportDirectory)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    fun attach(webView: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        WebViewCompat.addWebMessageListener(webView, "eReportLite", setOf(origin)) { _, message, sourceOrigin, mainFrame, reply ->
            if (closed) return@addWebMessageListener
            if (!mainFrame || !EndpointPolicy.isTrusted(sourceOrigin.toString(), origin)) return@addWebMessageListener
            val text = message.data ?: return@addWebMessageListener
            if (text.length > 280_000) return@addWebMessageListener
            executor.execute {
                var id = ""
                val response = try {
                    val request = JSONObject(text)
                    id = request.getString("id")
                    require(id.matches(Regex("[a-zA-Z0-9-]{1,80}"))) { "请求标识无效" }
                    val payload = request.optJSONObject("payload") ?: JSONObject()
                    val action = request.getString("action")
                    if (action == "share.text" || action == "clipboard.write") {
                        val requestId = id
                        main.post {
                            if (closed) return@post
                            val response = try {
                                executeUiAction(action, payload)
                                JSONObject().put("id", requestId).put("ok", true).put("payload", JSONObject())
                            } catch (exception: Exception) {
                                JSONObject().put("id", requestId).put("ok", false).put("error", exception.message ?: "操作失败")
                            }
                            reply.postMessage(response.toString())
                        }
                        return@execute
                    }
                    val result = when (action) {
                        "info" -> JSONObject().put("edition", "LITE").put("version", BuildConfig.VERSION_NAME)
                            .put("versionCode", BuildConfig.VERSION_CODE).put("runtimeVersion", BuildConfig.RUNTIME_VERSION)
                        "download.begin" -> JSONObject().put("transferId", exports.begin(payload.getString("name"),
                            payload.getString("contentType"), payload.getLong("size"), payload.getString("sha256")))
                        "download.chunk" -> {
                            exports.append(payload.getString("transferId"), payload.getLong("offset"),
                                Base64.decode(payload.getString("base64"), Base64.NO_WRAP))
                            JSONObject()
                        }
                        "download.complete" -> {
                            val completed = exports.complete(payload.getString("transferId"))
                            main.post { if (!closed) onExport(completed) else completed.file.delete() }
                            JSONObject()
                        }
                        "download.cancel" -> { exports.close(); JSONObject() }
                        else -> throw IllegalArgumentException("不支持的客户端操作")
                    }
                    JSONObject().put("id", id).put("ok", true).put("payload", result)
                } catch (exception: Exception) {
                    JSONObject().put("id", id).put("ok", false).put("error", exception.message ?: "客户端操作失败")
                }
                main.post { if (!closed) reply.postMessage(response.toString()) }
            }
        }
        return true
    }

    private fun executeUiAction(action: String, payload: JSONObject) {
        check(!activity.isFinishing) { "应用未处于前台" }
        val text = payload.getString("text")
        require(text.length in 1..65_536) { "文本为空或过长" }
        if (action == "share.text") {
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, payload.optString("title").take(200))
                .putExtra(Intent.EXTRA_TEXT, text)
            activity.startActivity(Intent.createChooser(intent, "分享工单位置"))
        } else {
            val clip = ClipData.newPlainText("e-report", text)
            if (android.os.Build.VERSION.SDK_INT >= 33 && payload.optBoolean("sensitive")) {
                clip.description.extras = android.os.PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            }
            (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        executor.execute { exports.close() }
        executor.shutdown()
    }
}
