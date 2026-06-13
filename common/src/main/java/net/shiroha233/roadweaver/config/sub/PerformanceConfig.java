package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.config.SubConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 性能与线程配置
 */
public final class PerformanceConfig implements SubConfig {
    private int sharedWorkerThreads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
    private int maxConcurrentGenerations = Math.max(1, Math.min(2, sharedWorkerThreads));
    private int threadDutyCycle = RoadConstants.DEFAULT_DUTY_CYCLE;
    private boolean idleGenerationEnabled = true;
    private int idleThreadDutyCycle = 20;

    @Override
    public void sanitize() {
        sharedWorkerThreads = Math.max(1, Math.min(RoadConstants.COMPUTE_THREADS_MAX, sharedWorkerThreads));
        maxConcurrentGenerations = Math.max(1, Math.min(128, maxConcurrentGenerations));
        if (threadDutyCycle < RoadConstants.DUTY_CYCLE_MIN || threadDutyCycle > RoadConstants.DUTY_CYCLE_MAX) {
            threadDutyCycle = RoadConstants.DEFAULT_DUTY_CYCLE;
        }
        if (idleThreadDutyCycle < RoadConstants.DUTY_CYCLE_MIN || idleThreadDutyCycle > RoadConstants.DUTY_CYCLE_MAX) {
            idleThreadDutyCycle = 20;
        }
    }

    @Override
    public PerformanceConfig snapshot() {
        PerformanceConfig copy = new PerformanceConfig();
        copy.sharedWorkerThreads = this.sharedWorkerThreads;
        copy.maxConcurrentGenerations = this.maxConcurrentGenerations;
        copy.threadDutyCycle = this.threadDutyCycle;
        copy.idleGenerationEnabled = this.idleGenerationEnabled;
        copy.idleThreadDutyCycle = this.idleThreadDutyCycle;
        return copy;
    }

    public int sharedWorkerThreads() { return sharedWorkerThreads; }
    public void setSharedWorkerThreads(int v) { this.sharedWorkerThreads = Math.max(1, Math.min(RoadConstants.COMPUTE_THREADS_MAX, v)); }
    public int maxConcurrentGenerations() { return maxConcurrentGenerations; }
    public void setMaxConcurrentGenerations(int v) { this.maxConcurrentGenerations = Math.max(1, Math.min(128, v)); }
    public int threadDutyCycle() { return threadDutyCycle; }
    public void setThreadDutyCycle(int v) { this.threadDutyCycle = Math.max(RoadConstants.DUTY_CYCLE_MIN, Math.min(RoadConstants.DUTY_CYCLE_MAX, v)); }
    public boolean idleGenerationEnabled() { return idleGenerationEnabled; }
    public void setIdleGenerationEnabled(boolean v) { this.idleGenerationEnabled = v; }
    public int idleThreadDutyCycle() { return idleThreadDutyCycle; }
    public void setIdleThreadDutyCycle(int v) { this.idleThreadDutyCycle = Math.max(RoadConstants.DUTY_CYCLE_MIN, Math.min(RoadConstants.DUTY_CYCLE_MAX, v)); }

    public boolean shouldThrottle() { return threadDutyCycle < RoadConstants.DUTY_CYCLE_MAX; }
}