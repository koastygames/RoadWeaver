package net.shiroha233.roadweaver.mixin.fabric;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.shiroha233.roadweaver.beardifier.RoadBeardifierAccess;
import net.shiroha233.roadweaver.beardifier.RoadDensityComputer;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

/**
 * 向 Beardifier 注入道路路基密度贡献
 */
@Mixin(Beardifier.class)
public class BeardifierMixin implements RoadBeardifierAccess {

    @Unique
    private static final Logger roadweaver$LOGGER = LoggerFactory.getLogger("RoadWeaver/BeardifierMixin");

    @Unique
    private List<RoadDensityComputer.Segment> roadweaver$roadSegments = Collections.emptyList();
    @Unique
    private int roadweaver$clearHeight = 4;

    @Override
    public void roadweaver$setRoadSegments(List<RoadDensityComputer.Segment> segments) {
        this.roadweaver$roadSegments = segments != null ? segments : Collections.emptyList();
    }

    @Override
    public List<RoadDensityComputer.Segment> roadweaver$getRoadSegments() {
        return this.roadweaver$roadSegments;
    }

    @Override
    public void roadweaver$setClearHeight(int height) {
        this.roadweaver$clearHeight = height;
    }

    @Override
    public int roadweaver$getClearHeight() {
        return this.roadweaver$clearHeight;
    }

    @Inject(method = "forStructuresInChunk", at = @At("RETURN"))
    private static void roadweaver$injectRoadData(StructureManager mgr, ChunkPos pos,
                                                   CallbackInfoReturnable<Beardifier> cir) {
        try {
            ModConfig cfg = ConfigService.get();
            if (cfg == null) return;

            LevelAccessor levelAccessor = ((StructureManagerAccessor) mgr).roadweaver$getLevel();
            ServerLevel serverLevel = roadweaver$resolveServerLevel(levelAccessor);
            if (serverLevel == null) return;

            int minX = pos.getMinBlockX();
            int minZ = pos.getMinBlockZ();
            int maxX = pos.getMaxBlockX();
            int maxZ = pos.getMaxBlockZ();

            List<RoadData> roads = RoadShardStorage.queryRect(serverLevel,
                    minX - 16, minZ - 16, maxX + 16, maxZ + 16);
            if (roads.isEmpty()) return;

            List<RoadDensityComputer.Segment> segments =
                    RoadDensityComputer.extractSegmentsForChunk(roads, minX, minZ, maxX, maxZ);
            if (!segments.isEmpty()) {
                RoadBeardifierAccess access = (RoadBeardifierAccess) cir.getReturnValue();
                access.roadweaver$setRoadSegments(segments);
                access.roadweaver$setClearHeight(cfg.roadAppearance().roadClearHeight());
            }
        } catch (Exception e) {
            roadweaver$LOGGER.debug("Failed to inject road data into Beardifier", e);
        }
    }

    @Inject(method = "compute", at = @At("RETURN"), cancellable = true)
    private void roadweaver$addRoadDensity(DensityFunction.FunctionContext ctx,
                                           CallbackInfoReturnable<Double> cir) {
        if (roadweaver$roadSegments.isEmpty()) return;

        double road = RoadDensityComputer.compute(ctx.blockX(), ctx.blockY(), ctx.blockZ(),
                roadweaver$roadSegments, roadweaver$clearHeight);
        if (road != 0.0) {
            cir.setReturnValue(cir.getReturnValue() + road);
        }
    }

    @Unique
    private static ServerLevel roadweaver$resolveServerLevel(LevelAccessor accessor) {
        if (accessor instanceof ServerLevel sl) return sl;
        if (accessor instanceof WorldGenRegion wgr) return wgr.getLevel();
        return null;
    }
}
