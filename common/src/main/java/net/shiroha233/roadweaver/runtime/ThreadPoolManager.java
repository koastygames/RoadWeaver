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

    // ===================== 线程节流（占空比控制）=====================
    // 每个工作周期的基准时长（毫秒）
    private static final long WORK_PERIOD_MS = 20;
    // 每个线程记录上次重置时间
    private static final ThreadLocal<Long> WORK_START = ThreadLocal.withInitial(System::currentTimeMillis);

    /**
     * 节流检查点 - 在耗时任务的循环中周期性调用。
     * 根据配置的 threadDutyCycle（占空比），工作一段时间后主动休眠。
     * 例如：50% 占空比 → 工作 20ms 后休眠 20ms；10% → 工作 20ms 后休眠 180ms。
     */
    public static void throttle() {
        int duty;
        try {
            duty = ConfigService.get().threadDutyCycle();
        } catch (Throwable t) {
            return; // 配置未加载，不节流
        }
        if (duty >= 100) return; // 100% 不节流

        long now = System.currentTimeMillis();
        long elapsed = now - WORK_START.get();

        if (elapsed >= WORK_PERIOD_MS) {
            // 工作了足够长时间，按占空比计算休眠时长
            long sleepMs = (long) (WORK_PERIOD_MS * (100.0 - duty) / duty);
            if (sleepMs > 0) {
                try {
                    Thread.sleep(Math.min(sleepMs, 200)); // 单次最多休眠 200ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            WORK_START.set(System.currentTimeMillis());
        }
    }

    /** 重置当前线程的节流计时器（在任务开始时调用） */
    public static void resetThrottle() {
        WORK_START.set(System.currentTimeMillis());
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
