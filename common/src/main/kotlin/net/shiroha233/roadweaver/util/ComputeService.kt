package net.shiroha233.roadweaver.util

import net.shiroha233.roadweaver.runtime.ThreadPoolManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Supplier

@Suppress("MemberVisibilityCanBePrivate")
object ComputeService {
    @JvmStatic
    fun executor(): Executor {
        return ThreadPoolManager.computeExecutor()
    }

    @JvmStatic
    fun <T> supplyAsync(supplier: Supplier<T>): CompletableFuture<T> {
        return CompletableFuture.supplyAsync(supplier, ThreadPoolManager.computeExecutor())
    }

    @JvmStatic
    fun runAsync(runnable: Runnable): CompletableFuture<Void> {
        return CompletableFuture.runAsync(runnable, ThreadPoolManager.computeExecutor())
    }

    @JvmStatic
    fun shutdownNow() {
        // 委托中央管理器以确保纪元轮换和完整的池关闭
        ThreadPoolManager.onServerStopping()
    }
}
