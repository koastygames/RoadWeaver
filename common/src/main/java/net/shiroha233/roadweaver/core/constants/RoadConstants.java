package net.shiroha233.roadweaver.core.constants;

/**
 * 全局命名常量，消除代码中的魔法数字
 */
public final class RoadConstants {

    private RoadConstants() {}

    public static final int DEFAULT_ASTAR_STEP = 4;
    public static final int DEFAULT_ASTAR_MAX_STEPS = 200_000;
    public static final int ASTAR_MAX_STEPS_MIN = 3_000;
    public static final int ASTAR_MAX_STEPS_MAX = 200_000;
    public static final int ASTAR_STEP_MAX = 128;
    public static final int ASTAR_STEP_MIN = 4;

    public static final int DEFAULT_HIGHWAY_ASTAR_STEP = 32;
    public static final int DEFAULT_HIGHWAY_ASTAR_MAX_STEPS = 20_000;
    public static final int HIGHWAY_ASTAR_MAX_STEPS_MIN = 1_000;
    public static final int HIGHWAY_ASTAR_MAX_STEPS_MAX = 200_000;
    public static final double DEFAULT_HIGHWAY_FLOATING_WEIGHT = 2.0;
    public static final double DEFAULT_HIGHWAY_PENETRATION_WEIGHT = 4.0;

    public static final int DEFAULT_HIGHWAY_GRID_BLOCKS = 2500;
    public static final int HIGHWAY_GRID_BLOCKS_MIN = 128;
    public static final int HIGHWAY_GRID_BLOCKS_MAX = 20_000;
    public static final int DEFAULT_HIGHWAY_ROAD_WIDTH = 7;
    public static final int HIGHWAY_ROAD_WIDTH_MIN = 1;
    public static final int HIGHWAY_ROAD_WIDTH_MAX = 31;
    public static final int DEFAULT_HIGHWAY_SLOPE_RUN_BLOCKS = 5;
    public static final int DEFAULT_HIGHWAY_SLOPE_RISE_BLOCKS = 1;
    public static final int HIGHWAY_SLOPE_RUN_MIN = 1;
    public static final int HIGHWAY_SLOPE_RUN_MAX = 64;
    public static final int HIGHWAY_SLOPE_RISE_MAX = 16;

    public static final int DEFAULT_PLAN_MAX_EDGE_LEN_BLOCKS = 2048;
    public static final int DEFAULT_BRIDGE_JOIN_LEN_BLOCKS = 1536;
    public static final double DEFAULT_KNN_ALPHA = 1.8;
    public static final double DEFAULT_KNN_MIN_ANGLE_DEG = 40.0;
    public static final double DEFAULT_COMPONENT_MIN_ANGLE_DEG = 35.0;
    public static final int DEFAULT_KNN_K = 2;
    public static final int DEFAULT_KNN_DEGREE_CAP = 2;
    public static final int DEFAULT_COMPONENT_DEGREE_CAP = 3;
    public static final int DEFAULT_INITIAL_PLAN_RADIUS_CHUNKS = 128;
    public static final int DEFAULT_DYNAMIC_PLAN_RADIUS_CHUNKS = 256;
    public static final int MAX_PLANNED_KEYS = 200_000;
    public static final int PLAN_TILE_MIN = 8;
    public static final int PLAN_TILE_MAX = 256;

    public static final long WORK_PERIOD_MS = 20;
    public static final long MAX_SLEEP_MS = 200;
    public static final int DEFAULT_DUTY_CYCLE = 50;
    public static final int DUTY_CYCLE_MIN = 1;
    public static final int DUTY_CYCLE_MAX = 100;
    public static final int DEFAULT_INITIAL_GENERATION_THREADS = 6;
    public static final int COMPUTE_THREADS_MAX = 128;

    public static final int DEFAULT_BRIDGE_DECK_CLEARANCE = 2;
    public static final int BRIDGE_DECK_CLEARANCE_MIN = 1;
    public static final int BRIDGE_DECK_CLEARANCE_MAX = 8;
    public static final int DEFAULT_BRIDGE_MAX_LENGTH_BLOCKS = 100;
    public static final int BRIDGE_MAX_LENGTH_MAX = 10_000;
    public static final int DEFAULT_BUOY_INTERVAL_BLOCKS = 32;
    public static final int BUOY_INTERVAL_MIN = 4;
    public static final int BUOY_INTERVAL_MAX = 256;
    public static final int DEFAULT_BRIDGE_PIER_INTERVAL = 6;
    public static final int BRIDGE_PIER_INTERVAL_MIN = 3;
    public static final int BRIDGE_PIER_INTERVAL_MAX = 32;
    public static final int DEFAULT_BRIDGE_PIER_WIDTH = 1;
    public static final int BRIDGE_PIER_WIDTH_MIN = 1;
    public static final int BRIDGE_PIER_WIDTH_MAX = 3;
    public static final int DEFAULT_BRIDGE_PIER_MAX_HEIGHT = 20;
    public static final int BRIDGE_PIER_MAX_HEIGHT_MIN = 6;
    public static final int BRIDGE_PIER_MAX_HEIGHT_MAX = 64;
    public static final int DEFAULT_BRIDGE_RAMP_SEGMENTS = 4;
    public static final int BRIDGE_RAMP_SEGMENTS_MAX = 12;
    public static final int DEFAULT_BRIDGE_MIN_WATER_DEPTH = 1;
    public static final int DEFAULT_BRIDGE_MIN_LENGTH = 5;
    public static final int DEFAULT_BRIDGE_MERGE_GAP = 8;

    public static final int DEFAULT_ROAD_WIDTH = 3;
    public static final int ROAD_WIDTH_MAX = 15;
    public static final int DEFAULT_LAMP_INTERVAL = 32;
    public static final int LAMP_INTERVAL_MIN = 1;
    public static final int LAMP_INTERVAL_MAX = 2048;
    public static final int DEFAULT_ROAD_CLEAR_HEIGHT = 4;
    public static final int ROAD_CLEAR_HEIGHT_MIN = 1;
    public static final int ROAD_CLEAR_HEIGHT_MAX = 16;
    public static final int DEFAULT_TUNNEL_CLEAR_HEIGHT = 5;
    public static final int TUNNEL_CLEAR_HEIGHT_MIN = 2;
    public static final int TUNNEL_CLEAR_HEIGHT_MAX = 16;
    public static final int DEFAULT_AVERAGING_RADIUS = 8;
    public static final int DEFAULT_MAX_SLOPE_STEP = 1;
    public static final int MAX_SLOPE_STEP_MAX = 8;

    public static final int DEFAULT_MAX_STRUCTURES_PER_ROAD = 3;
    public static final int MAX_STRUCTURES_PER_ROAD_MAX = 20;
    public static final int DEFAULT_SMALL_STRUCTURE_OFFSET = 8;
    public static final int DEFAULT_MEDIUM_STRUCTURE_OFFSET = 12;
    public static final int DEFAULT_LARGE_STRUCTURE_OFFSET = 16;
    public static final int STRUCTURE_OFFSET_MIN = 1;
    public static final int STRUCTURE_OFFSET_MAX = 64;
    public static final int DEFAULT_VILLAGE_ROAD_OFFSET = 60;
    public static final int DEFAULT_OTHER_STRUCTURE_ROAD_OFFSET = 15;
    public static final int ROAD_OFFSET_MAX = 256;

    public static final int DEFAULT_LONG_DRIVE_ROAD_WIDTH = 5;
    public static final int DEFAULT_LONG_DRIVE_ASTAR_STEP = 16;
    public static final int DEFAULT_LONG_DRIVE_SEGMENT_LENGTH = 500;
    public static final int LONG_DRIVE_SEGMENT_LENGTH_MIN = 50;
    public static final int LONG_DRIVE_SEGMENT_LENGTH_MAX = 5000;
    public static final int DEFAULT_LONG_DRIVE_LEAD_DISTANCE = 1000;
    public static final int LONG_DRIVE_LEAD_DISTANCE_MIN = 200;
    public static final int LONG_DRIVE_LEAD_DISTANCE_MAX = 10_000;
    public static final double DEFAULT_LONG_DRIVE_DIRECTION_BIAS = 150.0;
    public static final double LONG_DRIVE_DIRECTION_BIAS_MAX = 1000.0;

    public static final int DEFAULT_PREDICT_RADIUS_CHUNKS = 1024;

    public static final double DEFAULT_ORTHO_STEP_COST = 1.0;
    public static final double DEFAULT_DIAG_STEP_COST = 1.414;
    public static final int DEFAULT_ELEVATION_WEIGHT = 80;
    public static final int DEFAULT_BIOME_WEIGHT = 2;
    public static final int DEFAULT_STABILITY_WEIGHT = 15;
    public static final int DEFAULT_WATER_DEPTH_WEIGHT = 80;
    public static final int DEFAULT_NEAR_WATER_COST = 80;
    public static final int DEFAULT_WATER_PROXIMITY_COST = 20;
    public static final double DEFAULT_HEURISTIC_WEIGHT = 15.0;
    public static final double DEFAULT_DEVIATION_WEIGHT = 0.5;

    public static final int DB_LOCK_TIMEOUT_MS = 10_000;

    public static final int BLOCK_UPDATE_FLAG = 3;
    public static final int BELOW_DEPTH_1 = 1;
    public static final int BELOW_DEPTH_2 = 2;

    public static final int DEFAULT_QUANTIZED_SAMPLING_CHUNK_RADIUS = 1;
    public static final int QUANTIZED_SAMPLING_CHUNK_RADIUS_MAX = 8;

    public static final int GRADIENT_DESCENT_STEPS_MULTIPLIER = 3;
    public static final double GRADIENT_DESCENT_SUCCESS_DISTANCE_FACTOR = 1.5;

    public static final int POTENTIAL_FIELD_STEPS_MULTIPLIER = 4;
    public static final double POTENTIAL_FIELD_SUCCESS_DISTANCE_FACTOR = 1.5;
    public static final double POTENTIAL_FIELD_HEURISTIC_DAMPING = 0.6;
    public static final double DEFAULT_CONTOUR_DISCOUNT = 0.45;
    public static final double DEFAULT_SOFT_GRADE_LIMIT = 0.08;
    public static final double DEFAULT_HARD_GRADE_LIMIT = 0.15;
    public static final double DEFAULT_SOFT_GRADE_PENALTY = 600.0;
    public static final double DEFAULT_HARD_GRADE_PENALTY = 6000.0;
    public static final double DEFAULT_GRADIENT_ALIGN_PENALTY = 80.0;
    public static final double DEFAULT_PF_WATER_BASE_PENALTY = 800.0;

    public static final int SPATIAL_INDEX_GRID_SIZE = 8;
    public static final int SPATIAL_INDEX_GRID_SHIFT = 3;
    public static final int SPATIAL_INDEX_MAX_CACHED_CHUNKS = 512;
    public static final int SPATIAL_INDEX_LRU_INITIAL_CAPACITY = 64;
    public static final float SPATIAL_INDEX_LRU_LOAD_FACTOR = 0.75f;
    public static final int SPATIAL_INDEX_SEARCH_MARGIN = 4;
    public static final int SPATIAL_INDEX_MAX_MARGIN = 127;

    public static final int MIN_ROAD_SEGMENTS_FOR_STRUCTURE = 10;
    public static final int STRUCTURE_PLACEMENT_WINDOW_SIZE = 10;
    public static final int MAX_STRUCTURE_SLOPE = 3;

    public static final int CHUNK_SIZE_BLOCKS = 16;

    // 道路吸附后处理
    public static final int ROAD_SNAP_THRESHOLD = 12;
    public static final int ROAD_SNAP_SPLIT_THRESHOLD = 24;
    public static final int ROAD_SNAP_MIN_RUN_LENGTH = 3;
    public static final int ROAD_SNAP_GRID_SIZE = 8;
    public static final int ROAD_SNAP_GRID_SHIFT = 3;
    public static final int ROAD_SNAP_TRANSITION_SEGMENTS = 3;
}
