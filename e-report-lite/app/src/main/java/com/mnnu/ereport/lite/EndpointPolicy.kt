package com.mnnu.ereport.lite

import java.net.URI

object EndpointPolicy {
    const val PRODUCTION = "https://e-report.fantong.eu.org:60443"
    const val TEST = "https://e-report-test.fantong.eu.org:60443"

    fun normalizeOrigin(value: String): String {
        require(value.length <= 512) { "地址不能超过 512 个字符" }
        val uri = try { URI(value.trim()) } catch (exception: Exception) {
            throw IllegalArgumentException("请输入有效的 HTTPS 网页地址", exception)
        }
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()) { "网页地址必须使用 HTTPS" }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) { "地址不能包含账号、查询参数或片段" }
        require(uri.path.isNullOrEmpty() || uri.path == "/") { "请输入站点根地址，无需填写 /api 或页面路径" }
        require(uri.port == -1 || uri.port in 1..65535) { "端口无效" }
        return URI("https", null, uri.host.lowercase(), if (uri.port == 443) -1 else uri.port, null, null, null).toASCIIString()
    }

    fun isTrusted(url: String, origin: String): Boolean = try {
        val uri = URI(url)
        val root = URI(origin)
        uri.scheme == "https" && uri.rawUserInfo == null && uri.host.equals(root.host, true)
            && effectivePort(uri) == effectivePort(root)
    } catch (_: Exception) { false }

    fun isExternalLink(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme in setOf("https", "mailto", "tel", "geo") && uri.rawUserInfo == null
    } catch (_: Exception) { false }

    fun safeFileName(name: String): String = name.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_").take(160).ifBlank { "download" }

    private fun effectivePort(uri: URI) = if (uri.port == -1) 443 else uri.port
}
