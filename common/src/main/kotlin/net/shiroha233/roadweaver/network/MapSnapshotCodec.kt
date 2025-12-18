package net.shiroha233.roadweaver.network

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.shiroha233.roadweaver.client.map.data.MapSnapshot
import net.shiroha233.roadweaver.helpers.Records

object MapSnapshotCodec {
    @JvmStatic
    fun write(buf: FriendlyByteBuf, s: MapSnapshot) {
        val structures = s.structures()
        val conns = s.connections()

        buf.writeVarInt(structures.size)
        for (p in structures) buf.writeBlockPos(p)

        for (p in structures) {
            val name = s.structureName(p)
            val has = name !== null
            buf.writeBoolean(has)
            if (has) buf.writeUtf(name!!)
        }

        buf.writeVarInt(conns.size)
        for (c in conns) {
            buf.writeBlockPos(c.from)
            buf.writeBlockPos(c.to)
            buf.writeVarInt(c.status.ordinal)
        }

        val roads = s.roadPolylines()
        buf.writeVarInt(roads.size)
        for (pl in roads) {
            buf.writeVarInt(pl.size)
            for (p in pl) buf.writeBlockPos(p)
        }
    }

    @JvmStatic
    fun read(buf: FriendlyByteBuf): MapSnapshot {
        val sc = buf.readVarInt()
        val structures = ArrayList<BlockPos>(sc)
        for (i in 0 until sc) structures.add(buf.readBlockPos())

        val infos = ArrayList<Records.StructureInfo>(sc)
        for (i in 0 until sc) {
            val has = buf.readBoolean()
            if (has) {
                val id = buf.readUtf()
                infos.add(Records.StructureInfo(structures[i], id))
            }
        }

        val cc = buf.readVarInt()
        val conns = ArrayList<Records.StructureConnection>(cc)
        for (i in 0 until cc) {
            val a = buf.readBlockPos()
            val b = buf.readBlockPos()
            val ord = buf.readVarInt()
            val st = Records.ConnectionStatus.values()[ord]
            conns.add(Records.StructureConnection(a, b, st))
        }

        val rp = buf.readVarInt()
        val roads = ArrayList<List<BlockPos>>(rp)
        for (i in 0 until rp) {
            val pc = buf.readVarInt()
            val poly = ArrayList<BlockPos>(pc)
            for (j in 0 until pc) poly.add(buf.readBlockPos())
            roads.add(poly)
        }

        return MapSnapshot(structures, conns, infos, roads)
    }
}
