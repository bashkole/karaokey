package nl.ikomex.karaokey.server

import java.util.concurrent.ConcurrentHashMap

class RateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long
) {
    private val hits = ConcurrentHashMap<String, MutableList<Long>>()

    fun allow(key: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = hits.computeIfAbsent(key) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.removeAll { now - it > windowMs }
            if (timestamps.size >= maxRequests) {
                return false
            }
            timestamps.add(now)
            return true
        }
    }
}
