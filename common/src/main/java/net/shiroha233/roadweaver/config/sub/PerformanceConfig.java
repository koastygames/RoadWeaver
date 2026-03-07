package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.config.SubConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 性能与线程配置
 */
public final class PerformanceConfig implements SubConfig {
    private int generationThreads = Math.max(2, Math.min(3, Runtime.getRuntime().availableProcessors()));
    private int computeThreads = 0;
    private int initialGenerationThreads = RoadConstants.DEFAULT_INITIAL_GENERATION_THREADS;
    private int maxConcurrentGenerations = Math.max(1, Math.min(3, generationThreads));
    private int threadDutyCycle = RoadConstants.DEFAULT_DUTY_CYCLE;
    private int idleGenerationThreads = 1;
    private int idleThreadDutyCycle = 20;

    @Override
    public void sanitize() {
        computeThreads = Math.max(0, Math.min(RoadConstants.COMPUTE_THREADS_MAX, computeThreads));
        generationThreads = Math.max(1, Math.min(64, generationThreads));
        initialGenerationThreads = Math.max(1, Math.min(64, initialGenerationThreads));
        maxConcurrentGenerations = Math.max(1, Math.min(128, maxConcurrentGenerations));
        if (threadDutyCycle < RoadConstants.DUTY_CYCLE_MIN || threadDutyCycle > RoadConstants.DUTY_CYCLE_MAX) {
            threadDutyCycle = RoadConstants.DEFAULT_DUTY_CYCLE;
        }
        idleGenerationThreads = Math.max(0, Math.min(64, idleGenerationThreads));
        if (idleThreadDutyCycle < RoadConstants.DUTY_CYCLE_MIN || idleThreadDutyCycle > RoadConstants.DUTY_CYCLE_MAX) {
            idleThreadDutyCycle = 20;
        }
    }

    @Override
    public PerformanceConfig snapshot() {
        PerformanceConfig copy = new PerformanceConfig();
        copy.generationThreads = this.generationThreads;
        copy.computeThreads = this.computeThreads;
        copy.initialGenerationThreads = this.initialGenerationThreads;
        copy.maxConcurrentGenerations = this.maxConcurrentGenerations;
        copy.threadDutyCycle = this.threadDutyCycle;
        copy.idleGenerationThreads = this.idleGenerationThreads;
        copy.idleThreadDutyCycle = this.idleThreadDutyCycle;
        return copy;
    }

    public int generationThreads() { return generationThreads; }
    public void setGenerationThreads(int v) { this.generationThreads = v; }
    public int computeThreads() { return computeThreads; }
    public void setComputeThreads(int v) { this.computeThreads = v; }
    public int initialGenerationThreads() { return initialGenerationThreads; }
    public void setInitialGenerationThreads(int v) { this.initialGenerationThreads = v; }
    public int maxConcurrentGenerations() { return maxConcurrentGenerations; }
    public void setMaxConcurrentGenerations(int v) { this.maxConcurrentGenerations = v; }
    public int threadDutyCycle() { return threadDutyCycle; }
    public void setThreadDutyCycle(int v) { this.threadDutyCycle = Math.max(RoadConstants.DUTY_CYCLE_MIN, Math.min(RoadConstants.DUTY_CYCLE_MAX, v)); }
    public int idleGenerationThreads() { return idleGenerationThreads; }
    public void setIdleGenerationThreads(int v) { this.idleGenerationThreads = Math.max(0, Math.min(64, v)); }
    public int idleThreadDutyCycle() { return idleThreadDutyCycle; }
    public void setIdleThreadDutyCycle(int v) { this.idleThreadDutyCycle = Math.max(RoadConstants.DUTY_CYCLE_MIN, Math.min(RoadConstants.DUTY_CYCLE_MAX, v)); }

    public boolean shouldThrottle() { return threadDutyCycle < RoadConstants.DUTY_CYCLE_MAX; }
}
