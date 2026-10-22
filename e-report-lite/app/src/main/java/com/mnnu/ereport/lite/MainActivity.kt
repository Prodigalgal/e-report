package com.mnnu.ereport.lite

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.*
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object { private const val CHOOSE_FILE = 10; private const val SAVE_FILE = 11; private const val LOCATION = 12 }
    private lateinit var settings: LiteSettings
    private lateinit var updater: LiteAppUpdater
    private lateinit var content: FrameLayout
    private lateinit var errorPanel: View
    private lateinit var loading: ProgressBar
    private var webView: WebView? = null
    private var bridge: LiteWebBridge? = null
    private var failedLoad = false
    private var fileSelection: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null
    private val locationRequests = LocationPermissionRequests()
    private var webDialog: AlertDialog? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var previousSystemUi = 0
    private var pendingExport: ExportSession.Completed? = null
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = LiteSettings(this)
        updater = LiteAppUpdater(this)
        setContentView(R.layout.activity_main)
        content = findViewById(R.id.content)
        errorPanel = findViewById(R.id.error_panel)
        loading = findViewById(R.id.loading)
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val padding = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        findViewById<View>(R.id.settings).setOnClickListener { showSettings() }
        findViewById<View>(R.id.error_settings).setOnClickListener { showSettings() }
        findViewById<View>(R.id.retry).setOnClickListener { openSite() }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { navigateBack() }
        }
        openSite(savedInstanceState?.getString("page"))
        cleanupExports()
    }

    @SuppressLint("SetJavaScriptEnabled", "MissingOnRenderProcessGone")
    private fun openSite(page: String? = null) {
        val origin = settings.origin()
        findViewById<TextView>(R.id.environment).text = if (settings.environment == "test") "测试环境" else "生产环境"
        failedLoad = false
        errorPanel.visibility = View.GONE
        webView?.let { content.removeView(it); it.destroy() }
        bridge?.close()
        val browser = WebView(this)
        webView = browser
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(true)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString += " EReportLite/${BuildConfig.VERSION_NAME}"
            mediaPlaybackRequiresUserGesture = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (EndpointPolicy.isTrusted(url, origin)) return false
                if (request.isForMainFrame && request.hasGesture() && EndpointPolicy.isExternalLink(url)) openExternal(request.url)
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!EndpointPolicy.isTrusted(url.orEmpty(), origin)) { view.stopLoading(); return }
                failedLoad = false
                errorPanel.visibility = View.GONE
                loading.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView, url: String?) {
                loading.visibility = View.INVISIBLE
                CookieManager.getInstance().flush()
                if (!failedLoad) updater.check(origin, silent = true)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showError("请检查网络连接或服务地址，然后重试。")
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame) showError("服务暂时不可用（${errorResponse.statusCode}），请稍后重试。")
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                showError("站点证书校验失败，请检查服务地址或联系管理员。")
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                content.removeView(view)
                view.destroy()
                webView = null
                showError("页面进程已退出，重新连接即可恢复。")
                return true
            }
        }
        browser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) { loading.progress = progress }
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, parameters: FileChooserParams): Boolean {
                if (!EndpointPolicy.isTrusted(view.url.orEmpty(), origin)) return false
                fileSelection?.onReceiveValue(null)
                fileSelection = callback
                val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                val types = parameters.acceptTypes.flatMap { it.split(',') }.map { it.trim().lowercase() }
                    .filter { it.length < 100 && it.matches(Regex("[a-z0-9.+*-]+/[a-z0-9.+*-]+")) }.distinct().toTypedArray()
                picker.type = types.singleOrNull() ?: "*/*"
                if (types.size > 1) picker.putExtra(Intent.EXTRA_MIME_TYPES, types)
                picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, parameters.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                val chooser = Intent.createChooser(picker, "选择现场附件")
                val captureIntents = mutableListOf<Intent>()
                if (types.isEmpty() || types.any { it == "*/*" || it.startsWith("image/") }) {
                    val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    if (capture.resolveActivity(packageManager) != null) {
                        val directory = File(cacheDir, "camera").apply { mkdirs() }
                        cameraUri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", File(directory, "${UUID.randomUUID()}.jpg"))
                        capture.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        captureIntents.add(capture)
                    }
                }
                if (types.isEmpty() || types.any { it == "*/*" || it.startsWith("video/") }) {
                    val video = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                    if (video.resolveActivity(packageManager) != null) {
                        captureIntents.add(video)
                    }
                }
                if (captureIntents.isNotEmpty()) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, captureIntents.toTypedArray())
                return try { startActivityForResult(chooser, CHOOSE_FILE); true }
                catch (_: ActivityNotFoundException) { callback.onReceiveValue(null); fileSelection = null; true }
            }
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                showWebDialog(url, message, result, false); return true
            }
            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                showWebDialog(url, message, result, true); return true
            }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreenView != null) { callback.onCustomViewHidden(); return }
                fullscreenView = view; fullscreenCallback = callback
                previousSystemUi = window.decorView.systemUiVisibility
                view.setBackgroundColor(android.graphics.Color.BLACK)
                (window.decorView as ViewGroup).addView(view, ViewGroup.LayoutParams(-1, -1))
                if (android.os.Build.VERSION.SDK_INT >= 30) window.insetsController?.hide(WindowInsets.Type.systemBars())
                else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                }
            }
            override fun onHideCustomView() { hideFullscreen() }
            override fun onGeolocationPermissionsShowPrompt(requestOrigin: String, callback: GeolocationPermissions.Callback) {
                if (!EndpointPolicy.isTrusted(requestOrigin, origin)) { callback.invoke(requestOrigin, false, false); return }
                val shouldRequest = locationRequests.enqueue { granted ->
                    callback.invoke(requestOrigin, granted && EndpointPolicy.isTrusted(requestOrigin, settings.origin()), false)
                }
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) finishLocationRequest(true)
                else if (shouldRequest) requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), LOCATION)
            }
        }
        browser.setDownloadListener { url, _, _, _, _ ->
            if (EndpointPolicy.isExternalLink(url)) openExternal(Uri.parse(url))
            else Toast.makeText(this, "请使用附件页面的下载或保存按钮。", Toast.LENGTH_LONG).show()
        }
        bridge = LiteWebBridge(this, origin, File(cacheDir, "exports"), ::saveExport).also {
            if (!it.attach(browser)) showError("系统 WebView 版本过低，请更新 Android System WebView 后重试。")
        }
        content.addView(browser, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        browser.loadUrl(if (page != null && EndpointPolicy.isTrusted(page, origin)) page else "$origin/login")
    }

    private fun showError(message: String) {
        failedLoad = true
        loading.visibility = View.INVISIBLE
        errorPanel.visibility = View.VISIBLE
        findViewById<TextView>(R.id.error_message).text = message
    }

    private fun showWebDialog(url: String, message: String, result: JsResult, confirm: Boolean) {
        if (!EndpointPolicy.isTrusted(url, settings.origin()) || isFinishing) { result.cancel(); return }
        webDialog?.cancel()
        var resolved = false
        fun complete(accepted: Boolean) {
            if (resolved) return
            resolved = true
            if (accepted) result.confirm() else result.cancel()
        }
        val dialog = AlertDialog.Builder(this).setTitle(if (confirm) "确认操作" else "提示")
            .setMessage(message.take(4000)).setPositiveButton("确定") { _, _ -> complete(true) }
            .apply { if (confirm) setNegativeButton("取消") { _, _ -> complete(false) } }.create()
        dialog.setOnDismissListener { complete(false); if (webDialog === dialog) webDialog = null }
        webDialog = dialog
        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun hideFullscreen() {
        val view = fullscreenView ?: return
        (window.decorView as ViewGroup).removeView(view)
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden(); fullscreenCallback = null
        if (android.os.Build.VERSION.SDK_INT >= 30) window.insetsController?.show(WindowInsets.Type.systemBars())
        else window.decorView.systemUiVisibility = previousSystemUi
    }

    private fun showSettings() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        body.addView(TextView(this).apply { text = getString(R.string.version_summary, BuildConfig.VERSION_NAME); setTextColor(0xff64748b.toInt()); textSize = 14f })
        val group = RadioGroup(this)
        val production = RadioButton(this).apply { id = View.generateViewId(); text = "生产环境"; minHeight = dp(48) }
        val test = RadioButton(this).apply { id = View.generateViewId(); text = "测试环境"; minHeight = dp(48) }
        group.addView(production); group.addView(test)
        group.check(if (settings.environment == "test") test.id else production.id)
        body.addView(group)
        fun field(label: String, environment: String): EditText {
            body.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(0xff334155.toInt()) })
            return EditText(this).apply {
                setText(settings.origin(environment)); textSize = 16f; minHeight = dp(56)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                isSingleLine = false; maxLines = 3
                body.addView(this, LinearLayout.LayoutParams(-1, -2))
            }
        }
        val productionUrl = field("生产站点 HTTPS 地址", "production")
        val testUrl = field("测试站点 HTTPS 地址", "test")
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun action(title: String, onClick: () -> Unit) = Button(this).apply {
            text = title; minHeight = dp(48); setTextColor(0xff1d4ed8.toInt()); backgroundTintList = android.content.res.ColorStateList.valueOf(0xffdbeafe.toInt())
            setOnClickListener { onClick() }; actions.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        }
        action("恢复默认地址") { productionUrl.setText(EndpointPolicy.PRODUCTION); testUrl.setText(EndpointPolicy.TEST) }
        action("检查壳更新") { updater.check(settings.origin()) }
        body.addView(actions)
        val scroll = ScrollView(this).apply { addView(body) }
        val dialog = AlertDialog.Builder(this).setTitle("环境与应用设置").setView(scroll)
            .setNegativeButton("取消", null).setNeutralButton("刷新页面") { _, _ -> webView?.reload() }
            .setPositiveButton("保存并连接", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val next = if (group.checkedRadioButtonId == test.id) "test" else "production"
                    val productionOrigin = EndpointPolicy.normalizeOrigin(productionUrl.text.toString())
                    val testOrigin = EndpointPolicy.normalizeOrigin(testUrl.text.toString())
                    val nextOrigin = if (next == "test") testOrigin else productionOrigin
                    if (next != settings.environment || nextOrigin != settings.origin()) {
                        AlertDialog.Builder(this).setTitle(if (next == "test") "连接测试环境" else "连接生产环境")
                            .setMessage("切换后将清除当前登录和页面缓存，需要重新登录。${if (next == "production") "生产环境会操作真实业务数据。" else "测试数据与生产环境隔离。"}")
                            .setNegativeButton("取消", null).setPositiveButton("确认切换") { _, _ ->
                                clearSession {
                                    settings.save(next, productionOrigin, testOrigin)
                                    dialog.dismiss(); openSite()
                                }
                            }.show()
                    } else { settings.save(next, productionOrigin, testOrigin); dialog.dismiss() }
                } catch (exception: IllegalArgumentException) { productionUrl.error = exception.message; testUrl.error = exception.message }
            }
        }
        dialog.show()
    }

    private fun clearSession(onCleared: () -> Unit) {
        webDialog?.cancel(); webDialog = null; hideFullscreen()
        bridge?.close(); bridge = null
        fileSelection?.onReceiveValue(null); fileSelection = null
        finishLocationRequest(false)
        webView?.stopLoading()
        webView?.evaluateJavascript("localStorage.clear();sessionStorage.clear();", null)
        WebStorage.getInstance().deleteAllData()
        GeolocationPermissions.getInstance().clearAll()
        webView?.clearCache(true); webView?.clearHistory(); webView?.clearFormData()
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush(); onCleared() }
    }

    private fun saveExport(completed: ExportSession.Completed) {
        if (pendingExport != null) { completed.file.delete(); Toast.makeText(this, "请先完成当前文件保存。", Toast.LENGTH_LONG).show(); return }
        pendingExport = completed
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(completed.contentType).putExtra(Intent.EXTRA_TITLE, completed.name), SAVE_FILE)
        } catch (_: ActivityNotFoundException) {
            pendingExport = null; completed.file.delete()
            Toast.makeText(this, "未找到系统文件保存器。", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Activity result API required without an AndroidX Activity dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Intent?) {
        super.onActivityResult(requestCode, resultCode, result)
        if (requestCode == CHOOSE_FILE) {
            val selected = if (resultCode != RESULT_OK) null else result?.clipData?.let { clip ->
                (0 until clip.itemCount).map { clip.getItemAt(it).uri }.toTypedArray()
            } ?: result?.data?.let { arrayOf(it) } ?: cameraUri?.let { arrayOf(it) }
            fileSelection?.onReceiveValue(selected?.filter { it.scheme == "content" }?.toTypedArray())
            fileSelection = null; cameraUri = null
        }
        if (requestCode == SAVE_FILE) {
            val exported = pendingExport ?: return
            pendingExport = null
            val uri = result?.data?.takeIf { resultCode == RESULT_OK && it.scheme == "content" }
            io.execute {
                try {
                    if (uri != null) contentResolver.openOutputStream(uri, "wt").use { output ->
                        check(output != null) { "无法打开保存位置" }
                        exported.file.inputStream().use { it.copyTo(output) }
                        runOnUiThread { Toast.makeText(this, "文件已保存", Toast.LENGTH_SHORT).show() }
                    }
                } catch (_: Exception) {
                    runOnUiThread { Toast.makeText(this, "保存失败，请检查存储空间和权限后重试。", Toast.LENGTH_LONG).show() }
                } finally { exported.file.delete() }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION) finishLocationRequest(grantResults.any { it == PackageManager.PERMISSION_GRANTED })
    }
    private fun finishLocationRequest(granted: Boolean) {
        locationRequests.complete(granted)
    }
    private fun openExternal(uri: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "未找到可打开此链接的应用。", Toast.LENGTH_SHORT).show() }
    }
    private fun cleanupExports() = io.execute {
        for (folder in listOf("exports", "camera", "updates")) {
            File(cacheDir, folder).listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach { it.delete() }
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    // Android 13+ uses the OnBackInvoked callback; this override serves Android 8–12.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Platform back handling") override fun onBackPressed() {
        navigateBack()
    }
    private fun navigateBack() {
        if (fullscreenView != null) { hideFullscreen(); return }
        val browser = webView
        if (browser?.canGoBack() == true && !failedLoad) browser.goBack() else finish()
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putString("page", webView?.url) }
    // Permission dialogs pause the Activity while WebView geolocation is still awaiting their result.
    override fun onStart() { super.onStart(); webView?.onResume() }
    override fun onStop() { webView?.onPause(); super.onStop() }
    override fun onResume() { super.onResume(); if (::updater.isInitialized) updater.installPending() }
    override fun onPause() { CookieManager.getInstance().flush(); super.onPause() }
    override fun onDestroy() {
        webDialog?.cancel(); webDialog = null; hideFullscreen()
        fileSelection?.onReceiveValue(null); finishLocationRequest(false)
        bridge?.close(); webView?.destroy(); updater.close(); io.shutdown()
        super.onDestroy()
    }
}
