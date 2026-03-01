package net.shiroha233.roadweaver.beardifier;

import java.util.List;

/**
 * Beardifier 道路数据访问接口，通过 Mixin 注入
 */
public interface RoadBeardifierAccess {
    void roadweaver$setRoadSegments(List<RoadDensityComputer.Segment> segments);
    List<RoadDensityComputer.Segment> roadweaver$getRoadSegments();
    void roadweaver$setClearHeight(int height);
    int roadweaver$getClearHeight();
}
