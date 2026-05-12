package net.shiroha233.roadweaver.persistence.fabric;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.attachments.WorldDataAttachment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fabric 平台世界数据提供者实现
 */
public class FabricWorldDataProvider extends WorldDataProvider {

    @Override
    public StructureLocationData getStructureLocations(ServerLevel level) {
        StructureLocationData data = ((AttachmentTarget) level).getAttached(WorldDataAttachment.STRUCTURE_LOCATIONS);
        return data != null ? data : new StructureLocationData(new ArrayList<>(), new ArrayList<>());
    }

    @Override
    public void setStructureLocations(ServerLevel level, StructureLocationData data) {
        ((AttachmentTarget) level).setAttached(WorldDataAttachment.STRUCTURE_LOCATIONS, data);
    }

    @Override
    public List<StructureConnection> getStructureConnections(ServerLevel level) {
        return ((AttachmentTarget) level).getAttachedOrCreate(WorldDataAttachment.CONNECTED_STRUCTURES, ArrayList::new);
    }

    @Override
    public void setStructureConnections(ServerLevel level, List<StructureConnection> connections) {
        ((AttachmentTarget) level).setAttached(WorldDataAttachment.CONNECTED_STRUCTURES, connections);
    }

    @Override
    public List<StructureConnection> getHighwayConnections(ServerLevel level) {
        return ((AttachmentTarget) level).getAttachedOrCreate(WorldDataAttachment.HIGHWAY_CONNECTIONS, ArrayList::new);
    }

    @Override
    public void setHighwayConnections(ServerLevel level, List<StructureConnection> connections) {
        ((AttachmentTarget) level).setAttached(WorldDataAttachment.HIGHWAY_CONNECTIONS, connections);
    }

    @Override
    public Set<Long> getPlannedTileKeys(ServerLevel level) {
        return ((AttachmentTarget) level).getAttachedOrCreate(WorldDataAttachment.PLANNED_TILE_KEYS, HashSet::new);
    }

    @Override
    public void setPlannedTileKeys(ServerLevel level, Set<Long> keys) {
        ((AttachmentTarget) level).setAttached(WorldDataAttachment.PLANNED_TILE_KEYS, keys);
    }

    @Override
    public Map<Long, Long> getPlannedTileCenters(ServerLevel level) {
        return ((AttachmentTarget) level).getAttachedOrCreate(WorldDataAttachment.PLANNED_TILE_CENTERS, HashMap::new);
    }

    @Override
    public void setPlannedTileCenters(ServerLevel level, Map<Long, Long> centers) {
        ((AttachmentTarget) level).setAttached(WorldDataAttachment.PLANNED_TILE_CENTERS, centers);
    }

    @Override
    public Map<Long, Long> getHighwayIntersections(ServerLevel level) {
        return ((AttachmentTarget) level).getAttachedOrCreate(WorldDataAttachment.HIGHWAY_INTERSECTIONS, HashMap::new);
    }

    @Override
    public void setHighwayIntersections(ServerLevel level, Map<Long, Long> intersections) {
        ((AttachmentTarget) level).setAttached(WorldDataAttachment.HIGHWAY_INTERSECTIONS, intersections);
    }
}
