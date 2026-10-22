package com.mnnu.ereport.lite

import org.junit.Assert.*
import org.junit.Test

class LocationPermissionRequestsTest {
    @Test fun concurrentRequestsShareOneSystemPromptAndCompleteOnce() {
        val requests = LocationPermissionRequests()
        val results = mutableListOf<Boolean>()
        assertTrue(requests.enqueue(results::add))
        assertFalse(requests.enqueue(results::add))
        requests.complete(false)
        requests.complete(true)
        assertEquals(listOf(false, false), results)
        assertTrue(requests.enqueue(results::add))
        requests.complete(true)
        assertEquals(listOf(false, false, true), results)
    }

    @Test fun reentrantRequestDoesNotResolveOrEraseTheNextCallback() {
        val requests = LocationPermissionRequests()
        val results = mutableListOf<String>()
        requests.enqueue {
            results.add("first:$it")
            assertTrue(requests.enqueue { next -> results.add("next:$next") })
        }
        requests.complete(false)
        assertEquals(listOf("first:false"), results)
        requests.complete(true)
        assertEquals(listOf("first:false", "next:true"), results)
    }
}
