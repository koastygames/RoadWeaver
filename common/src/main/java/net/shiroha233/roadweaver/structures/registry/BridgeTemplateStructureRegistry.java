package net.shiroha233.roadweaver.structures.registry;

import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.shiroha233.roadweaver.structures.types.BridgeTemplateStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
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

    private BridgeTemplateStructureRegistry() {}
    
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

        public BridgeTemplate(ResourceLocation id, Holder<Structure> holder, BridgeTemplateStructure structure) {
            this.id = id;
            this.holder = holder;
            this.structure = structure;

            if (Minecraft.getInstance().getSingleplayerServer() != null) {
                var template = Minecraft.getInstance().getSingleplayerServer().getStructureManager().get(structure.templateId).orElse(null);
                List<StructureTemplate.Palette> palettes = template != null ? getPalettes(template) : List.of();
                if (template == null || palettes.isEmpty()) {
                    return;
                }
                size = template.getSize();
                voxelGrid = new BlockState[size.getX()][size.getY()][size.getZ()];

                var palette = palettes.get(0);
                for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                    voxelGrid[info.pos().getX()][info.pos().getY()][info.pos().getZ()] = info.state();
                }
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
            int originalY = (int) Math.floor(y + structure.heightOffset + 1);  // 从路面高度计y坐标
            int originalZ = (int) Math.floor(z + 0.01 + size.getZ() / 2.0);

            return originalX >= 0 && originalX < size.getX() && originalY < size.getY() && originalZ >= 0 && originalZ < size.getZ();
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
            int originalY = (int) Math.floor(y + structure.heightOffset + 1);  // 从路面高度计y坐标
            int originalZ = (int) Math.floor(z + 0.01 + size.getZ() / 2.0);

            if (originalX < 0 || originalX >= size.getX() || originalY >= size.getY() || originalZ < 0 || originalZ >= size.getZ()) {
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

        private static List<StructureTemplate.Palette> getPalettes(StructureTemplate tpl) {
            try {
                Field f = StructureTemplate.class.getDeclaredField("palettes");
                f.setAccessible(true);
                Object val = f.get(tpl);
                if (val instanceof List<?> list) {
                    return (List<StructureTemplate.Palette>) list;
                }
            } catch (Exception ignored) {}
            return List.of();
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
        return CACHE.computeIfAbsent(dimensionKey, k -> loadFromRegistry(level.registryAccess()));
    }
    
    /**
     * 根据条件选择一个桥模板结构
     * 
     * @param level      服务端世界
     * @param seed       随机种子
     * @return 选中的结构，如果没有符合条件的返回 null
     */
    public static BridgeTemplate choose(ServerLevel level,
                                        int seed) {
        List<BridgeTemplate> all = getAll(level);

        Random random = new Random(seed * 10000L + 2025 + 1225);
        int randomIndex = random.nextInt(all.size());

        all.sort(Comparator.comparingInt(o -> o.id.getPath().hashCode()));

        return all.get(randomIndex);
    }
    
    /**
     * 从注册表加载所有桥模板结构
     */
    private static List<BridgeTemplate> loadFromRegistry(RegistryAccess registryAccess) {
        List<BridgeTemplate> result = new ArrayList<>();
        
        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        
        for (var entry : structureRegistry.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            Structure structure = entry.getValue();
            
            // 只收集 BridgeTemplateStructure 类型
            if (structure instanceof BridgeTemplateStructure roadsideStructure) {
                Holder<Structure> holder = structureRegistry.getHolderOrThrow(entry.getKey());
                result.add(new BridgeTemplate(id, holder, roadsideStructure));
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
