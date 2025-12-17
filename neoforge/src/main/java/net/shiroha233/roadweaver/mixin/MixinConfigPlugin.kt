package net.shiroha233.roadweaver.mixin

import net.neoforged.fml.loading.LoadingModList
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo

class MixinConfigPlugin : IMixinConfigPlugin {

    override fun onLoad(mixinPackage: String) {
    }

    override fun getRefMapperConfig(): String {
        return ""
    }

    override fun shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean {
        if (mixinClassName.contains("DynamicTree")) {
            return isModLoaded("dynamictrees")
        }
        return true
    }

    private fun isModLoaded(modid: String): Boolean {
        val loadingModList = LoadingModList.get()
        return loadingModList?.getModFileById(modid) != null
    }

    override fun acceptTargets(myTargets: MutableSet<String>, otherTargets: MutableSet<String>) {
    }

    override fun getMixins(): MutableList<String> {
        return mutableListOf()
    }

    override fun preApply(
        targetClassName: String,
        targetClass: ClassNode,
        mixinClassName: String,
        mixinInfo: IMixinInfo
    ) {
    }

    override fun postApply(
        targetClassName: String,
        targetClass: ClassNode,
        mixinClassName: String,
        mixinInfo: IMixinInfo
    ) {
    }
}
