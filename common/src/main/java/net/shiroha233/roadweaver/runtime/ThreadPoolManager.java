package net.shiroha233.roadweaver.runtime;

import net.minecraft.server.MinecraftServer;
import net.shiroha233.roadweaver.config.ConfigService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public final class ThreadPoolManager {
    private ThreadPoolManager() {}

    private static volatile ExecutorService COMPUTE_EXEC;
    private static volatile ExecutorService GENERATION_EXEC;
    private static final AtomicLong EPOCH = new AtomicLong(0L);

    private static ThreadFactory namedFactory(String prefix) {
        return r -> {
            Thread t = new Thread(r, prefix + "-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        };
    }

    public static synchronized void onServerStarted(MinecraftServer server) {
        EPOCH.incrementAndGet();
        // 计算池: 从配置（0=自动，自动模式为 CPU-1）
        int computeThreads = resolveComputeThreadsFromConfig();
        if (COMPUTE_EXEC != null && !COMPUTE_EXEC.isShutdown() && !COMPUTE_EXEC.isTerminated()) {
            try { COMPUTE_EXEC.shutdownNow(); } catch (Throwable ignored) {}
        }
        COMPUTE_EXEC = Executors.newFixedThreadPool(computeThreads, namedFactory("RW-Compute"));

        // 生成池: 从配置
        int genThreads = Math.max(1, ConfigService.get().generationThreads());
        if (GENERATION_EXEC != null && !GENERATION_EXEC.isShutdown() && !GENERATION_EXEC.isTerminated()) {
            try { GENERATION_EXEC.shutdownNow(); } catch (Throwable ignored) {}
        }
        GENERATION_EXEC = Executors.newFixedThreadPool(genThreads, namedFactory("RW-Gen"));
    }

    public static synchronized void onServerStopping() {
        EPOCH.incrementAndGet();
        if (COMPUTE_EXEC != null) {
            try { COMPUTE_EXEC.shutdownNow(); } catch (Throwable ignored) {}
            COMPUTE_EXEC = null;
        }
        if (GENERATION_EXEC != null) {
            try { GENERATION_EXEC.shutdownNow(); } catch (Throwable ignored) {}
            GENERATION_EXEC = null;
        }
    }

    public static synchronized void resizeGenerationPool(int threads) {
        int genThreads = Math.max(1, threads);
        if (GENERATION_EXEC != null && !GENERATION_EXEC.isShutdown() && !GENERATION_EXEC.isTerminated()) {
            try { GENERATION_EXEC.shutdownNow(); } catch (Throwable ignored) {}
        }
        GENERATION_EXEC = Executors.newFixedThreadPool(genThreads, namedFactory("RW-Gen"));
    }

    // 计算池在运行时动态调整
    public static synchronized void resizeComputePool(int threads) {
        int computeThreads;
        if (threads <= 0) {
            computeThreads = resolveComputeThreadsFromConfig();
        } else {
            computeThreads = Math.max(1, threads);
        }
        if (COMPUTE_EXEC != null && !COMPUTE_EXEC.isShutdown() && !COMPUTE_EXEC.isTerminated()) {
            try { COMPUTE_EXEC.shutdownNow(); } catch (Throwable ignored) {}
        }
        COMPUTE_EXEC = Executors.newFixedThreadPool(computeThreads, namedFactory("RW-Compute"));
    }

    public static ExecutorService computeExecutor() {
        ExecutorService e = COMPUTE_EXEC;
        if (e == null || e.isShutdown() || e.isTerminated()) {
            synchronized (ThreadPoolManager.class) {
                if (COMPUTE_EXEC == null || COMPUTE_EXEC.isShutdown() || COMPUTE_EXEC.isTerminated()) {
                    int computeThreads = resolveComputeThreadsFromConfig();
                    COMPUTE_EXEC = Executors.newFixedThreadPool(computeThreads, namedFactory("RW-Compute"));
                }
                e = COMPUTE_EXEC;
            }
        }
        return e;
    }

    public static ExecutorService generationExecutor() {
        ExecutorService e = GENERATION_EXEC;
        if (e == null || e.isShutdown() || e.isTerminated()) {
            synchronized (ThreadPoolManager.class) {
                if (GENERATION_EXEC == null || GENERATION_EXEC.isShutdown() || GENERATION_EXEC.isTerminated()) {
                    int genThreads = Math.max(1, ConfigService.get().generationThreads());
                    GENERATION_EXEC = Executors.newFixedThreadPool(genThreads, namedFactory("RW-Gen"));
                }
                e = GENERATION_EXEC;
            }
        }
        return e;
    }

    public static long currentEpoch() {
        return EPOCH.get();
    }

    public static boolean isEpoch(long epoch) {
        return EPOCH.get() == epoch;
    }

    // 从配置解析计算线程数，0=自动（CPU-1），异常时回退为1
    private static int resolveComputeThreadsFromConfig() {
        int configured = 0;
        try {
            configured = ConfigService.get().computeThreads();
        } catch (Throwable ignored) {}
        if (configured > 0) {
            return Math.max(1, configured);
        }
        try {
            int cores = Runtime.getRuntime().availableProcessors();
            return Math.max(1, cores - 1);
        } catch (Throwable t) {
            return 1;
        }
    }
}
