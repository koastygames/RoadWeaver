package net.shiroha233.roadweaver.util;

import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * 异步计算服务，封装计算线程池的提交操作
 */
public final class ComputeService {

    private ComputeService() {}

    public static Executor executor() {
        return ThreadPoolManager.computeExecutor();
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, ThreadPoolManager.computeExecutor());
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, ThreadPoolManager.computeExecutor());
    }

    public static void shutdownNow() {
        ThreadPoolManager.onServerStopping();
    }
}
