package net.shiroha233.roadweaver.mixin;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 配置插件，用于条件加载可选模组的 Mixin。
 * <p>
 * 当目标模组（如 DynamicTrees）未安装时，跳过对应的 Mixin，避免类加载失败。
 * </p>
 */
public class MixinConfigPlugin implements IMixinConfigPlugin {

    private static final ModList modList = ModList.get();
    private static final LoadingModList loadingModList = LoadingModList.get();

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // DynamicTrees 相关的 Mixin 只在模组存在时加载
        if (mixinClassName.contains("DynamicTree")) {
            return isModLoaded("dynamictrees");
        }
        return true;
    }

    /**
     * 检查模组是否已加载
     */
    private boolean isModLoaded(String modid) {
        if (modList != null) {
            return modList.isLoaded(modid);
        } else if (loadingModList != null) {
            return loadingModList.getModFileById(modid) != null;
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
