package net.shiroha233.roadweaver.network;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端网络桥接 - 跨平台网络通信抽象层
 * 职责：提供客户端到服务端的网络请求接口，通过 @ExpectPlatform 实现 Fabric/Forge 平台适配
 */
public final class ClientNetBridge {
    private ClientNetBridge() {}

    @ExpectPlatform
    public static void requestSnapshot(int requestSeq, ResourceLocation dimensionId, int minX, int minZ, int maxX, int maxZ) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void requestTeleport(int x, int y, int z) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        throw new AssertionError();
    }
}
