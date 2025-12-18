package net.shiroha233.roadweaver.client.config

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService
import net.shiroha233.roadweaver.config.structure.StructureEntry
import net.shiroha233.roadweaver.config.structure.StructureSelectionConfig
import net.shiroha233.roadweaver.config.structure.StructureTagEntry
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/**
 * 结构选择界面 (Kotlin 重构版)
 */
class StructureSelectionScreen(private val parentScreen: Screen) :
    Screen(Component.translatable("config.roadweaver.structure_selection.title")) {

    private lateinit var listWidget: StructureListWidget
    private lateinit var searchBox: EditBox
    private var searchFilter = ""

    // 展开状态
    private val expandedTags = HashSet<String>()
    private val expandedMods = HashSet<String>()
    private val expandedPaths = HashSet<String>()

    companion object {
        private const val BASE_INDENT_TAG = 30
        private const val BASE_INDENT_ORPHAN = 10
        private const val INDENT_STEP = 15 // 每级缩进增加量
        private const val HEADER_HEIGHT = 50
        private const val FOOTER_HEIGHT = 40
    }

    override fun init() {
        // 搜索框
        searchBox = EditBox(font, width / 2 - 100, 22, 200, 18,
            Component.translatable("config.roadweaver.structure_selection.search"))
        searchBox.setHint(Component.translatable("config.roadweaver.structure_selection.search.hint"))
        searchBox.setResponder { text ->
            searchFilter = text.lowercase(Locale.ROOT)
            rebuildList()
        }
        addRenderableWidget(searchBox)

        // 列表组件
        val listTop = HEADER_HEIGHT
        val listBottom = height - FOOTER_HEIGHT
        listWidget = StructureListWidget(minecraft!!, width, listBottom - listTop, listTop)
        addRenderableWidget(listWidget)
        rebuildList()

        // 底部按钮
        val buttonY = height - 28
        val buttonWidth = 80
        val spacing = 5
        val totalWidth = buttonWidth * 4 + spacing * 3
        val startX = (width - totalWidth) / 2

        // 全选
        addRenderableWidget(Button.builder(
            Component.translatable("config.roadweaver.structure_selection.select_all")
        ) {
            StructureSelectionConfig.get().enableAll()
            rebuildList()
        }.pos(startX, buttonY).size(buttonWidth, 20).build())

        // 全不选
        addRenderableWidget(Button.builder(
            Component.translatable("config.roadweaver.structure_selection.deselect_all")
        ) {
            StructureSelectionConfig.get().clearAll()
            rebuildList()
        }.pos(startX + buttonWidth + spacing, buttonY).size(buttonWidth, 20).build())

        // 默认
        addRenderableWidget(Button.builder(
            Component.translatable("config.roadweaver.structure_selection.default")
        ) {
            val cfg = StructureSelectionConfig.get()
            cfg.clearAll()
            cfg.enableDefaultVillages()
            rebuildList()
        }.pos(startX + (buttonWidth + spacing) * 2, buttonY).size(buttonWidth, 20).build())

        // 完成
        addRenderableWidget(Button.builder(
            Component.translatable("gui.done")
        ) { onClose() }.pos(startX + (buttonWidth + spacing) * 3, buttonY).size(buttonWidth, 20).build())
    }

    private fun rebuildList() {
        if (!::listWidget.isInitialized) return
        listWidget.clearAllEntries()

        val result = StructureDiscoveryService.getResult()
        if (result == null) {
            listWidget.addEntryItem(StructureListWidget.MessageEntry(
                listWidget,
                Component.translatable("config.roadweaver.structure_selection.no_data")
            ))
            return
        }

        val config = StructureSelectionConfig.get()
        val addedStructures = HashSet<String>()

        // 收集所有 Mod ID
        val modIds = HashSet<String>()
        result.tags().forEach { modIds.add(it.namespace()) }
        result.allStructures().forEach { modIds.add(it.namespace()) }

        // 标签按模组分组
        val tagsByMod = result.tags().groupBy { it.namespace() }
            .mapValues { entry ->
                entry.value.sortedBy { it.displayName().lowercase(Locale.ROOT) }
            }

        // 模组排序
        val sortedModIds = modIds.sortedWith { a, b ->
            if (a == b) return@sortedWith 0
            if (a == "minecraft") return@sortedWith -1
            if (b == "minecraft") return@sortedWith 1
            if (a == "roadweaver") return@sortedWith -1
            if (b == "roadweaver") return@sortedWith 1
            val na = getModDisplayName(a).lowercase(Locale.ROOT)
            val nb = getModDisplayName(b).lowercase(Locale.ROOT)
            val cmp = na.compareTo(nb)
            if (cmp != 0) cmp else a.compareTo(b)
        }

        val hasSearch = searchFilter.isNotEmpty()

        for (modId in sortedModIds) {
            val modEntries = ArrayList<StructureListWidget.Entry>()
            val isModExpanded = hasSearch || expandedMods.contains(modId)
            val modDisplayName = getModDisplayName(modId)
            
            // 搜索匹配逻辑：如果搜索词匹配 Mod 名或 ID，则显示该 Mod 下所有内容
            val modMatchesFilter = hasSearch && (matchesFilter(modDisplayName) || matchesFilter(modId))
            var hasAnyForMod = false

            // 1. 处理标签
            val modTags = tagsByMod[modId] ?: emptyList()
            for (tag in modTags) {
                val tagMatchesFilter = matchesFilter(tag.displayName()) || matchesFilter(tag.tagId().toString())
                
                // 找出匹配的结构
                val matchingStructures = tag.structures().filter { 
                    matchesFilter(it.displayName()) || matchesFilter(it.id().toString()) 
                }

                // 决定是否显示该标签
                val shouldShowTag = modMatchesFilter || tagMatchesFilter || matchingStructures.isNotEmpty() || !hasSearch
                if (!shouldShowTag) continue

                hasAnyForMod = true
                val isTagEnabled = config.isTagEnabled(tag.tagId().toString())
                val isTagExpanded = expandedTags.contains(tag.tagId().toString())

                if (isModExpanded) {
                    modEntries.add(StructureListWidget.TagEntry(
                        listWidget, tag, isTagEnabled, isTagExpanded,
                        { onTagToggle(it) }, { onTagExpandToggle(it) }
                    ))

                    if (isTagExpanded) {
                        // 如果 Mod 匹配或 Tag 匹配，显示所有结构；否则只显示搜索匹配的结构
                        val baseList = if (!hasSearch || modMatchesFilter || tagMatchesFilter) {
                            tag.structures()
                        } else {
                            matchingStructures
                        }
                        
                        if (baseList.isNotEmpty()) {
                            val pathTree = StructurePathNode.buildTree(baseList, tag.namespace())
                            addPathNodeEntries(modEntries, pathTree, config, BASE_INDENT_TAG)
                            
                            // 记录已添加
                            tag.structures().forEach { addedStructures.add(it.id().toString()) }
                        }
                    } else {
                        // 折叠状态也要记录，防止作为孤立结构重复显示
                        tag.structures().forEach { addedStructures.add(it.id().toString()) }
                    }
                } else {
                    tag.structures().forEach { addedStructures.add(it.id().toString()) }
                }
            }

            // 2. 处理孤立结构
            val orphanStructures = result.allStructures().filter { 
                it.namespace() == modId && 
                !addedStructures.contains(it.id().toString()) &&
                (modMatchesFilter || matchesFilter(it.displayName()) || matchesFilter(it.id().toString()) || !hasSearch)
            }

            if (orphanStructures.isNotEmpty()) {
                hasAnyForMod = true
                if (isModExpanded) {
                    modEntries.add(StructureListWidget.HeaderEntry(
                        listWidget,
                        Component.translatable("config.roadweaver.structure_selection.other_structures")
                    ))
                    val pathTree = StructurePathNode.buildTree(orphanStructures, modId)
                    addPathNodeEntries(modEntries, pathTree, config, BASE_INDENT_ORPHAN)
                }
            }

            // 3. 添加 Mod 头
            if (hasAnyForMod) {
                val headerText = Component.literal("$modDisplayName [$modId]")
                listWidget.addEntryItem(StructureListWidget.ModHeaderEntry(
                    listWidget, modId, headerText, isModExpanded
                ) { onModHeaderToggle(it) })

                if (isModExpanded) {
                    modEntries.forEach { listWidget.addEntryItem(it) }
                }
            }
        }
    }

    private fun addPathNodeEntries(
        entries: MutableList<StructureListWidget.Entry>,
        node: StructurePathNode,
        config: StructureSelectionConfig,
        currentIndent: Int
    ) {
        val hasSearch = searchFilter.isNotEmpty()

        // 先处理子节点（文件夹）
        // 这里的 node 可能是 root 虚拟节点，也可能是递归中的某个节点
        // root 节点的 children 是第一级路径
        
        // 我们遍历 children
        for (child in node.children.values) {
            // 关键改进：根据 shouldShowAsFolder 判断是否作为文件夹显示
            if (child.shouldShowAsFolder()) {
                // 作为文件夹显示
                val isExpanded = hasSearch || expandedPaths.contains(child.fullPath)
                
                val allIds = child.getAllStructureIds()
                val enabledCount = allIds.count { config.isStructureEnabled(it) }
                val allEnabled = enabledCount == allIds.size && allIds.isNotEmpty()
                val partialEnabled = enabledCount > 0 && enabledCount < allIds.size
                
                entries.add(StructureListWidget.PathFolderEntry(
                    listWidget, child, isExpanded, allEnabled, partialEnabled,
                    currentIndent,
                    { onPathExpandToggle(it) }, { onPathSelectAllToggle(it) }
                ))

                if (isExpanded) {
                    // 递归添加，注意缩进增加
                    addPathNodeEntries(entries, child, config, currentIndent + INDENT_STEP)
                }
            } else {
                // 不作为文件夹显示，直接“打平”它的内容（递归调用，但缩进不增加，或者增加？
                // 如果不显示文件夹，那么它的子项应该直接展示在当前层级。
                // 递归调用 addPathNodeEntries 处理该节点的 children 和 structures
                // 此时 indent 是否增加？
                // 如果文件夹这层皮被剥掉了，子项应该和文件夹同级。
                // 所以 indent 保持不变 (或者 currentIndent)。
                addPathNodeEntries(entries, child, config, currentIndent)
            }
        }

        // 处理当前节点挂载的叶子结构
        for (structure in node.structures) {
            // 过滤
            if (hasSearch && !matchesFilter(structure.displayName()) && !matchesFilter(structure.id().toString())) {
                continue
            }
            
            val isEnabled = config.isStructureEnabled(structure.id().toString())
            // PathStructureEntry 使用 currentIndent
            entries.add(StructureListWidget.PathStructureEntry(
                listWidget, structure, isEnabled, currentIndent,
                { onStructureToggle(it) }
            ))
        }
    }

    private fun onPathExpandToggle(pathNode: StructurePathNode) {
        val path = pathNode.fullPath
        if (expandedPaths.contains(path)) expandedPaths.remove(path) else expandedPaths.add(path)
        rebuildList()
    }

    private fun onPathSelectAllToggle(pathNode: StructurePathNode) {
        val config = StructureSelectionConfig.get()
        val allIds = pathNode.getAllStructureIds()
        val allEnabled = allIds.all { config.isStructureEnabled(it) }

        if (allEnabled) {
            allIds.forEach { config.disableStructure(it) }
        } else {
            allIds.forEach { config.enableStructure(it) }
        }
        rebuildList()
    }

    private fun matchesFilter(text: String): Boolean {
        if (searchFilter.isEmpty()) return true
        return text.lowercase(Locale.ROOT).contains(searchFilter)
    }

    private fun onModHeaderToggle(modId: String) {
        if (expandedMods.contains(modId)) expandedMods.remove(modId) else expandedMods.add(modId)
        rebuildList()
    }

    private fun getModDisplayName(modId: String?): String {
        if (modId.isNullOrEmpty()) return "unknown"
        if (modId == "minecraft") return "Minecraft"
        if (modId == "roadweaver") return "RoadWeaver"
        return dev.architectury.platform.Platform.getOptionalMod(modId)
            .map { it.name }.orElse(modId)
    }

    private fun onTagToggle(tag: StructureTagEntry) {
        StructureSelectionConfig.get().toggleTag(tag.tagId().toString())
        rebuildList()
    }

    private fun onTagExpandToggle(tag: StructureTagEntry) {
        val id = tag.tagId().toString()
        if (expandedTags.contains(id)) expandedTags.remove(id) else expandedTags.add(id)
        rebuildList()
    }

    private fun onStructureToggle(structure: StructureEntry) {
        StructureSelectionConfig.get().toggleStructure(structure.id().toString())
        rebuildList()
    }

    override fun render(graphics: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF)
    }

    override fun onClose() {
        StructureSelectionConfig.get().save()
        minecraft!!.setScreen(parentScreen)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchBox.isFocused) return searchBox.keyPressed(keyCode, scanCode, modifiers)
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (searchBox.isFocused) return searchBox.charTyped(codePoint, modifiers)
        return super.charTyped(codePoint, modifiers)
    }
}
