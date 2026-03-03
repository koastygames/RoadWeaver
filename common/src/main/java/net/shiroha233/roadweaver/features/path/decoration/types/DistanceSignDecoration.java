package net.shiroha233.roadweaver.features.path.decoration.types;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.shiroha233.roadweaver.features.path.decoration.base.OrientedDecoration;
import net.shiroha233.roadweaver.core.model.WoodAssets;
import net.shiroha233.roadweaver.features.path.decoration.material.BiomeWoodAware;
import net.shiroha233.roadweaver.features.path.decoration.text.SignTextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 距离路牌装饰
 */
public class DistanceSignDecoration extends OrientedDecoration implements BiomeWoodAware {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private final boolean isStart;
    private final String signText;
    private WoodAssets wood;

    public DistanceSignDecoration(BlockPos pos, Vec3i direction, WorldGenLevel world, boolean isStart, String distanceText) {
        super(pos, direction, world);
        this.isStart = isStart;
        this.signText = distanceText;
    }

    @Override
    public void place() {
        if (!placeAllowed()) return;
        int rotation = getCardinalRotationFromVector(getOrthogonalVector(), isStart);
        DirectionProperties props = getDirectionProperties(rotation);

        BlockPos basePos = this.getPos();
        WorldGenLevel world = this.getWorld();

        BlockPos signPos = basePos.above(2).relative(props.offsetDirection.getOpposite());
        world.setBlock(signPos,
                wood.hangingSign().defaultBlockState()
                        .setValue(BlockStateProperties.ROTATION_16, rotation)
                        .setValue(BlockStateProperties.ATTACHED, true),
                3);
        placeFenceStructure(basePos, props);
        try {
            updateSigns(world, signPos, signText);
        } catch (Throwable t) {
            LOGGER.warn("Distance sign text write failed at {}", signPos, t);
        }
    }

    private void placeFenceStructure(BlockPos pos, DirectionProperties props) {
        WorldGenLevel world = this.getWorld();
        world.setBlock(pos.above(3).relative(props.offsetDirection.getOpposite()), wood.fence().defaultBlockState().setValue(props.directionProperty, true), 3);
        world.setBlock(pos.above(0), wood.fence().defaultBlockState(), 3);
        world.setBlock(pos.above(1), wood.fence().defaultBlockState(), 3);
        world.setBlock(pos.above(2), wood.fence().defaultBlockState(), 3);
        world.setBlock(pos.above(3), wood.fence().defaultBlockState().setValue(props.reverseDirectionProperty, true), 3);
    }

    private void updateSigns(WorldGenLevel level, BlockPos pos, String text) {
        SignTextService.writeDistanceSign(level, pos, text);
    }

    @Override
    public void setWoodType(WoodAssets assets) {
        this.wood = assets;
    }
}
