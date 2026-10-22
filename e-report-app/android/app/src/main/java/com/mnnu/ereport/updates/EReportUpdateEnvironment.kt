package com.mnnu.ereport.updates

import android.app.Application
import android.content.Context
import android.net.Uri
import expo.modules.updates.UpdatesController
import java.net.URI

object EReportUpdateEnvironment {
    private const val PREFERENCES = "e_report_ota_environment_v1"
    private const val DEFAULT_API = "https://e-report.fantong.eu.org:60443/api"
    var startupEnvironment = "production"
        private set
    var startupApiBaseUrl = DEFAULT_API
        private set

    fun configure(application: Application) {
        val preferences = application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val storedEnvironment = preferences.getString("environment", "production").orEmpty()
        val storedApi = preferences.getString("apiBaseUrl", DEFAULT_API).orEmpty()
        startupEnvironment = if (storedEnvironment == "test") "test" else "production"
        startupApiBaseUrl = try { validateApiUrl(storedApi) } catch (_: IllegalArgumentException) { DEFAULT_API }
        UpdatesController.overrideConfiguration(application, mapOf(
            "updateUrl" to Uri.parse("$startupApiBaseUrl/app-updates/ota"),
            "scopeKey" to "e-report:$startupEnvironment:$startupApiBaseUrl",
            "requestHeaders" to mapOf("e-report-environment" to startupEnvironment)
        ))
    }

    fun save(context: Context, environment: String, apiBaseUrl: String): Boolean {
        require(environment == "production" || environment == "test") { "服务环境无效" }
        val normalized = validateApiUrl(apiBaseUrl)
        check(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString("environment", environment).putString("apiBaseUrl", normalized).commit()) { "无法保存原生更新环境" }
        return startupEnvironment != environment || startupApiBaseUrl != normalized
    }

    private fun validateApiUrl(value: String): String {
        require(value.length <= 512) { "API 地址过长" }
        val uri = URI(value.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
            && uri.rawQuery == null && uri.rawFragment == null && !uri.path.contains("..")) { "API 地址必须为不含凭据的 HTTPS 地址" }
        return uri.toASCIIString().trimEnd('/')
    }
}
