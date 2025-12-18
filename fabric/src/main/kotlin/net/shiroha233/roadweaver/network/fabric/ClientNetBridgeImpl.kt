package net.shiroha233.roadweaver.network.fabric

object ClientNetBridgeImpl {
    @JvmStatic
    fun requestSnapshot(minX: Int, minZ: Int, maxX: Int, maxZ: Int) {
        MapNetworkFabric.requestSnapshot(minX, minZ, maxX, maxZ)
    }

    @JvmStatic
    fun requestTeleport(x: Int, y: Int, z: Int) {
        MapNetworkFabric.requestTeleport(x, y, z)
    }

    @JvmStatic
    fun requestManualConnect(ax: Int, az: Int, bx: Int, bz: Int) {
        MapNetworkFabric.requestManualConnect(ax, az, bx, bz)
    }
}
