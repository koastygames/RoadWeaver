package net.shiroha233.roadweaver.config.structure

import net.minecraft.resources.ResourceLocation
import java.util.Collections
import java.util.Objects

/**
 * 结构标签条目
 *
 * 表示一个结构标签及其包含的所有结构
 */
class StructureTagEntry(
    private val tagId: ResourceLocation,
    private val displayName: String,
    structures: List<StructureEntry>
) : Comparable<StructureTagEntry> {

    private val structures: MutableList<StructureEntry> = ArrayList(structures)
    private val isVanilla: Boolean

    init {
        Collections.sort(this.structures)
        this.isVanilla = "minecraft" == tagId.namespace
    }

    fun tagId(): ResourceLocation = tagId

    fun displayName(): String = displayName

    fun structures(): List<StructureEntry> = Collections.unmodifiableList(structures)

    fun isVanilla(): Boolean = isVanilla

    /**
     * 获取标签的命名空间
     */
    fun namespace(): String = tagId.namespace

    /**
     * 获取标签形式的字符串（带 # 前缀）
     */
    fun tagString(): String = "#" + tagId.toString()

    /**
     * 获取此标签下所有结构的 ID 集合
     */
    fun getAllStructureIds(): Set<String> {
        val ids: MutableSet<String> = HashSet()
        for (entry in structures) {
            ids.add(entry.id().toString())
        }
        return ids
    }

    override fun compareTo(other: StructureTagEntry): Int {
        // 原版优先
        if (this.isVanilla != other.isVanilla) {
            return if (this.isVanilla) -1 else 1
        }
        return this.tagId.compareTo(other.tagId)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StructureTagEntry) return false
        return Objects.equals(tagId, other.tagId)
    }

    override fun hashCode(): Int = Objects.hash(tagId)
}
