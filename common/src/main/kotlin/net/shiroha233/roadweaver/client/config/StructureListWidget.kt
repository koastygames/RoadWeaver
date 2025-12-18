package net.shiroha233.roadweaver.client.config

import com.mojang.blaze3d.systems.RenderSystem
import dev.architectury.platform.Platform
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.shiroha233.roadweaver.config.structure.StructureEntry
import net.shiroha233.roadweaver.config.structure.StructureTagEntry
import java.util.*
import java.util.function.Consumer

/**
 * 结构列表组件 (1.20.1)
 */
class StructureListWidget(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int
) : ContainerObjectSelectionList<StructureListWidget.Entry>(minecraft, width, height, top, top + height, 24) {

    companion object {
        private val MOD_ICON_CACHE: MutableMap<String, Optional<ResourceLocation>> = HashMap()

        fun getLocalizedStructureName(structure: StructureEntry?): String {
            if (structure === null) return ""
            if (structure.isVanilla) {
                val id = structure.id()
                val key = "structure.${id.namespace}.${id.path}"
                val translated = Component.translatable(key).getString()
                if (translated != key) return translated
            }
            return structure.displayName()
        }

        fun getModIconTexture(modId: String?): ResourceLocation? {
            if (modId.isNullOrEmpty()) return null
            val cached = MOD_ICON_CACHE[modId]
            if (cached !== null) return cached.orElse(null)

            var resolved: ResourceLocation? = null
            try {
                val optMod = Platform.getOptionalMod(modId)
                if (optMod.isPresent) {
                    val mod = optMod.get()
                    // 1.20.1 Platform.Mod might have different logo access
                    // Architectury 9.x API check
                    val logoOpt = mod.getLogoFile(32)
                    if (logoOpt.isPresent) {
                        val logoPath = logoOpt.get()
                        val candidates = ArrayList<ResourceLocation>()
                        
                        if (logoPath.startsWith("assets/")) {
                            val rel = logoPath.substring("assets/".length)
                            val slash = rel.indexOf('/')
                            if (slash >= 0) {
                                val ns = rel.substring(0, slash)
                                val path = rel.substring(slash + 1)
                                candidates.add(ResourceLocation(ns, path))
                            }
                        } else if (logoPath.indexOf(':') >= 0) {
                            val split = logoPath.split(":")
                            if (split.size == 2) {
                                candidates.add(ResourceLocation(split[0], split[1]))
                            }
                        } else {
                            candidates.add(ResourceLocation(modId, logoPath))
                            if (!logoPath.startsWith("textures/")) {
                                candidates.add(ResourceLocation(modId, "textures/$logoPath"))
                                candidates.add(ResourceLocation(modId, "textures/gui/$logoPath"))
                            }
                        }

                        val rm = Minecraft.getInstance().resourceManager
                        for (rl in candidates) {
                            if (rl !== null && rm.getResource(rl).isPresent()) {
                                resolved = rl
                                break
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {
            }
            
            MOD_ICON_CACHE[modId] = Optional.ofNullable(resolved)
            return resolved
        }
    }

    override fun getRowWidth(): Int = width - 40
    override fun getScrollbarPosition(): Int = getRowLeft() + getRowWidth() + 6

    fun clearAllEntries() {
        clearEntries()
    }

    fun addEntryItem(entry: Entry) {
        addEntry(entry)
    }

    abstract class Entry : ContainerObjectSelectionList.Entry<Entry>()

    /**
     * 模组头条目
     */
    class ModHeaderEntry(
        private val list: StructureListWidget,
        private val modId: String,
        private val title: Component,
        private val expanded: Boolean,
        private val onToggle: Consumer<String>
    ) : Entry() {
        override fun render(
            graphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            val mc = Minecraft.getInstance()
            val expandIcon = if (expanded) "▼" else "▶"
            
            // 绘制展开箭头
            graphics.drawString(mc.font, expandIcon, left + 2, top + 7, 0xFFFFFF, false)

            var textX = left + 24
            val icon = getModIconTexture(modId)

            icon?.let {
                RenderSystem.enableBlend()
                graphics.blit(it, left + 14, top + 4, 0f, 0f, 16, 16, 16, 16)
                RenderSystem.disableBlend()
                textX += 10
            }

            graphics.drawString(mc.font, title, textX, top + 7, 0xFFFFFF, false)
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (button == 0) {
                // 在 1.20.1 中使用 getRowWidth 计算点击范围
                val rowLeft = (list.width - list.getRowWidth()) / 2
                if (mouseX >= rowLeft.toDouble() && mouseX <= (rowLeft + list.getRowWidth()).toDouble()) {
                    onToggle.accept(modId)
                    return true
                }
            }
            return super.mouseClicked(mouseX, mouseY, button)
        }

        override fun children(): List<GuiEventListener> = emptyList()
        override fun narratables(): List<NarratableEntry> = emptyList()
    }

    /**
     * 标签条目
     */
    class TagEntry(
        private val list: StructureListWidget,
        private val tag: StructureTagEntry,
        private val enabled: Boolean,
        private val expanded: Boolean,
        private val onToggle: Consumer<StructureTagEntry>,
        private val onExpandToggle: Consumer<StructureTagEntry>
    ) : Entry() {
        override fun render(
            graphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            val mc = Minecraft.getInstance()
            val indent = 10
            val expandIcon = if (expanded) "▼" else "▶"
            
            // 箭头
            graphics.drawString(mc.font, expandIcon, left + indent, top + 7, 0xAAAAAA, false)
            
            // Checkbox (手动绘制简单框)
            val boxSize = 10
            val boxX = left + indent + 12
            val boxY = top + 6

            graphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0xFF000000.toInt())
            graphics.fill(boxX + 1, boxY + 1, boxX + boxSize - 1, boxY + boxSize - 1, 0xFF888888.toInt())
            if (enabled) {
                graphics.fill(boxX + 2, boxY + 2, boxX + boxSize - 2, boxY + boxSize - 2, 0xFF55FF55.toInt())
            }

            // 文本
            val text = tag.displayName()
            graphics.drawString(mc.font, text, boxX + 14, top + 7, 0xDDDDDD, false)
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (button == 0) {
                val rowLeft = (list.width - list.getRowWidth()) / 2
                val indent = 10
                // 箭头区域
                if (mouseX >= (rowLeft + indent).toDouble() && mouseX <= (rowLeft + indent + 10).toDouble()) {
                    onExpandToggle.accept(tag)
                    return true
                }
                
                // 剩余区域 toggle checkbox
                if (mouseX > (rowLeft + indent + 10).toDouble() && mouseX <= (rowLeft + list.getRowWidth()).toDouble()) {
                    onToggle.accept(tag)
                    return true
                }
            }
            return super.mouseClicked(mouseX, mouseY, button)
        }
        
        override fun children(): List<GuiEventListener> = emptyList()
        override fun narratables(): List<NarratableEntry> = emptyList()
    }

    /**
     * 路径文件夹条目
     */
    class PathFolderEntry(
        private val list: StructureListWidget,
        private val pathNode: StructurePathNode,
        private val expanded: Boolean,
        private val allEnabled: Boolean,
        private val partialEnabled: Boolean,
        private val indent: Int,
        private val onExpandToggle: Consumer<StructurePathNode>,
        private val onSelectAllToggle: Consumer<StructurePathNode>
    ) : Entry() {
        override fun render(
            graphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            val mc = Minecraft.getInstance()
            val expandIcon = if (expanded) "▼" else "▶"
            
            graphics.drawString(mc.font, expandIcon, left + indent, top + 7, 0xAAAAAA, false)

            val boxSize = 10
            val boxX = left + indent + 12
            val boxY = top + 6
            
            graphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0xFF000000.toInt())
            graphics.fill(boxX + 1, boxY + 1, boxX + boxSize - 1, boxY + boxSize - 1, 0xFF888888.toInt())
            
            if (allEnabled) {
                graphics.fill(boxX + 2, boxY + 2, boxX + boxSize - 2, boxY + boxSize - 2, 0xFF55FF55.toInt())
            } else if (partialEnabled) {
                graphics.fill(boxX + 3, boxY + 3, boxX + boxSize - 3, boxY + boxSize - 3, 0xFFFFFF55.toInt())
            }

            val count = pathNode.getTotalStructureCount()
            val text = "${pathNode.name} ($count)"
            graphics.drawString(mc.font, text, boxX + 14, top + 7, 0xDDDDDD, false)
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (button == 0) {
                val rowLeft = (list.width - list.getRowWidth()) / 2
                // 箭头区域
                if (mouseX >= (rowLeft + indent).toDouble() && mouseX <= (rowLeft + indent + 10).toDouble()) {
                    onExpandToggle.accept(pathNode)
                    return true
                }
                // Checkbox 区域
                if (mouseX > (rowLeft + indent + 10).toDouble() && mouseX <= (rowLeft + list.getRowWidth()).toDouble()) {
                    onSelectAllToggle.accept(pathNode)
                    return true
                }
            }
            return super.mouseClicked(mouseX, mouseY, button)
        }

        override fun children(): List<GuiEventListener> = emptyList()
        override fun narratables(): List<NarratableEntry> = emptyList()
    }

    /**
     * 结构条目
     */
    class PathStructureEntry(
        private val list: StructureListWidget,
        private val structure: StructureEntry,
        private val enabled: Boolean,
        private val indent: Int,
        private val onToggle: Consumer<StructureEntry>
    ) : Entry() {
        override fun render(
            graphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            val mc = Minecraft.getInstance()
            
            val boxSize = 10
            val boxX = left + indent + 12
            val boxY = top + 6
            
            graphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0xFF000000.toInt())
            graphics.fill(boxX + 1, boxY + 1, boxX + boxSize - 1, boxY + boxSize - 1, 0xFF888888.toInt())
            if (enabled) {
                graphics.fill(boxX + 2, boxY + 2, boxX + boxSize - 2, boxY + boxSize - 2, 0xFF55FF55.toInt())
            }

            val name = StructurePathNode.getLeafName(structure)
            val color = if (structure.isVanilla) 0xFFFFFF else 0xFFAAAA
            graphics.drawString(mc.font, name, boxX + 14, top + 7, color, false)
            
            // 悬停显示完整 ID
            if (hovering && mouseX >= boxX + 14) {
                 graphics.renderTooltip(mc.font, Component.literal(structure.id().toString()), mouseX.toInt(), mouseY.toInt())
            }
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (button == 0) {
                val rowLeft = (list.width - list.getRowWidth()) / 2
                // 点击整行都可切换
                if (mouseX >= rowLeft.toDouble() && mouseX <= (rowLeft + list.getRowWidth()).toDouble()) {
                    onToggle.accept(structure)
                    return true
                }
            }
            return super.mouseClicked(mouseX, mouseY, button)
        }

        override fun children(): List<GuiEventListener> = emptyList()
        override fun narratables(): List<NarratableEntry> = emptyList()
    }

    class HeaderEntry(
        private val list: StructureListWidget,
        private val title: Component
    ) : Entry() {
        override fun render(
            graphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            val mc = Minecraft.getInstance()
            graphics.drawString(mc.font, title, left + 10, top + 7, 0xFFFF55, false)
        }
        override fun children(): List<GuiEventListener> = emptyList()
        override fun narratables(): List<NarratableEntry> = emptyList()
    }
    
    class MessageEntry(
        private val list: StructureListWidget,
        private val message: Component
    ) : Entry() {
        override fun render(
            graphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            val mc = Minecraft.getInstance()
            graphics.drawCenteredString(mc.font, message, left + width / 2, top + 7, 0xAAAAAA)
        }
        override fun children(): List<GuiEventListener> = emptyList()
        override fun narratables(): List<NarratableEntry> = emptyList()
    }
}
