package net.shiroha233.roadweaver.persistence.fabric

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget
import net.minecraft.server.level.ServerLevel
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import net.shiroha233.roadweaver.persistence.attachments.WorldDataAttachment

class FabricWorldDataProvider : WorldDataProvider() {
    override fun getStructureLocations(level: ServerLevel): Records.StructureLocationData {
        val data = (level as AttachmentTarget).getAttached(WorldDataAttachment.STRUCTURE_LOCATIONS)
        return data ?: Records.StructureLocationData(ArrayList())
    }

    override fun setStructureLocations(level: ServerLevel, data: Records.StructureLocationData) {
        (level as AttachmentTarget).setAttached(WorldDataAttachment.STRUCTURE_LOCATIONS, data)
    }

    override fun getStructureConnections(level: ServerLevel): List<Records.StructureConnection> {
        return (level as AttachmentTarget).getAttachedOrCreate(WorldDataAttachment.CONNECTED_STRUCTURES) { ArrayList() }
    }

    override fun setStructureConnections(level: ServerLevel, connections: List<Records.StructureConnection>) {
        (level as AttachmentTarget).setAttached(WorldDataAttachment.CONNECTED_STRUCTURES, connections)
    }

    override fun getPlannedTileKeys(level: ServerLevel): Set<Long> {
        return (level as AttachmentTarget).getAttachedOrCreate(WorldDataAttachment.PLANNED_TILE_KEYS) { HashSet() }
    }

    override fun setPlannedTileKeys(level: ServerLevel, keys: Set<Long>) {
        (level as AttachmentTarget).setAttached(WorldDataAttachment.PLANNED_TILE_KEYS, keys)
    }

    override fun getPlannedTileCenters(level: ServerLevel): Map<Long, Long> {
        return (level as AttachmentTarget).getAttachedOrCreate(WorldDataAttachment.PLANNED_TILE_CENTERS) { HashMap() }
    }

    override fun setPlannedTileCenters(level: ServerLevel, centers: Map<Long, Long>) {
        (level as AttachmentTarget).setAttached(WorldDataAttachment.PLANNED_TILE_CENTERS, centers)
    }
}
