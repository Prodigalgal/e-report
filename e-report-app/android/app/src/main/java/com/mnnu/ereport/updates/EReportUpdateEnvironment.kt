package com.mnnu.ereport.updates

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import expo.modules.updates.UpdatesController
import expo.modules.updates.UpdatesUtils
import java.net.URI

object EReportUpdateEnvironment {
    private const val PREFERENCES = "e_report_ota_environment_v1"
    private const val DEFAULT_API = "https://e-report.fantong.eu.org:60443/api"
    private const val CACHE_SCOPE_KEY = "cacheScopeKey"
    private const val UPDATES_DATABASE = "updates.db"
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
        val scopeKey = "e-report:$startupEnvironment:$startupApiBaseUrl"
        try {
            resetCacheForScope(application, preferences, scopeKey)
        } catch (exception: Exception) {
            Log.e("EReportUpdates", "Could not isolate OTA cache; launching the embedded application with OTA disabled for this process", exception)
            UpdatesController.overrideConfiguration(application, mapOf("enabled" to false))
            return
        }
        UpdatesController.overrideConfiguration(application, mapOf(
            "updateUrl" to Uri.parse("$startupApiBaseUrl/app-updates/ota"),
            "scopeKey" to scopeKey,
            "requestHeaders" to mapOf("e-report-environment" to startupEnvironment)
        ))
    }

    private fun resetCacheForScope(application: Application, preferences: SharedPreferences, scopeKey: String) {
        if (preferences.getString(CACHE_SCOPE_KEY, null) == scopeKey) return
        // SDK 57 keys embedded updates by UUID; changing its scope does not update its stored URL or headers.
        // Reset only the OTA cache before the controller opens it, preserving authentication and business files.
        val directory = UpdatesUtils.getOrCreateUpdatesDirectory(application).canonicalFile
        check(directory.parentFile == application.filesDir.canonicalFile) { "Unexpected OTA cache directory" }
        val database = application.getDatabasePath(UPDATES_DATABASE)
        if (database.exists()) check(application.deleteDatabase(UPDATES_DATABASE)) { "Could not clear OTA database" }
        check(directory.deleteRecursively()) { "Could not clear OTA asset cache" }
        check(preferences.edit().putString(CACHE_SCOPE_KEY, scopeKey).commit()) { "Could not persist OTA cache scope" }
        Log.i("EReportUpdates", "OTA cache initialized for the selected environment")
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
