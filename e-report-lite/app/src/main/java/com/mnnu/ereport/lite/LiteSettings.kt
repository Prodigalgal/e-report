package com.mnnu.ereport.lite

import android.content.Context

class LiteSettings(context: Context) {
    private val preferences = context.getSharedPreferences("e_report_lite_settings_v1", Context.MODE_PRIVATE)
    val environment: String get() = preferences.getString("environment", "production")!!.let { if (it == "test") it else "production" }
    fun origin(environment: String = this.environment): String {
        val fallback = if (environment == "test") EndpointPolicy.TEST else EndpointPolicy.PRODUCTION
        return try { EndpointPolicy.normalizeOrigin(preferences.getString("origin_$environment", fallback)!!) }
        catch (_: IllegalArgumentException) { fallback }
    }
    fun save(environment: String, production: String, test: String) {
        require(environment in setOf("production", "test")) { "环境无效" }
        val productionOrigin = EndpointPolicy.normalizeOrigin(production)
        val testOrigin = EndpointPolicy.normalizeOrigin(test)
        check(preferences.edit().putString("environment", environment).putString("origin_production", productionOrigin)
            .putString("origin_test", testOrigin).commit()) { "无法保存环境设置" }
    }
}
