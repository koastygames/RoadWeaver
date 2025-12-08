package net.shiroha233.roadweaver.structures.roadside;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

final class RoadsideMobSpawner {
    private RoadsideMobSpawner() {
    }

    private static final ResourceLocation MAID_ENTITY_ID = ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "maid");
    private static final ResourceLocation MAID_HOUSE_TEMPLATE = ResourceLocation.fromNamespaceAndPath("roadweaver", "roadside/medium/maid_house");
    private static final ResourceLocation SAKURA_COFFEE_HOUSE_TEMPLATE = ResourceLocation.fromNamespaceAndPath("roadweaver", "roadside/medium/sakura_coffee_house");

    static void trySpawn(ServerLevel server, RandomSource random, BlockPos centerPos, RoadsideDecorationSpec spec) {
        if (!needsMaid(spec.templateId())) {
            return;
        }
        EntityType<?> type = server.registryAccess()
                .registryOrThrow(Registries.ENTITY_TYPE)
                .getOptional(MAID_ENTITY_ID)
                .orElse(null);
        if (type == null) {
            return;
        }
        Entity entity = type.create(server);
        if (entity == null) {
            return;
        }
        double x = centerPos.getX() + 0.5;
        double y = centerPos.getY() + 1;
        double z = centerPos.getZ() + 0.5;
        entity.moveTo(x, y, z, random.nextFloat() * 360f, 0f);
        server.addFreshEntity(entity);
    }

    private static boolean needsMaid(ResourceLocation templateId) {
        return MAID_HOUSE_TEMPLATE.equals(templateId) || SAKURA_COFFEE_HOUSE_TEMPLATE.equals(templateId);
    }
}
