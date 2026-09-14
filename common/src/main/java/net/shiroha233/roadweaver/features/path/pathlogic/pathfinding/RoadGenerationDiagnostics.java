package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

/**
 * Lightweight generation diagnostics used by 3.1.0 callers without requiring
 * a logging dependency in the hot path. Counters are intentionally thread-safe
 * and can be reset between test worlds.
 */
public final class RoadGenerationDiagnostics {
    private static long pavedSegments;
    private static long skippedSegments;
    private static long bridgeSegments;

    private RoadGenerationDiagnostics() {}

    public static synchronized void recordPavedSegment() {
        pavedSegments++;
    }

    public static synchronized void recordSkippedSegment() {
        skippedSegments++;
    }

    public static synchronized void recordBridgeSegment() {
        bridgeSegments++;
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(pavedSegments, skippedSegments, bridgeSegments);
    }

    public static synchronized void reset() {
        pavedSegments = 0;
        skippedSegments = 0;
        bridgeSegments = 0;
    }

    public record Snapshot(long pavedSegments, long skippedSegments, long bridgeSegments) {}
}
