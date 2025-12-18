package net.shiroha233.roadweaver.network.forge

@Suppress("MemberVisibilityCanBePrivate")
object ClientNetBridgeImpl {
    @JvmStatic
    fun requestSnapshot(minX: Int, minZ: Int, maxX: Int, maxZ: Int) {
        MapNetworkForge.requestSnapshot(minX, minZ, maxX, maxZ)
    }

    @JvmStatic
    fun requestTeleport(x: Int, y: Int, z: Int) {
        MapNetworkForge.requestTeleport(x, y, z)
    }

    @JvmStatic
    fun requestManualConnect(ax: Int, az: Int, bx: Int, bz: Int) {
        MapNetworkForge.requestManualConnect(ax, az, bx, bz)
    }
}
