package net.shiroha233.roadweaver.pathfinding;

import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;

import java.util.Collections;
import java.util.List;

/**
 * 寻路结果数据模型
 */
public record PathResult(List<RoadSegmentPlacement> segments, boolean success) {

    public static PathResult success(List<RoadSegmentPlacement> segments) {
        return new PathResult(segments != null ? segments : Collections.emptyList(), true);
    }

    public static PathResult failure() {
        return new PathResult(Collections.emptyList(), false);
    }

    public boolean isEmpty() {
        return segments == null || segments.isEmpty();
    }
}
