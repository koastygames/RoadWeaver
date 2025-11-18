package net.shiroha233.roadweaver.features.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.shiroha233.roadweaver.features.decoration.util.BiomeWoodAware;
import net.shiroha233.roadweaver.helpers.Records;

public class FenceWaypointDecoration extends Decoration implements BiomeWoodAware {
    private Records.WoodAssets wood;

    public FenceWaypointDecoration(BlockPos placePos, WorldGenLevel world) {
        super(placePos, world);
    }

    @Override
    public void place() {
        if (!placeAllowed()) return;
        BlockPos surfacePos = this.getPos();
        WorldGenLevel world = this.getWorld();
        world.setBlock(surfacePos, wood.fence().defaultBlockState(), 3);
        world.setBlock(surfacePos.above(), Blocks.TORCH.defaultBlockState(), 3);
    }

    @Override
    public void setWoodType(Records.WoodAssets assets) {
        this.wood = assets;
    }
}
