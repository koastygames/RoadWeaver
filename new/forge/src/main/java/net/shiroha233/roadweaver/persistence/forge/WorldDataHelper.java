package net.shiroha233.roadweaver.persistence.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Forge 平台便捷访问工具
 * 职责：提供简化的世界数据访问接口，自动处理客户端/服务端差异
 * 注意：客户端世界为 ClientLevel，不可直接持久化访问，若传入非 ServerLevel 将返回空数据
 */
public final class WorldDataHelper {
    private WorldDataHelper() {}

    public static StructureLocationData getStructureLocations(Level level) {
        if (level instanceof ServerLevel server) {
            return WorldDataProvider.getInstance().getStructureLocations(server);
        }
        return new StructureLocationData(new ArrayList<>(), new ArrayList<>());
    }

    public static List<StructureConnection> getConnectedStructures(Level level) {
        if (level instanceof ServerLevel server) {
            return WorldDataProvider.getInstance().getStructureConnections(server);
        }
        return new ArrayList<>();
    }

    public static void setStructureLocations(Level level, StructureLocationData data) {
        if (level instanceof ServerLevel server) {
            WorldDataProvider.getInstance().setStructureLocations(server, data);
        }
    }

    public static void setStructureConnections(Level level, List<StructureConnection> connections) {
        if (level instanceof ServerLevel server) {
            WorldDataProvider.getInstance().setStructureConnections(server, connections);
        }
    }
}
