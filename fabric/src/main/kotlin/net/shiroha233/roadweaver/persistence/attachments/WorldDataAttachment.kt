package net.shiroha233.roadweaver.persistence.attachments

import com.mojang.serialization.Codec
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.resources.ResourceLocation
import net.shiroha233.roadweaver.RoadWeaver
import net.shiroha233.roadweaver.helpers.Records
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object WorldDataAttachment {
    private val LOGGER: Logger = LoggerFactory.getLogger(RoadWeaver.MOD_ID)

    @JvmField
    val CONNECTED_STRUCTURES: AttachmentType<List<Records.StructureConnection>> =
        AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "connected_villages"),
            Codec.list(Records.StructureConnection.CODEC)
        )

    @JvmField
    val STRUCTURE_LOCATIONS: AttachmentType<Records.StructureLocationData> =
        AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "village_locations"),
            Records.StructureLocationData.CODEC
        )

    @JvmField
    val PLANNED_TILE_KEYS: AttachmentType<Set<Long>> =
        AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "planned_tiles"),
            Codec.list(Codec.LONG).xmap(
                { list -> HashSet(list) },
                { set -> ArrayList(set) }
            )
        )

    @JvmField
    val PLANNED_TILE_CENTERS: AttachmentType<Map<Long, Long>> =
        AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "planned_tile_centers"),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(
                { m ->
                    val out = HashMap<Long, Long>()
                    for ((k, v) in m) {
                        try {
                            out[k.toLong()] = v
                        } catch (_: NumberFormatException) {
                        }
                    }
                    out
                },
                { m ->
                    val out = HashMap<String, Long>()
                    for ((k, v) in m) {
                        out[k.toString()] = v
                    }
                    out
                }
            )
        )

    @JvmStatic
    fun registerWorldDataAttachment() {
        LOGGER.info("Registering WorldData attachment")
    }
}
