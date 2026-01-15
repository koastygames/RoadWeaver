package net.shiroha233.roadweaver.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 配置插件，用于条件加载可选模组的 Mixin。
 * 当目标模组未安装时，跳过对应的 Mixin，避免类加载失败。
 */
public class MixinConfigPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Oh-The-Trees-Youll-Grow 相关的 Mixin
        if (mixinClassName.contains("TreeFromStructureNBTFeatureMixin")) {
            return isModLoaded("ohthetreesyoullgrow");
        }
        
        // ReTerraForged 相关的 Mixin
        if (mixinClassName.contains("RTFTemplateFeatureMixin") || 
            mixinClassName.contains("RTFBushFeatureMixin")) {
            return isModLoaded("reterraforged");
        }

        return true;
    }

    private boolean isModLoaded(String modid) {
        return FabricLoader.getInstance().isModLoaded(modid);
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
