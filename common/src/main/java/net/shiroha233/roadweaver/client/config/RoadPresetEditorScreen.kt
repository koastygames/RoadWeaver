package net.shiroha233.roadweaver.client.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.shiroha233.roadweaver.config.NaturalPresetService
import net.shiroha233.roadweaver.config.PresetService
import java.util.Locale

/**
 * 道路材质预设编辑器（Kotlin 版）。
 *
 * 支持：
 * - 人工道路预设：多文件 presets/<id>.json
 * - 自然道路预设：单文件 natural_presets.json（按群系一栏）
 */
class RoadPresetEditorScreen(private val parent: Screen?) : Screen(Component.translatable("gui.roadweaver.preset_editor.title")) {

    private enum class Tab {
        ARTIFICIAL,
        NATURAL
    }

    private enum class TargetList {
        BASE,
        SLAB
    }

    private data class UiPreset(
        var id: String,
        var name: String,
        var materials: MutableList<String> = mutableListOf(),
        var slabMaterials: MutableList<String> = mutableListOf()
    )

    private data class UiNaturalEntry(
        var biomeId: String,
        var materials: MutableList<String> = mutableListOf()
    )

    private var currentTab: Tab = Tab.ARTIFICIAL
    private var activeList: TargetList = TargetList.BASE

    private var loadedOnce = false

    private var searchBox: EditBox? = null
    private var nameBox: EditBox? = null
    private var biomeIdBox: EditBox? = null

    private var listWidget: RoadPresetListWidget? = null

    private val allBlocks: MutableList<Block> = ArrayList()
    private val filteredBlocks: MutableList<Block> = ArrayList()
    private var blockScrollOffset = 0

    private var visibleRightRows = 12
    private var visibleRightCols = 12

    private val activeMaterials: MutableList<String> = ArrayList()
    private val activeSlabMaterials: MutableList<String> = ArrayList()

    private val artificialPresets: MutableList<UiPreset> = ArrayList()
    private val originalArtificialIds: MutableList<String> = ArrayList()
    private var activeArtificialIndex = 0

    private val naturalEntries: MutableList<UiNaturalEntry> = ArrayList()
    private var activeNaturalIndex = 0

    override fun init() {
        super.init()
        clearWidgets()

        if (!loadedOnce) {
            reloadAllData()
            loadedOnce = true
        }

        if (allBlocks.isEmpty()) {
            buildCandidateBlocksFromCreativeTabs()
        }

        val headerH = 42
        val footerH = 36
        val padding = 10
        val centerX = width / 2

        val leftPanelX = padding
        val leftPanelW = 190

        val rightPanelW = 260
        val rightPanelX = width - padding - rightPanelW

        val middlePanelX = leftPanelX + leftPanelW + padding
        val middlePanelW = rightPanelX - padding - middlePanelX

        val tabY = 18
        val tabW = 90
        addRenderableWidget(
            Button.builder(Component.translatable("gui.roadweaver.preset_editor.tab.artificial")) { _: Button ->
                switchTab(Tab.ARTIFICIAL)
            }.bounds(centerX - tabW - 4, tabY, tabW, 18).build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("gui.roadweaver.preset_editor.tab.natural")) { _: Button ->
                switchTab(Tab.NATURAL)
            }.bounds(centerX + 4, tabY, tabW, 18).build()
        )

        val contentTop = headerH
        val contentBottom = height - footerH

        // 左侧列表
        val listTop = contentTop + 24
        val listHeight = contentBottom - listTop
        val lw = RoadPresetListWidget(minecraft!!, leftPanelW, listHeight, listTop) { idx ->
            onSelectLeftRow(idx)
        }
        // 对齐到左侧面板，避免列表项（选框/文字）与面板背景错位
        lw.setX(leftPanelX)
        this.listWidget = lw
        addRenderableWidget(lw)

        // 左侧顶部输入框（name 或 biomeId）
        if (currentTab == Tab.ARTIFICIAL) {
            val box = EditBox(font, leftPanelX, contentTop + 2, leftPanelW, 18, Component.translatable("gui.roadweaver.preset_editor.name"))
            box.setMaxLength(32)
            box.value = getActiveArtificial().name
            nameBox = box
            addRenderableWidget(box)

            val btnY = contentBottom - 22
            addRenderableWidget(
                Button.builder(Component.translatable("gui.roadweaver.preset_editor.new")) { _: Button -> onNewArtificial() }
                    .bounds(leftPanelX, btnY, 58, 18).build()
            )
            addRenderableWidget(
                Button.builder(Component.translatable("gui.roadweaver.preset_editor.rename")) { _: Button -> onRenameArtificial() }
                    .bounds(leftPanelX + 60, btnY, 62, 18).build()
            )
            addRenderableWidget(
                Button.builder(Component.translatable("gui.roadweaver.preset_editor.delete")) { _: Button -> onDeleteArtificial() }
                    .bounds(leftPanelX + 124, btnY, 58, 18).build()
            )
        } else {
            val box = EditBox(font, leftPanelX, contentTop + 2, leftPanelW, 18, Component.translatable("gui.roadweaver.preset_editor.biome_id"))
            box.setMaxLength(64)
            box.value = getActiveNatural().biomeId
            biomeIdBox = box
            addRenderableWidget(box)

            val btnY = contentBottom - 22
            addRenderableWidget(
                Button.builder(Component.translatable("gui.roadweaver.preset_editor.add_biome")) { _: Button -> onAddBiome() }
                    .bounds(leftPanelX, btnY, 62, 18).build()
            )
            addRenderableWidget(
                Button.builder(Component.translatable("gui.roadweaver.preset_editor.update_biome")) { _: Button -> onUpdateBiome() }
                    .bounds(leftPanelX + 64, btnY, 62, 18).build()
            )
            addRenderableWidget(
                Button.builder(Component.translatable("gui.roadweaver.preset_editor.delete_biome")) { _: Button -> onDeleteBiome() }
                    .bounds(leftPanelX + 128, btnY, 62, 18).build()
            )
        }

        // 右侧搜索框
        val search = EditBox(font, rightPanelX, contentTop + 2, rightPanelW, 18, Component.translatable("gui.roadweaver.preset_editor.search"))
        search.setMaxLength(64)
        search.setResponder { _ ->
            blockScrollOffset = 0
            rebuildFilteredList()
        }
        searchBox = search
        addRenderableWidget(search)

        // 右侧方块网格尺寸
        val gridTop = contentTop + 24
        val gridBottom = contentBottom - 6
        val availH = (gridBottom - gridTop).coerceAtLeast(18)
        visibleRightRows = (availH / 18).coerceIn(3, 20)
        visibleRightCols = (rightPanelW / 18).coerceIn(6, 25)

        // 底部保存/取消
        val saveW = 80
        val btnY = height - 28
        addRenderableWidget(
            Button.builder(Component.translatable("gui.roadweaver.common.save")) { _: Button -> onSave() }
                .bounds(centerX - saveW - 4, btnY, saveW, 20)
                .build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("gui.roadweaver.common.cancel")) { _: Button -> onCancel() }
                .bounds(centerX + 4, btnY, saveW, 20)
                .build()
        )

        // 刷新列表内容
        rebuildLeftList()
        rebuildFilteredList()
        loadActiveListsFromSelection()
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // 不调用 super：避免原版在游戏内 Screen 背景中启用模糊效果
        // 仅保留轻微暗化，保证不挡视野
        graphics.fill(0, 0, width, height, 0x22000000)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // 背景由 renderBackground() 统一处理（无模糊）

        val padding = 10
        val headerH = 42
        val footerH = 36
        val contentTop = headerH
        val contentBottom = height - footerH

        val leftPanelX = padding
        val leftPanelW = 190
        val rightPanelW = 260
        val rightPanelX = width - padding - rightPanelW
        val middlePanelX = leftPanelX + leftPanelW + padding
        val middlePanelW = rightPanelX - padding - middlePanelX

        // 背景面板
        graphics.fill(leftPanelX - 2, contentTop - 2, leftPanelX + leftPanelW + 2, contentBottom + 2, 0xAA0A0A0A.toInt())
        // 中间与右侧面板使用不透明底色，避免背景模糊透出
        graphics.fill(middlePanelX - 2, contentTop - 2, middlePanelX + middlePanelW + 2, contentBottom + 2, 0xFF0A0A0A.toInt())
        graphics.fill(rightPanelX - 2, contentTop - 2, rightPanelX + rightPanelW + 2, contentBottom + 2, 0xFF0A0A0A.toInt())

        graphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF)

        val midGridX = middlePanelX + 10
        val midGridY = contentTop + 28

        // 中间：材质槽位
        graphics.drawString(font, Component.translatable("gui.roadweaver.preset_editor.base_materials"), midGridX, contentTop + 10, 0xFFFFFF, false)
        renderMaterialGrid(graphics, midGridX, midGridY, activeMaterials, activeList == TargetList.BASE)

        val slabTop = midGridY + 4 * 18 + 22
        graphics.drawString(font, Component.translatable("gui.roadweaver.preset_editor.slab_materials"), midGridX, slabTop - 14, 0xFFFFFF, false)

        if (currentTab == Tab.ARTIFICIAL) {
            renderMaterialGrid(graphics, midGridX, slabTop, activeSlabMaterials, activeList == TargetList.SLAB)
        } else {
            // 自然道路不使用半砖：绘制灰色占位
            renderDisabledGrid(graphics, midGridX, slabTop)
        }

        // 右侧：方块选择网格
        val rightGridTop = contentTop + 24
        renderBlocksGrid(graphics, rightPanelX, rightGridTop)

        super.render(graphics, mouseX, mouseY, partialTick)
    }

    private fun renderMaterialGrid(g: GuiGraphics, startX: Int, startY: Int, list: List<String>, active: Boolean) {
        // 槽位背景使用不透明色，避免背后模糊透出
        val bg = if (active) 0xFF202020.toInt() else 0xFF141414.toInt()
        var idx = 0
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val x = startX + col * 18
                val y = startY + row * 18
                g.fill(x, y, x + 18, y + 18, bg)
                if (idx < list.size) {
                    val b = blockFromId(list[idx])
                    if (b != null && b != Blocks.AIR) {
                        g.renderFakeItem(ItemStack(b), x + 1, y + 1)
                    }
                }
                idx++
            }
        }
    }

    private fun renderDisabledGrid(g: GuiGraphics, startX: Int, startY: Int) {
        var idx = 0
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val x = startX + col * 18
                val y = startY + row * 18
                val bg = if (idx % 2 == 0) 0xFF1A1A1A.toInt() else 0xFF101010.toInt()
                g.fill(x, y, x + 18, y + 18, bg)
                idx++
            }
        }
    }

    private fun renderBlocksGrid(g: GuiGraphics, startX: Int, startY: Int) {
        val rows = visibleRightRows
        val cols = visibleRightCols
        val maxOffset = ((filteredBlocks.size + cols - 1) / cols - rows).coerceAtLeast(0)
        if (blockScrollOffset > maxOffset) blockScrollOffset = maxOffset
        if (blockScrollOffset < 0) blockScrollOffset = 0

        var index = blockScrollOffset * cols
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = startX + col * 18
                val y = startY + row * 18
                g.fill(x, y, x + 18, y + 18, 0xFF141414.toInt())
                if (index < filteredBlocks.size) {
                    val b = filteredBlocks[index]
                    g.renderFakeItem(ItemStack(b), x + 1, y + 1)
                }
                index++
            }
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true

        val padding = 10
        val headerH = 42
        val footerH = 36
        val contentTop = headerH
        val contentBottom = height - footerH

        val leftPanelX = padding
        val leftPanelW = 190
        val rightPanelW = 260
        val rightPanelX = width - padding - rightPanelW
        val middlePanelX = leftPanelX + leftPanelW + padding

        val midGridX = middlePanelX + 10
        val baseGridY = contentTop + 28
        val slabGridY = baseGridY + 4 * 18 + 22

        // 点击材质格子：左键删除，并设置当前编辑目标
        if (handleClickMaterialGrid(mouseX, mouseY, midGridX, baseGridY, TargetList.BASE, activeMaterials, button)) return true
        if (currentTab == Tab.ARTIFICIAL) {
            if (handleClickMaterialGrid(mouseX, mouseY, midGridX, slabGridY, TargetList.SLAB, activeSlabMaterials, button)) return true
        }

        // 点击右侧方块网格：添加到当前目标列表
        val rightGridTop = contentTop + 24
        if (handleClickBlocks(mouseX, mouseY, rightPanelX, rightGridTop, button)) return true

        return false
    }

    private fun handleClickMaterialGrid(
        mouseX: Double,
        mouseY: Double,
        startX: Int,
        startY: Int,
        target: TargetList,
        list: MutableList<String>,
        button: Int
    ): Boolean {
        var idx = 0
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val x = startX + col * 18
                val y = startY + row * 18
                if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                    activeList = target
                    if (button == 0 && idx < list.size) {
                        list.removeAt(idx)
                        return true
                    }
                    return true
                }
                idx++
            }
        }
        return false
    }

    private fun handleClickBlocks(mouseX: Double, mouseY: Double, startX: Int, startY: Int, button: Int): Boolean {
        if (button != 0) return false

        val rows = visibleRightRows
        val cols = visibleRightCols
        val x0 = startX
        val y0 = startY
        val w = cols * 18
        val h = rows * 18
        if (mouseX < x0 || mouseX >= x0 + w || mouseY < y0 || mouseY >= y0 + h) return false

        val col = ((mouseX - x0) / 18.0).toInt().coerceIn(0, cols - 1)
        val row = ((mouseY - y0) / 18.0).toInt().coerceIn(0, rows - 1)

        val index = (blockScrollOffset + row) * cols + col
        if (index < 0 || index >= filteredBlocks.size) return true

        val b = filteredBlocks[index]
        val id = BuiltInRegistries.BLOCK.getKey(b) ?: return true

        when (activeList) {
            TargetList.BASE -> if (activeMaterials.size < 16) activeMaterials.add(id.toString())
            TargetList.SLAB -> if (currentTab == Tab.ARTIFICIAL && activeSlabMaterials.size < 16) activeSlabMaterials.add(id.toString())
        }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double): Boolean {
        val padding = 10
        val headerH = 42
        val rightPanelW = 260
        val rightPanelX = width - padding - rightPanelW
        val gridTop = headerH + 24

        val cols = visibleRightCols
        val rows = visibleRightRows
        val w = cols * 18
        val h = rows * 18

        if (mouseX >= rightPanelX && mouseX < rightPanelX + w && mouseY >= gridTop && mouseY < gridTop + h) {
            if (deltaY > 0) blockScrollOffset--
            if (deltaY < 0) blockScrollOffset++
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY)
    }

    override fun isPauseScreen(): Boolean = false

    private fun switchTab(tab: Tab) {
        if (currentTab == tab) return
        saveActiveListsToSelection()
        currentTab = tab
        activeList = TargetList.BASE
        init()
    }

    private fun onSelectLeftRow(index: Int) {
        saveActiveListsToSelection()
        if (currentTab == Tab.ARTIFICIAL) {
            activeArtificialIndex = index.coerceIn(0, artificialPresets.size - 1)
            nameBox?.value = getActiveArtificial().name
        } else {
            activeNaturalIndex = index.coerceIn(0, naturalEntries.size - 1)
            biomeIdBox?.value = getActiveNatural().biomeId
        }
        loadActiveListsFromSelection()
        rebuildLeftList()
    }

    private fun getActiveArtificial(): UiPreset {
        if (artificialPresets.isEmpty()) {
            artificialPresets.add(UiPreset("custom_1", "Custom 1"))
            activeArtificialIndex = 0
        }
        val idx = activeArtificialIndex.coerceIn(0, artificialPresets.size - 1)
        return artificialPresets[idx]
    }

    private fun getActiveNatural(): UiNaturalEntry {
        if (naturalEntries.isEmpty()) {
            naturalEntries.add(UiNaturalEntry("minecraft:plains", mutableListOf("minecraft:dirt_path", "minecraft:gravel")))
            activeNaturalIndex = 0
        }
        val idx = activeNaturalIndex.coerceIn(0, naturalEntries.size - 1)
        return naturalEntries[idx]
    }

    private fun reloadAllData() {
        // 人工预设
        artificialPresets.clear()
        originalArtificialIds.clear()
        PresetService.reload()
        PresetService.getAllPresets().forEach { def ->
            artificialPresets.add(
                UiPreset(
                    id = def.id(),
                    name = def.name(),
                    materials = def.materials().toMutableList(),
                    slabMaterials = def.slabMaterials().toMutableList()
                )
            )
            originalArtificialIds.add(def.id())
        }
        if (artificialPresets.isEmpty()) {
            artificialPresets.add(UiPreset("custom_1", "Custom 1"))
            originalArtificialIds.add("custom_1")
        }

        // 自然预设
        naturalEntries.clear()
        NaturalPresetService.reload()
        val nat = NaturalPresetService.getAllEntries()
        nat.entries.forEach { (biomeId, mats) ->
            naturalEntries.add(UiNaturalEntry(biomeId, mats.toMutableList()))
        }
        naturalEntries.sortBy { it.biomeId.lowercase(Locale.ROOT) }
        if (naturalEntries.isEmpty()) {
            naturalEntries.add(UiNaturalEntry("minecraft:plains", mutableListOf("minecraft:dirt_path", "minecraft:gravel")))
        }

        activeArtificialIndex = activeArtificialIndex.coerceIn(0, artificialPresets.size - 1)
        activeNaturalIndex = activeNaturalIndex.coerceIn(0, naturalEntries.size - 1)
    }

    private fun loadActiveListsFromSelection() {
        activeMaterials.clear()
        activeSlabMaterials.clear()
        if (currentTab == Tab.ARTIFICIAL) {
            val p = getActiveArtificial()
            activeMaterials.addAll(p.materials.take(16))
            activeSlabMaterials.addAll(p.slabMaterials.take(16))
        } else {
            val e = getActiveNatural()
            activeMaterials.addAll(e.materials.take(16))
        }
    }

    private fun saveActiveListsToSelection() {
        if (currentTab == Tab.ARTIFICIAL) {
            val p = getActiveArtificial()
            val name = nameBox?.value?.trim().orEmpty().ifBlank { p.name }
            p.name = name
            p.materials = activeMaterials.take(16).toMutableList()
            p.slabMaterials = activeSlabMaterials.take(16).toMutableList()
        } else {
            val e = getActiveNatural()
            val biome = biomeIdBox?.value?.trim().orEmpty().ifBlank { e.biomeId }
            e.biomeId = biome
            e.materials = activeMaterials.take(16).toMutableList()
        }
    }

    private fun rebuildLeftList() {
        val rows = when (currentTab) {
            Tab.ARTIFICIAL -> artificialPresets.map { p ->
                RoadPresetListWidget.Row(Component.literal(p.name), Component.literal(p.id))
            }

            Tab.NATURAL -> naturalEntries.map { e ->
                val title = localizedBiomeName(e.biomeId)
                RoadPresetListWidget.Row(title, Component.literal(e.biomeId))
            }
        }

        val activeIdx = if (currentTab == Tab.ARTIFICIAL) activeArtificialIndex else activeNaturalIndex
        listWidget?.setRows(rows, activeIdx)
    }

    private fun localizedBiomeName(biomeId: String): Component {
        val rl = ResourceLocation.tryParse(biomeId) ?: return Component.literal(biomeId)
        val key = "biome.${rl.namespace}.${rl.path}"
        val translated = Component.translatable(key).string
        return if (translated == key) Component.literal(biomeId) else Component.translatable(key)
    }

    private fun rebuildFilteredList() {
        filteredBlocks.clear()
        val q = searchBox?.value?.trim()?.lowercase(Locale.ROOT).orEmpty()
        allBlocks.forEach { b ->
            val id = BuiltInRegistries.BLOCK.getKey(b) ?: return@forEach
            if (q.isEmpty() || id.toString().lowercase(Locale.ROOT).contains(q)) {
                filteredBlocks.add(b)
            }
        }
    }

    private fun onNewArtificial() {
        saveActiveListsToSelection()
        val baseName = Component.translatable("gui.roadweaver.preset_editor.default_name").string.ifBlank { "Preset" }
        val id = generateNewPresetId()
        val name = "$baseName ${artificialPresets.size + 1}"
        artificialPresets.add(UiPreset(id, name))
        activeArtificialIndex = artificialPresets.size - 1
        init()
    }

    private fun onRenameArtificial() {
        if (artificialPresets.isEmpty()) return
        val name = nameBox?.value?.trim().orEmpty()
        if (name.isEmpty()) return
        artificialPresets[activeArtificialIndex].name = name
        rebuildLeftList()
    }

    private fun onDeleteArtificial() {
        if (artificialPresets.isEmpty()) return
        artificialPresets.removeAt(activeArtificialIndex)
        if (artificialPresets.isEmpty()) {
            artificialPresets.add(UiPreset("custom_1", "Custom 1"))
            activeArtificialIndex = 0
        } else {
            activeArtificialIndex = activeArtificialIndex.coerceIn(0, artificialPresets.size - 1)
        }
        init()
    }

    private fun onAddBiome() {
        saveActiveListsToSelection()
        val id = biomeIdBox?.value?.trim().orEmpty()
        if (id.isEmpty()) return
        if (naturalEntries.any { it.biomeId == id }) return
        naturalEntries.add(UiNaturalEntry(id, mutableListOf("minecraft:dirt_path", "minecraft:gravel")))
        naturalEntries.sortBy { it.biomeId.lowercase(Locale.ROOT) }
        activeNaturalIndex = naturalEntries.indexOfFirst { it.biomeId == id }.coerceAtLeast(0)
        init()
    }

    private fun onUpdateBiome() {
        if (naturalEntries.isEmpty()) return
        val id = biomeIdBox?.value?.trim().orEmpty()
        if (id.isEmpty()) return
        val cur = getActiveNatural().biomeId
        if (id == cur) return
        if (naturalEntries.any { it.biomeId == id }) return
        getActiveNatural().biomeId = id
        naturalEntries.sortBy { it.biomeId.lowercase(Locale.ROOT) }
        activeNaturalIndex = naturalEntries.indexOfFirst { it.biomeId == id }.coerceAtLeast(0)
        init()
    }

    private fun onDeleteBiome() {
        if (naturalEntries.isEmpty()) return
        naturalEntries.removeAt(activeNaturalIndex)
        if (naturalEntries.isEmpty()) {
            naturalEntries.add(UiNaturalEntry("minecraft:plains", mutableListOf("minecraft:dirt_path", "minecraft:gravel")))
            activeNaturalIndex = 0
        } else {
            activeNaturalIndex = activeNaturalIndex.coerceIn(0, naturalEntries.size - 1)
        }
        init()
    }

    private fun onSave() {
        saveActiveListsToSelection()

        // 保存人工预设：写入文件 + 删除被移除的 id
        val currentIds = artificialPresets.map { it.id }.filter { it.isNotBlank() }.toSet()
        originalArtificialIds.forEach { oldId ->
            if (!currentIds.contains(oldId)) {
                PresetService.deletePresetFile(oldId)
            }
        }
        artificialPresets.forEach { p ->
            if (p.id.isBlank()) return@forEach
            PresetService.saveOrUpdatePresetFile(p.id, p.name, p.materials, p.slabMaterials)
        }
        PresetService.reload()

        // 保存自然预设：单文件
        val natMap = linkedMapOf<String, List<String>>()
        naturalEntries.forEach { e ->
            val biome = e.biomeId.trim()
            if (biome.isBlank()) return@forEach
            val mats = e.materials.map { it.trim() }.filter { it.isNotBlank() }
            if (mats.isEmpty()) return@forEach
            natMap[biome] = mats
        }
        NaturalPresetService.save(natMap)
        NaturalPresetService.reload()

        minecraft?.setScreen(parent)
    }

    private fun onCancel() {
        minecraft?.setScreen(parent)
    }

    private fun generateNewPresetId(): String {
        var i = 1
        while (true) {
            val id = "custom_$i"
            if (artificialPresets.none { it.id == id }) return id
            i++
        }
    }

    private fun buildCandidateBlocksFromCreativeTabs() {
        allBlocks.clear()

        val mc = Minecraft.getInstance()
        val player = mc.player
        val level = player?.level()
        if (player == null || level == null) {
            BuiltInRegistries.BLOCK.forEach { b -> if (b != Blocks.AIR) allBlocks.add(b) }
            return
        }

        val features: FeatureFlagSet = player.connection.enabledFeatures()
        val hasPermissions: Boolean = player.canUseGameMasterBlocks()
        val registries: HolderLookup.Provider = level.registryAccess()
        CreativeModeTabs.tryRebuildTabContents(features, hasPermissions, registries)

        val unique = linkedSetOf<Block>()
        addBlocksFromTab(unique, "building_blocks")
        addBlocksFromTab(unique, "natural_blocks")

        if (unique.isEmpty()) {
            BuiltInRegistries.BLOCK.forEach { b -> if (b != Blocks.AIR) unique.add(b) }
        }
        allBlocks.addAll(unique)
    }

    private fun addBlocksFromTab(out: MutableSet<Block>, tabId: String) {
        val key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.parse(tabId))
        val tab: CreativeModeTab? = BuiltInRegistries.CREATIVE_MODE_TAB.get(key)
        if (tab == null) return
        tab.displayItems.forEach { stack ->
            val item = stack.item
            if (item is BlockItem) {
                val b = item.block
                if (b != Blocks.AIR) out.add(b)
            }
        }
    }

    private fun blockFromId(id: String?): Block? {
        if (id == null) return Blocks.AIR
        return try {
            val rl = ResourceLocation.parse(id)
            BuiltInRegistries.BLOCK.get(rl)
        } catch (_: Exception) {
            Blocks.AIR
        }
    }
}
