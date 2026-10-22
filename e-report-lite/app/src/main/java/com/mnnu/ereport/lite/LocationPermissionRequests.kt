package com.mnnu.ereport.lite

internal class LocationPermissionRequests {
    private val callbacks = mutableListOf<(Boolean) -> Unit>()

    fun enqueue(callback: (Boolean) -> Unit): Boolean {
        val firstRequest = callbacks.isEmpty()
        callbacks.add(callback)
        return firstRequest
    }

    fun complete(granted: Boolean) {
        val completed = callbacks.toList()
        // WebView can synchronously request permission again from a result callback.
        callbacks.clear()
        completed.forEach { it(granted) }
    }
}
