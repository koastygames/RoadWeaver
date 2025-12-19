package net.shiroha233.roadweaver.persistence.forge;

import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Forge 端便捷访问工具。
 * 注意：客户端(CLIENT)世界为 ClientLevel，不可直接持久化访问，若传入非 ServerLevel 将返回空数据。
 */
public final class WorldDataHelper {
    private WorldDataHelper() {}

    public static Records.StructureLocationData getStructureLocations(Level level) {
        if (level instanceof ServerLevel server) {
            return WorldDataProvider.getInstance().getStructureLocations(server);
        }
        return new Records.StructureLocationData(new ArrayList<>());
    }

    public static List<Records.StructureConnection> getConnectedStructures(Level level) {
        if (level instanceof ServerLevel server) {
            return WorldDataProvider.getInstance().getStructureConnections(server);
        }
        return new ArrayList<>();
    }

    public static void setStructureLocations(Level level, Records.StructureLocationData data) {
        if (level instanceof ServerLevel server) {
            WorldDataProvider.getInstance().setStructureLocations(server, data);
        }
    }

    public static void setStructureConnections(Level level, List<Records.StructureConnection> connections) {
        if (level instanceof ServerLevel server) {
            WorldDataProvider.getInstance().setStructureConnections(server, connections);
        }
    }
}