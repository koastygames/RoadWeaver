package net.shiroha233.roadweaver.features.path.decoration.types;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.shiroha233.roadweaver.features.path.decoration.base.OrientedDecoration;
import net.shiroha233.roadweaver.features.path.decoration.material.wood.BiomeWoodAware;
import net.shiroha233.roadweaver.features.path.decoration.text.SignTextService;
import net.shiroha233.roadweaver.helpers.Records;

public class SeaQuestionSignDecoration extends OrientedDecoration implements BiomeWoodAware {
    private final boolean isStart;
    private Records.WoodAssets wood;

    public SeaQuestionSignDecoration(BlockPos pos, Vec3i direction, WorldGenLevel world, boolean isStart) {
        super(pos, direction, world);
        this.isStart = isStart;
    }

    @Override
    public void place() {
        if (!placeAllowed()) return;

        int rotation = getCardinalRotationFromVector(getOrthogonalVector(), isStart);
        DirectionProperties props = getDirectionProperties(rotation);

        BlockPos basePos = this.getPos();
        WorldGenLevel world = this.getWorld();

        BlockPos signPos = basePos.above(2).relative(props.offsetDirection.getOpposite());
        int signRotation = (rotation + 8) % 16;
        world.setBlock(signPos,
                wood.hangingSign().defaultBlockState()
                        .setValue(BlockStateProperties.ROTATION_16, signRotation)
                        .setValue(BlockStateProperties.ATTACHED, true),
                3);
        updateSigns(world, signPos);

        placeFenceStructure(basePos, props);
    }

    private void placeFenceStructure(BlockPos pos, DirectionProperties props) {
        WorldGenLevel world = this.getWorld();
        world.setBlock(pos.above(3).relative(props.offsetDirection.getOpposite()), wood.fence().defaultBlockState().setValue(props.directionProperty, true), 3);
        world.setBlock(pos.above(0), wood.fence().defaultBlockState(), 3);
        world.setBlock(pos.above(1), wood.fence().defaultBlockState(), 3);
        world.setBlock(pos.above(2), wood.fence().defaultBlockState(), 3);
        world.setBlock(pos.above(3), wood.fence().defaultBlockState().setValue(props.reverseDirectionProperty, true), 3);
    }

    private void updateSigns(WorldGenLevel level, BlockPos pos) {
        SignTextService.writeSeaQuestionSign(level, pos);
    }

    @Override
    public void setWoodType(Records.WoodAssets assets) {
        this.wood = assets;
    }
}
