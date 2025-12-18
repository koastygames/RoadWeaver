package net.shiroha233.roadweaver.config.structure

import net.minecraft.resources.ResourceLocation
import java.util.Objects

/**
 * 单个结构的条目信息
 *
 * 用于在 GUI 中展示和选择结构
 */
class StructureEntry(
    private val id: ResourceLocation,
    private val displayName: String,
    @get:JvmName("isVanillaFlag")
    val isVanilla: Boolean,
    val dimensions: Set<ResourceLocation> = emptySet()
) : Comparable<StructureEntry> {

    fun id(): ResourceLocation = id

    fun displayName(): String = displayName

    fun isVanilla(): Boolean = isVanilla

    fun dimensions(): Set<ResourceLocation> = dimensions

    /**
     * 获取结构的命名空间
     */
    fun namespace(): String = id.namespace

    /**
     * 获取结构的路径（不含命名空间）
     */
    fun path(): String = id.path

    override fun compareTo(other: StructureEntry): Int {
        // 原版优先，然后按命名空间，最后按路径排序
        if (this.isVanilla() != other.isVanilla()) {
            return if (this.isVanilla()) -1 else 1
        }
        val nsCompare = this.namespace().compareTo(other.namespace())
        if (nsCompare != 0) return nsCompare
        return this.path().compareTo(other.path())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StructureEntry) return false
        return Objects.equals(id, other.id)
    }

    override fun hashCode(): Int = Objects.hash(id)

    override fun toString(): String = id.toString()
}
