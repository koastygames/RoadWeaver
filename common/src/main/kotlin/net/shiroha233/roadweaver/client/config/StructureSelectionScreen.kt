package net.shiroha233.roadweaver.client.config

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
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
    private var currentDimension: ResourceLocation? = null
    private var dimensionButton: Button? = null
    private var dimensionListWidget: DimensionListWidget? = null
    private var pendingCloseDimensionDropdown: Boolean = false

    // 展开状态
    private val expandedTags = HashSet<String>()
    private val expandedMods = HashSet<String>()
    private val expandedPaths = HashSet<String>()

    companion object {
        private const val BASE_INDENT_TAG = 30
        private const val BASE_INDENT_ORPHAN = 10
        private const val INDENT_STEP = 15 // 每级缩进增加量
        private const val HEADER_HEIGHT = 72 // 标题 + 搜索框 + 维度选择
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

        // 维度选择（动态下拉）
        val dimBtnY = 45
        val dimBtnW = 220
        val dimBtnH = 18
        val dimBtnX = width / 2 - dimBtnW / 2
        val btn = Button.builder(getDimensionButtonText()) {
            toggleDimensionDropdown()
        }.pos(dimBtnX, dimBtnY).size(dimBtnW, dimBtnH).build()
        dimensionButton = btn
        addRenderableWidget(btn)


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

        // 默认（仅村庄）
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
        dimensionButton?.active = result !== null
        updateDimensionButtonText()
        if (result === null) {
            closeDimensionDropdown()
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

            // 用于统计 Mod 下的结构是否全选
            val structuresInMod = ArrayList<String>()

            // 1. 处理标签
            val modTags = tagsByMod[modId] ?: emptyList()
            for (tag in modTags) {
                // 先做维度过滤，避免通过字符串二次查找
                val visibleStructures = tag.structures().filter { matchesDimension(it) }
                if (visibleStructures.isEmpty()) continue

                val tagMatchesFilter = matchesFilter(tag.displayName()) || matchesFilter(tag.tagId().toString())

                // 找出匹配搜索的结构
                val matchingStructures = visibleStructures.filter {
                    matchesFilter(it.displayName()) || matchesFilter(it.id().toString())
                }

                // 决定是否显示该标签
                val shouldShowTag = modMatchesFilter || tagMatchesFilter || matchingStructures.isNotEmpty() || !hasSearch
                if (!shouldShowTag) continue

                hasAnyForMod = true
                structuresInMod.addAll(visibleStructures.map { it.id().toString() })

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
                            visibleStructures
                        } else {
                            matchingStructures
                        }
                        
                        if (baseList.isNotEmpty()) {
                            val pathTree = StructurePathNode.buildTree(baseList, tag.namespace())
                            addPathNodeEntries(modEntries, pathTree, config, BASE_INDENT_TAG)
                            
                            // 记录已添加
                            baseList.forEach { addedStructures.add(it.id().toString()) }
                        }
                    } else {
                        // 折叠状态也要记录
                        visibleStructures.forEach { addedStructures.add(it.id().toString()) }
                    }
                } else {
                    visibleStructures.forEach { addedStructures.add(it.id().toString()) }
                }
            }

            // 2. 处理孤立结构
            val orphanStructures = result.allStructures().filter { 
                it.namespace() == modId && 
                matchesDimension(it) &&
                !addedStructures.contains(it.id().toString()) &&
                (modMatchesFilter || matchesFilter(it.displayName()) || matchesFilter(it.id().toString()) || !hasSearch)
            }

            if (orphanStructures.isNotEmpty()) {
                hasAnyForMod = true
                orphanStructures.forEach { structuresInMod.add(it.id().toString()) }

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
                // 计算 Mod 全选状态
                val enabledCount = structuresInMod.count { config.isStructureEnabled(it) }
                val modAllEnabled = structuresInMod.isNotEmpty() && enabledCount == structuresInMod.size
                val modPartialEnabled = enabledCount > 0 && enabledCount < structuresInMod.size

                val headerText = Component.literal("$modDisplayName [$modId]")
                listWidget.addEntryItem(StructureListWidget.ModHeaderEntry(
                    listWidget, modId, headerText, isModExpanded,
                    modAllEnabled, modPartialEnabled,
                    { onModHeaderToggle(it) },
                    { onModSelectAll(it, structuresInMod) }
                ))

                if (isModExpanded) {
                    modEntries.forEach { listWidget.addEntryItem(it) }
                }
            }
        }
    }

    private fun matchesDimension(entry: StructureEntry): Boolean {
        val dim = currentDimension ?: return true
        val dims = entry.dimensions()
        return dims.contains(dim)
    }

    private fun getDimensionButtonText(): Component {
        val name = if (currentDimension === null) {
            Component.translatable("config.roadweaver.structure_selection.dimension.all")
        } else {
            getDimensionDisplayName(currentDimension!!)
        }
        return Component.translatable("config.roadweaver.structure_selection.dimension", name)
    }

    private fun updateDimensionButtonText() {
        dimensionButton?.message = getDimensionButtonText()
    }

    private fun getDimensionDisplayName(dimId: ResourceLocation): Component {
        val key = "dimension.${dimId.namespace}.${dimId.path}"
        val translated = Component.translatable(key)
        return if (translated.string != key) translated else Component.literal(dimId.toString())
    }

    private fun toggleDimensionDropdown() {
        if (dimensionListWidget !== null) {
            closeDimensionDropdown()
        } else {
            openDimensionDropdown()
        }
    }

    private fun openDimensionDropdown() {
        val result = StructureDiscoveryService.getResult() ?: return
        val btn = dimensionButton ?: return

        val rows: MutableList<DimensionListWidget.Row> = ArrayList()
        rows.add(
            DimensionListWidget.Row(
                null,
                Component.translatable("config.roadweaver.structure_selection.dimension.all"),
                null
            )
        )

        for (dimId in result.dimensions()) {
            val title = getDimensionDisplayName(dimId)
            val subtitle = Component.literal(dimId.toString())
            rows.add(
                DimensionListWidget.Row(
                    dimId,
                    title,
                    if (title.string != subtitle.string) subtitle else null
                )
            )
        }

        val top = btn.y + btn.height + 2
        val maxH = (height - FOOTER_HEIGHT - top - 4).coerceAtLeast(44)
        val desiredRows = rows.size.coerceAtMost(8).coerceAtLeast(2)
        val listH = (desiredRows * 22).coerceAtMost(maxH)

        val list = DimensionListWidget(minecraft!!, btn.width, listH, top) { selected ->
            currentDimension = selected
            updateDimensionButtonText()
            // 注意：不能在 Screen.mouseClicked 遍历 children 时直接 removeWidget，否则可能触发并发修改异常。
            // 这里设置标记，等 super.mouseClicked 返回后再统一关闭。
            pendingCloseDimensionDropdown = true
            rebuildList()
        }
        list.setLeftPos(btn.x)
        list.setRenderBackground(false)
        list.setRenderTopAndBottom(false)
        list.setRows(rows, currentDimension)
        dimensionListWidget = list
        addRenderableWidget(list)
    }

    private fun closeDimensionDropdown() {
        val list = dimensionListWidget ?: return
        removeWidget(list)
        dimensionListWidget = null
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val dd = dimensionListWidget
        val btn = dimensionButton

        // 1. 优先处理下拉列表的点击（防止穿透）
        if (dd !== null && dd.isMouseOver(mouseX, mouseY)) {
            dd.mouseClicked(mouseX, mouseY, button)
            return true
        }

        // 2. 如果点击了下拉列表外部且没点到按钮，则关闭下拉
        if (dd !== null && btn !== null) {
            val clickedButton = btn.isMouseOver(mouseX, mouseY)
            val clickedDropdown = dd.isMouseOver(mouseX, mouseY)
            if (!clickedButton && !clickedDropdown) {
                closeDimensionDropdown()
            }
        }

        // 3. 传递给其他组件（如搜索框、列表等）
        val handled = super.mouseClicked(mouseX, mouseY, button)
        
        // 4. 处理延迟关闭
        if (pendingCloseDimensionDropdown) {
            pendingCloseDimensionDropdown = false
            closeDimensionDropdown()
        }
        return handled
    }

    private fun onModSelectAll(modId: String, structures: List<String>) {
        val config = StructureSelectionConfig.get()
        val allEnabled = structures.all { config.isStructureEnabled(it) }
        
        if (allEnabled) {
            structures.forEach { config.disableStructure(it) }
        } else {
            structures.forEach { config.enableStructure(it) }
        }
        rebuildList()
    }

    private fun addPathNodeEntries(
        entries: MutableList<StructureListWidget.Entry>,
        node: StructurePathNode,
        config: StructureSelectionConfig,
        currentIndent: Int
    ) {
        val hasSearch = searchFilter.isNotEmpty()

        // 先处理子节点（文件夹）
        for (child in node.children.values) {
            // 过滤文件夹中的内容
            val childStructureIds = child.getAllStructureIds()
            // 这里我们不需要再次过滤维度，因为 buildTree 时传入的 structures 已经是过滤过的
            
            if (childStructureIds.isEmpty()) continue

            if (child.shouldShowAsFolder()) {
                val isExpanded = hasSearch || expandedPaths.contains(child.fullPath)
                
                val enabledCount = childStructureIds.count { config.isStructureEnabled(it) }
                val allEnabled = enabledCount == childStructureIds.size && childStructureIds.isNotEmpty()
                val partialEnabled = enabledCount > 0 && enabledCount < childStructureIds.size
                
                entries.add(StructureListWidget.PathFolderEntry(
                    listWidget, child, isExpanded, allEnabled, partialEnabled,
                    currentIndent,
                    { onPathExpandToggle(it) }, { onPathSelectAllToggle(it) }
                ))

                if (isExpanded) {
                    addPathNodeEntries(entries, child, config, currentIndent + INDENT_STEP)
                }
            } else {
                addPathNodeEntries(entries, child, config, currentIndent)
            }
        }

        // 处理当前节点挂载的叶子结构
        for (structure in node.structures) {
            if (hasSearch && !matchesFilter(structure.displayName()) && !matchesFilter(structure.id().toString())) {
                continue
            }
            
            val isEnabled = config.isStructureEnabled(structure.id().toString())
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
