package net.shiroha233.roadweaver.structures.registry;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.shiroha233.roadweaver.structures.types.BridgeTemplateStructure;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 桥模板结构注册中心
 * 
 * 从世界的 Structure 注册表中读取所有 BridgeTemplateStructure，
 * 提供根据条件筛选和选择结构的方法。
 * 
 * 数据来源：datapack 中的 worldgen/structure/*.json
 */
public final class BridgeTemplateStructureRegistry {

    private BridgeTemplateStructureRegistry() {
    }

    // 缓存：每个世界的桥模板结构列表
    private static final Map<ResourceKey<?>, List<BridgeTemplate>> CACHE = new ConcurrentHashMap<>();

    /**
     * 桥模板结构
     */
    public static class BridgeTemplate {
        private final ResourceLocation id;
        private final Holder<Structure> holder;
        private final BridgeTemplateStructure structure;
        private BlockState[][][] voxelGrid = new BlockState[0][0][0];
        private Vec3i size = new Vec3i(0, 0, 0);

        public BridgeTemplate(ResourceLocation id, Holder<Structure> holder, BridgeTemplateStructure structure,
                ServerLevel level) {
            this.id = id;
            this.holder = holder;
            this.structure = structure;

            if (level != null) {
                StructureTemplate template = level.getStructureManager().get(structure.templateId).orElse(null);
                if (template == null) {
                    return;
                }
                size = template.getSize();
                voxelGrid = new BlockState[size.getX()][size.getY()][size.getZ()];

                // 这里不用mixin Accessor拿到调色板了，直接用保存NBT功能解析NBT
                // 因为这里似乎不太好mixin
                CompoundTag tag = template.save(new CompoundTag());
                ListTag palette = tag.getList("palette", 10);
                ListTag blocks = tag.getList("blocks", 10);

                parseVoxel(palette, blocks);
            }
        }

        /**
         * 判断给定的坐标点是否在体素网格范围内
         *
         * @param x X坐标值
         * @param y Y坐标值
         * @param z Z坐标值
         * @return 如果坐标点在体素网格范围内返回true，否则返回false
         */
        public boolean isInVoxelGrid(double x, double y, double z) {
            int originalX = (int) Math.floor(x);
            int originalY = (int) Math.floor(y + structure.heightOffset + 1); // 从路面高度计y坐标
            int originalZ = (int) Math.floor(z + 0.01 + size.getZ() / 2.0);

            return originalX >= 0 && originalX < size.getX() && originalY < size.getY() && originalZ >= 0
                    && originalZ < size.getZ();
        }

        /**
         * 获取给定坐标处的体素块状态
         *
         * @param x X坐标值
         * @param y Y坐标值
         * @param z Z坐标值
         * @return 给定坐标处的体素块状态，如果不存在则返回 null
         */
        public BlockState getBlock(double x, double y, double z) {
            // 映射回原始体素坐标
            // 原始X坐标由曲线参数决定
            int originalX = (int) Math.floor(x);

            // 原始Y和Z坐标由局部坐标决定（考虑网格中心）
            int originalY = (int) Math.floor(y + structure.heightOffset + 1); // 从路面高度计y坐标
            int originalZ = (int) Math.floor(z + 0.01 + size.getZ() / 2.0);

            if (originalX < 0 || originalX >= size.getX() || originalY >= size.getY() || originalZ < 0
                    || originalZ >= size.getZ()) {
                return null;
            }

            // 高度过低的话会重复模板中最低的块(地基)进行放置
            if (originalY < 0) {
                originalY = 0;
            }

            return voxelGrid[originalX][originalY][originalZ];
        }

        public int getStartLength() {
            return structure.bridgeDeckStart;
        }

        public int getEndLength() {
            return size.getX() - structure.bridgeDeckEnd;
        }

        public int getDeckLength() {
            return structure.bridgeDeckEnd - structure.bridgeDeckStart + 1;
        }

        public int getTotalLength() {
            return size.getX();
        }

        public ResourceLocation getId() {
            return id;
        }

        public Holder<Structure> getHolder() {
            return holder;
        }

        public BridgeTemplateStructure getStructure() {
            return structure;
        }

        private void parseVoxel(ListTag paletteTag, ListTag blocksTag) {
            List<BlockState> palette = new ArrayList<>();

            for (int i = 0; i < paletteTag.size(); i++) {
                CompoundTag blockTag = paletteTag.getCompound(i);
                BlockState blockState = readBlockState(blockTag);
                palette.add(blockState);
            }

            for (int i = 0; i < blocksTag.size(); i++) {
                CompoundTag blockTag = blocksTag.getCompound(i);

                ListTag pos = blockTag.getList("pos", 3);
                int x = pos.getInt(0);
                int y = pos.getInt(1);
                int z = pos.getInt(2);

                int state = blockTag.getInt("state");

                if (x >= 0 && x < size.getX() && y >= 0 && y < size.getY() && z >= 0 && z < size.getZ()) {
                    voxelGrid[x][y][z] = palette.get(state);
                }
            }
        }

        public static BlockState readBlockState(CompoundTag pTag) {
            if (!pTag.contains("Name", 8)) {
                return Blocks.AIR.defaultBlockState();
            } else {
                ResourceLocation resourcelocation = ResourceLocation.parse(pTag.getString("Name"));
                Optional<? extends Holder<Block>> optional = BuiltInRegistries.BLOCK.asLookup().get(ResourceKey.create(Registries.BLOCK, resourcelocation));
                if (optional.isEmpty()) {
                    return Blocks.AIR.defaultBlockState();
                } else {
                    Block block = optional.get().value();
                    BlockState blockstate = block.defaultBlockState();
                    if (pTag.contains("Properties", 10)) {
                        CompoundTag compoundtag = pTag.getCompound("Properties");
                        StateDefinition<Block, BlockState> statedefinition = block.getStateDefinition();

                        for(String s : compoundtag.getAllKeys()) {
                            Property<?> property = statedefinition.getProperty(s);
                            if (property != null) {
                                blockstate = setValueHelper(blockstate, property, s, compoundtag, pTag);
                            }
                        }
                    }

                    return blockstate;
                }
            }
        }

        private static <S extends StateHolder<?, S>, T extends Comparable<T>> S setValueHelper(S pStateHolder, Property<T> pProperty, String pPropertyName, CompoundTag pPropertiesTag, CompoundTag pBlockStateTag) {
            Optional<T> optional = pProperty.getValue(pPropertiesTag.getString(pPropertyName));
            return optional.map(t -> pStateHolder.setValue(pProperty, t)).orElse(pStateHolder);
        }
    }

    /**
     * 获取所有已注册的桥模板结构
     * 
     * @param level 服务端世界（用于获取注册表）
     * @return 桥模板结构列表
     */
    public static List<BridgeTemplate> getAll(ServerLevel level) {
        ResourceKey<?> dimensionKey = level.dimension();
        return CACHE.computeIfAbsent(dimensionKey, k -> loadFromRegistry(level));
    }

    /**
     * 根据条件选择一个桥模板结构
     * 
     * @param level 服务端世界
     * @param seed  随机种子
     * @return 选中的结构，如果没有符合条件的返回 null
     */
    /**
     * 根据条件选择一个桥模板结构
     * 
     * @param level 服务端世界
     * @param seed  随机种子
     * @return 选中的结构，如果没有符合条件的返回 null
     */
    public static BridgeTemplate choose(ServerLevel level, int seed) {
        return choose(level, seed, null);
    }

    /**
     * 根据群系选择桥模板结构（用于按群系区分桥梁样式）
     */
    public static BridgeTemplate choose(ServerLevel level, int seed, Holder<Biome> biome) {
        List<BridgeTemplate> all = getAll(level);
        if (all == null || all.isEmpty()) {
            return null;
        }

        List<BridgeTemplate> candidates = filterByBiome(all, biome);
        if (candidates.isEmpty()) {
            return null;
        }

        return chooseFromList(candidates, seed);
    }

    private static List<BridgeTemplate> filterByBiome(List<BridgeTemplate> all, Holder<Biome> biome) {
        if (biome == null) {
            return all;
        }
        List<BridgeTemplate> result = new ArrayList<>();
        for (BridgeTemplate template : all) {
            if (template.structure.biomes().contains(biome)) {
                result.add(template);
            }
        }
        return result;
    }

    private static BridgeTemplate chooseFromList(List<BridgeTemplate> candidates, int seed) {
        Random random = new Random(seed * 10000L + 2025 + 1225);
        int randomIndex = random.nextInt(candidates.size());

        candidates.sort(Comparator.comparingInt(o -> o.id.getPath().hashCode()));

        return candidates.get(randomIndex);
    }

    /**
     * 从注册表加载所有桥模板结构
     */
    private static List<BridgeTemplate> loadFromRegistry(ServerLevel level) {
        List<BridgeTemplate> result = new ArrayList<>();
        RegistryAccess registryAccess = level.registryAccess();

        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);

        for (var entry : structureRegistry.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            Structure structure = entry.getValue();

            // 只收集 BridgeTemplateStructure 类型
            if (structure instanceof BridgeTemplateStructure roadsideStructure) {
                Holder<Structure> holder = structureRegistry.getHolderOrThrow(entry.getKey());
                result.add(new BridgeTemplate(id, holder, roadsideStructure, level));
            }
        }

        return result;
    }

    /**
     * 清除缓存（在世界卸载或重载时调用）
     */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * 清除指定维度的缓存
     */
    public static void clearCache(ResourceKey<?> dimensionKey) {
        CACHE.remove(dimensionKey);
    }
}
