package net.shiroha233.roadweaver.runtime

import net.minecraft.server.MinecraftServer
import net.shiroha233.roadweaver.config.ConfigService
import net.shiroha233.roadweaver.config.ModConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * 统一线程池管理器。
 */
object ThreadPoolManager {
    @Volatile
    private var COMPUTE_EXEC: ExecutorService? = null

    @Volatile
    private var GENERATION_EXEC: ExecutorService? = null

    @Volatile
    private var INITIAL_GEN_EXEC: ExecutorService? = null

    private val EPOCH = AtomicLong(0L)

    private fun namedFactory(prefix: String): ThreadFactory {
        return ThreadFactory { r ->
            val t = Thread(r, "$prefix-${System.nanoTime()}")
            t.isDaemon = true
            t
        }
    }

    /**
     * 服务器启动时调用：初始化所有线程池
     */
    @JvmStatic
    @Synchronized
    fun onServerStarted(server: MinecraftServer) {
        EPOCH.incrementAndGet()
        val cfg: ModConfig = ConfigService.get()

        val computeThreads = resolveComputeThreads(cfg.computeThreads())
        shutdownQuietly(COMPUTE_EXEC)
        COMPUTE_EXEC = Executors.newFixedThreadPool(computeThreads, namedFactory("RW-Compute"))

        val genThreads = max(1, cfg.generationThreads())
        shutdownQuietly(GENERATION_EXEC)
        GENERATION_EXEC = Executors.newFixedThreadPool(genThreads, namedFactory("RW-Gen"))

        val initialThreads = max(1, cfg.initialGenerationThreads())
        shutdownQuietly(INITIAL_GEN_EXEC)
        INITIAL_GEN_EXEC = Executors.newFixedThreadPool(initialThreads, namedFactory("RW-InitGen"))
    }

    /**
     * 服务器停止时调用：关闭所有线程池
     */
    @JvmStatic
    @Synchronized
    fun onServerStopping() {
        EPOCH.incrementAndGet()
        shutdownQuietly(COMPUTE_EXEC)
        COMPUTE_EXEC = null
        shutdownQuietly(GENERATION_EXEC)
        GENERATION_EXEC = null
        shutdownQuietly(INITIAL_GEN_EXEC)
        INITIAL_GEN_EXEC = null
    }

    private fun shutdownQuietly(exec: ExecutorService?) {
        if (exec != null && !exec.isShutdown && !exec.isTerminated) {
            try {
                exec.shutdownNow()
            } catch (_: Throwable) {
            }
        }
    }

    @JvmStatic
    @Synchronized
    fun resizeGenerationPool(threads: Int) {
        val genThreads = max(1, threads)
        shutdownQuietly(GENERATION_EXEC)
        GENERATION_EXEC = Executors.newFixedThreadPool(genThreads, namedFactory("RW-Gen"))
    }

    @JvmStatic
    @Synchronized
    fun resizeComputePool(threads: Int) {
        val computeThreads = resolveComputeThreads(threads)
        shutdownQuietly(COMPUTE_EXEC)
        COMPUTE_EXEC = Executors.newFixedThreadPool(computeThreads, namedFactory("RW-Compute"))
    }

    @JvmStatic
    @Synchronized
    fun resizeInitialGenPool(threads: Int) {
        val initialThreads = max(1, threads)
        shutdownQuietly(INITIAL_GEN_EXEC)
        INITIAL_GEN_EXEC = Executors.newFixedThreadPool(initialThreads, namedFactory("RW-InitGen"))
    }

    @JvmStatic
    fun computeExecutor(): ExecutorService {
        var e = COMPUTE_EXEC
        if (e == null || e.isShutdown || e.isTerminated) {
            synchronized(this) {
                if (COMPUTE_EXEC == null || COMPUTE_EXEC!!.isShutdown || COMPUTE_EXEC!!.isTerminated) {
                    val computeThreads = resolveComputeThreads(ConfigService.get().computeThreads())
                    COMPUTE_EXEC = Executors.newFixedThreadPool(computeThreads, namedFactory("RW-Compute"))
                }
                e = COMPUTE_EXEC
            }
        }
        return e!!
    }

    @JvmStatic
    fun generationExecutor(): ExecutorService {
        var e = GENERATION_EXEC
        if (e == null || e.isShutdown || e.isTerminated) {
            synchronized(this) {
                if (GENERATION_EXEC == null || GENERATION_EXEC!!.isShutdown || GENERATION_EXEC!!.isTerminated) {
                    val genThreads = max(1, ConfigService.get().generationThreads())
                    GENERATION_EXEC = Executors.newFixedThreadPool(genThreads, namedFactory("RW-Gen"))
                }
                e = GENERATION_EXEC
            }
        }
        return e!!
    }

    @JvmStatic
    fun initialGenExecutor(): ExecutorService {
        var e = INITIAL_GEN_EXEC
        if (e == null || e.isShutdown || e.isTerminated) {
            synchronized(this) {
                if (INITIAL_GEN_EXEC == null || INITIAL_GEN_EXEC!!.isShutdown || INITIAL_GEN_EXEC!!.isTerminated) {
                    val initialThreads = max(1, ConfigService.get().initialGenerationThreads())
                    INITIAL_GEN_EXEC = Executors.newFixedThreadPool(initialThreads, namedFactory("RW-InitGen"))
                }
                e = INITIAL_GEN_EXEC
            }
        }
        return e!!
    }

    @JvmStatic
    fun awaitInitialGenCompletion(timeoutSeconds: Long): Boolean {
        val e = INITIAL_GEN_EXEC ?: return true
        e.shutdown()
        return try {
            e.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    @JvmStatic
    fun currentEpoch(): Long {
        return EPOCH.get()
    }

    @JvmStatic
    fun isEpoch(epoch: Long): Boolean {
        return EPOCH.get() == epoch
    }

    // ===================== 线程节流（占空比控制）=====================
    private const val WORK_PERIOD_MS = 20L
    private val WORK_START: ThreadLocal<Long> = ThreadLocal.withInitial { System.currentTimeMillis() }

    @JvmStatic
    fun throttle(dutyCycle: Int) {
        if (dutyCycle >= 100) return
        val duty = max(1, min(100, dutyCycle))

        val now = System.currentTimeMillis()
        val elapsed = now - WORK_START.get()

        if (elapsed >= WORK_PERIOD_MS) {
            val sleepMs = (WORK_PERIOD_MS * (100.0 - duty) / duty).toLong()
            if (sleepMs > 0) {
                try {
                    Thread.sleep(min(sleepMs, 200L))
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            WORK_START.set(System.currentTimeMillis())
        }
    }

    /**
     * @deprecated 使用 throttle(int)
     */
    @Deprecated("Use throttle(dutyCycle: Int)")
    @JvmStatic
    fun throttle() {
        val duty = try {
            ConfigService.get().threadDutyCycle()
        } catch (_: Throwable) {
            return
        }
        throttle(duty)
    }

    /** 重置当前线程的节流计时器（在任务开始时调用） */
    @JvmStatic
    fun resetThrottle() {
        WORK_START.set(System.currentTimeMillis())
    }

    /** 清理当前线程的节流计时器（任务完成时调用） */
    @JvmStatic
    fun clearThrottle() {
        WORK_START.remove()
    }

    private fun resolveComputeThreads(configured: Int): Int {
        if (configured > 0) {
            return max(1, configured)
        }
        return try {
            val cores = Runtime.getRuntime().availableProcessors()
            max(1, cores - 1)
        } catch (_: Throwable) {
            1
        }
    }
}
