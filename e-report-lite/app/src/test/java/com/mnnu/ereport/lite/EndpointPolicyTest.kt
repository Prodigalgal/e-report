package com.mnnu.ereport.lite

import org.junit.Assert.*
import org.junit.Test

class EndpointPolicyTest {
    @Test fun validatesHttpsRootWithoutCredentialsOrPaths() {
        assertEquals("https://example.org", EndpointPolicy.normalizeOrigin(" https://EXAMPLE.org:443/ "))
        for (value in listOf("http://example.org", "https://a:b@example.org", "https://example.org/api", "https://example.org?x=1", "javascript:alert(1)")) {
            assertThrows(IllegalArgumentException::class.java) { EndpointPolicy.normalizeOrigin(value) }
        }
    }
    @Test fun restrictsBridgeToExactOriginAndPort() {
        val origin = "https://example.org:60443"
        assertTrue(EndpointPolicy.isTrusted("$origin/work-orders/123", origin))
        for (url in listOf("https://example.org", "https://example.org.evil.test:60443", "https://a@example.org:60443", "http://example.org:60443")) {
            assertFalse(EndpointPolicy.isTrusted(url, origin))
        }
    }
    @Test fun rejectsIntentAndScriptNavigationAndSanitizesExports() {
        assertFalse(EndpointPolicy.isExternalLink("intent://settings"))
        assertFalse(EndpointPolicy.isExternalLink("javascript:alert(1)"))
        assertEquals("工单_.pdf", EndpointPolicy.safeFileName("../../工单?.pdf"))
    }
}
