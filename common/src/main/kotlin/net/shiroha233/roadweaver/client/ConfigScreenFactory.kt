package net.shiroha233.roadweaver.client

import dev.architectury.injectables.annotations.ExpectPlatform
import net.minecraft.client.gui.screens.Screen

/**
 * 配置屏幕工厂 - 平台抽象接口
 * 使用 Architectury 的 ExpectPlatform 机制实现跨平台调用
 */
object ConfigScreenFactory {
    /**
     * 创建配置屏幕
     * @param parent 父屏幕
     * @return 配置屏幕实例
     */
    @JvmStatic
    @ExpectPlatform
    fun createConfigScreen(parent: Screen): Screen {
        throw AssertionError("该方法应该由平台特定代码实现！")
    }
}
