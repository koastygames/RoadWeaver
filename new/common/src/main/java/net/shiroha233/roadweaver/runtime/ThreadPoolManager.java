package net.shiroha233.roadweaver.runtime;

import net.minecraft.server.MinecraftServer;
import net.shiroha233.roadweaver.config.ConfigService;

import net.shiroha233.roadweaver.core.constants.RoadConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 双线程池管理器
 */
public final class ThreadPoolManager {
    private ThreadPoolManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private static final AtomicReference<ExecutorService> COMPUTE_EXEC = new AtomicReference<>();
    private static final AtomicReference<ExecutorService> GENERATION_EXEC = new AtomicReference<>();
    private static final AtomicLong EPOCH = new AtomicLong(0L);

    private static final ThreadLocal<Long> WORK_START = ThreadLocal.withInitial(System::currentTimeMillis);

    // ==================== 生命周期 ====================

    public static synchronized void onServerStarted(MinecraftServer server) {
        EPOCH.incrementAndGet();
        rebuildComputePool(resolveComputeThreads());
        rebuildGenerationPool(resolveGenerationThreads());
        LOGGER.debug("ThreadPoolManager: 线程池已启动 (epoch={})", EPOCH.get());
    }

    public static synchronized void onServerStopping() {
        EPOCH.incrementAndGet();
        shutdownQuietly(COMPUTE_EXEC.get());
        COMPUTE_EXEC.set(null);
        shutdownQuietly(GENERATION_EXEC.get());
        GENERATION_EXEC.set(null);
        LOGGER.debug("ThreadPoolManager: 线程池已关闭 (epoch={})", EPOCH.get());
    }

    // ==================== 运行时重建 ====================

    public static synchronized void resizeGenerationPool(int threads) {
        rebuildGenerationPool(Math.max(1, threads));
    }

    public static synchronized void resizeComputePool(int threads) {
        int resolved = threads <= 0 ? resolveComputeThreads() : Math.max(1, threads);
        rebuildComputePool(resolved);
    }

    // ==================== Executor 访问 ====================

    public static ExecutorService computeExecutor() {
        ExecutorService e = COMPUTE_EXEC.get();
        if (e == null || e.isShutdown()) {
            synchronized (ThreadPoolManager.class) {
                e = COMPUTE_EXEC.get();
                if (e == null || e.isShutdown()) {
                    rebuildComputePool(resolveComputeThreads());
                    e = COMPUTE_EXEC.get();
                }
            }
        }
        return e;
    }

    public static ExecutorService generationExecutor() {
        ExecutorService e = GENERATION_EXEC.get();
        if (e == null || e.isShutdown()) {
            synchronized (ThreadPoolManager.class) {
                e = GENERATION_EXEC.get();
                if (e == null || e.isShutdown()) {
                    rebuildGenerationPool(resolveGenerationThreads());
                    e = GENERATION_EXEC.get();
                }
            }
        }
        return e;
    }

    // ==================== Epoch ====================

    public static long currentEpoch() {
        return EPOCH.get();
    }

    public static boolean isEpoch(long epoch) {
        return EPOCH.get() == epoch;
    }

    // ==================== 占空比节流 ====================

    /**
     * 节流检查点 — 在耗时循环中周期性调用。
     * 按占空比工作一段时间后主动休眠，避免独占 CPU。
     */
    public static void throttle(int duty) {
        if (duty >= RoadConstants.DUTY_CYCLE_MAX) return;
        if (duty <= 0) duty = RoadConstants.DEFAULT_DUTY_CYCLE;

        long elapsed = System.currentTimeMillis() - WORK_START.get();
        if (elapsed >= RoadConstants.WORK_PERIOD_MS) {
            long sleepMs = (long) (RoadConstants.WORK_PERIOD_MS * (100.0 - duty) / duty);
            if (sleepMs > 0) {
                try {
                    Thread.sleep(Math.min(sleepMs, RoadConstants.MAX_SLEEP_MS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            WORK_START.set(System.currentTimeMillis());
        }
    }

    public static void throttle() {
        try {
            throttle(ConfigService.get().performance().threadDutyCycle());
        } catch (Throwable ignored) {}
    }

    public static void resetThrottle() {
        WORK_START.set(System.currentTimeMillis());
    }

    public static void clearThrottle() {
        WORK_START.remove();
    }

    // ==================== 内部方法 ====================

    private static void rebuildComputePool(int threads) {
        shutdownQuietly(COMPUTE_EXEC.get());
        COMPUTE_EXEC.set(Executors.newFixedThreadPool(threads, namedFactory("RW-Compute")));
    }

    private static void rebuildGenerationPool(int threads) {
        shutdownQuietly(GENERATION_EXEC.get());
        GENERATION_EXEC.set(Executors.newFixedThreadPool(threads, namedFactory("RW-Gen")));
    }

    private static void shutdownQuietly(ExecutorService exec) {
        if (exec != null && !exec.isShutdown()) {
            try { exec.shutdownNow(); } catch (Throwable ignored) {}
        }
    }

    private static ThreadFactory namedFactory(String prefix) {
        return r -> {
            Thread t = new Thread(r, prefix + "-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        };
    }

    private static int resolveComputeThreads() {
        try {
            int configured = ConfigService.get().performance().computeThreads();
            if (configured > 0) return Math.max(1, configured);
        } catch (Throwable ignored) {}
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    private static int resolveGenerationThreads() {
        try {
            return Math.max(1, ConfigService.get().performance().generationThreads());
        } catch (Throwable ignored) {}
        return 2;
    }
}
