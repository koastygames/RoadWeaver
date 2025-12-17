package net.shiroha233.roadweaver.network

import dev.architectury.injectables.annotations.ExpectPlatform

object ClientNetBridge {
    @JvmStatic
    @ExpectPlatform
    fun requestSnapshot(minX: Int, minZ: Int, maxX: Int, maxZ: Int) {
        throw AssertionError()
    }

    @JvmStatic
    @ExpectPlatform
    fun requestTeleport(x: Int, y: Int, z: Int) {
        throw AssertionError()
    }

    @JvmStatic
    @ExpectPlatform
    fun requestManualConnect(ax: Int, az: Int, bx: Int, bz: Int) {
        throw AssertionError()
    }
}
