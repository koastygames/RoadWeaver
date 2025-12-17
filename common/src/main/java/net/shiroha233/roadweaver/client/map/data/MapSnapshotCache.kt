package net.shiroha233.roadweaver.client.map.data

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object MapSnapshotCache {
    @Volatile
    private var SNAPSHOT: MapSnapshot? = null

    private val CLEAR_SEQ = AtomicInteger()

    @JvmStatic
    fun peek(): MapSnapshot? {
        return SNAPSHOT
    }

    @JvmStatic
    fun put(s: MapSnapshot) {
        SNAPSHOT = s
    }

    @JvmStatic
    fun scheduleClear(delayMs: Long) {
        val token = CLEAR_SEQ.incrementAndGet()
        val d = kotlin.math.max(0L, delayMs)
        val delayed: Executor = CompletableFuture.delayedExecutor(d, TimeUnit.MILLISECONDS)
        CompletableFuture.runAsync(
            {
                if (CLEAR_SEQ.get() == token) {
                    SNAPSHOT = null
                }
            },
            delayed
        )
    }

    @JvmStatic
    fun cancelClear() {
        CLEAR_SEQ.incrementAndGet()
    }

    @JvmStatic
    fun clearNow() {
        CLEAR_SEQ.incrementAndGet()
        SNAPSHOT = null
    }
}
