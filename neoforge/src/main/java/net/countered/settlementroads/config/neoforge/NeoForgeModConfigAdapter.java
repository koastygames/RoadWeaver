package net.countered.settlementroads.config.neoforge;

import net.countered.settlementroads.config.IModConfig;

import java.util.List;

/**
 * Adapter class to bridge NeoForgeJsonConfig with IModConfig interface
 */
public class NeoForgeModConfigAdapter implements IModConfig {

    @Override
    public List<String> structuresToLocate() {
        return NeoForgeJsonConfig.getStructuresToLocate();
    }

    @Override
    public int structureSearchRadius() {
        return NeoForgeJsonConfig.getStructureSearchRadius();
    }

    @Override
    public int initialLocatingCount() {
        return NeoForgeJsonConfig.getInitialLocatingCount();
    }

    @Override
    public int maxConcurrentRoadGeneration() {
        return NeoForgeJsonConfig.getMaxConcurrentRoadGeneration();
    }

    @Override
    public int structureSearchTriggerDistance() {
        return NeoForgeJsonConfig.getStructureSearchTriggerDistance();
    }

    @Override
    public int averagingRadius() {
        return NeoForgeJsonConfig.getAveragingRadius();
    }

    @Override
    public boolean allowArtificial() {
        return NeoForgeJsonConfig.getAllowArtificial();
    }

    @Override
    public boolean allowNatural() {
        return NeoForgeJsonConfig.getAllowNatural();
    }

    @Override
    public boolean placeWaypoints() {
        return NeoForgeJsonConfig.getPlaceWaypoints();
    }

    @Override
    public boolean placeRoadFences() {
        return NeoForgeJsonConfig.getPlaceRoadFences();
    }

    @Override
    public boolean placeSwings() {
        return NeoForgeJsonConfig.getPlaceSwings();
    }

    @Override
    public boolean placeBenches() {
        return NeoForgeJsonConfig.getPlaceBenches();
    }

    @Override
    public boolean placeGloriettes() {
        return NeoForgeJsonConfig.getPlaceGloriettes();
    }

    @Override
    public int structureDistanceFromRoad() {
        return NeoForgeJsonConfig.getStructureDistanceFromRoad();
    }

    @Override
    public int maxHeightDifference() {
        return NeoForgeJsonConfig.getMaxHeightDifference();
    }

    @Override
    public int maxTerrainStability() {
        return NeoForgeJsonConfig.getMaxTerrainStability();
    }

    @Override
    public int manualMaxHeightDifference() {
        return NeoForgeJsonConfig.getManualMaxHeightDifference();
    }

    @Override
    public int manualMaxTerrainStability() {
        return NeoForgeJsonConfig.getManualMaxTerrainStability();
    }

    @Override
    public boolean manualIgnoreWater() {
        return NeoForgeJsonConfig.getManualIgnoreWater();
    }

    @Override
    public boolean enableRoadsideStructures() {
        return NeoForgeJsonConfig.getEnableRoadsideStructures();
    }

    @Override
    public List<String> roadsideStructureTags() {
        return NeoForgeJsonConfig.getRoadsideStructureTags();
    }

    @Override
    public float roadsideStructureSpawnChance() {
        return NeoForgeJsonConfig.getRoadsideStructureSpawnChance();
    }

    @Override
    public int minDistanceBetweenRoadsideStructures() {
        return NeoForgeJsonConfig.getMinDistanceBetweenRoadsideStructures();
    }

    @Override
    public int roadsideStructureDistance() {
        return NeoForgeJsonConfig.getRoadsideStructureDistance();
    }
}
