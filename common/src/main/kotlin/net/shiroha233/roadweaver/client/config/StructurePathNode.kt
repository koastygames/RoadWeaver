package net.shiroha233.roadweaver.client.config

import net.minecraft.resources.ResourceLocation
import net.shiroha233.roadweaver.config.structure.StructureEntry
import java.util.*
import java.util.regex.Pattern

/**
 * 结构路径节点
 *
 * 用于将结构 ID 按分隔符（/、_）解析成树形结构，支持路径压缩和折叠。
 */
class StructurePathNode(
    var name: String,
    val fullPath: String,
    val depth: Int,
    val children: MutableMap<String, StructurePathNode> = LinkedHashMap(),
    val structures: MutableList<StructureEntry> = ArrayList()
) {
    companion object {
        private val SEPARATOR_PATTERN = Pattern.compile("[/_]")

        fun buildTree(structures: List<StructureEntry>, namespace: String): StructurePathNode {
            val root = StructurePathNode("", "$namespace:", 0)

            for (structure in structures) {
                val path = structure.path()
                val parts = SEPARATOR_PATTERN.split(path)

                if (parts.size <= 1) {
                    root.structures.add(structure)
                } else {
                    var current = root
                    val pathBuilder = StringBuilder(namespace).append(":")
                    
                    // 最后一个 part 是文件名/结构名，前面的都是路径
                    for (i in 0 until parts.size - 1) {
                        val part = parts[i]
                        if (part.isEmpty()) continue

                        if (pathBuilder.length > namespace.length + 1) {
                            pathBuilder.append("/")
                        }
                        pathBuilder.append(part)

                        val childPath = pathBuilder.toString()
                        current = current.children.computeIfAbsent(part) { k ->
                            StructurePathNode(k, childPath, current.depth + 1)
                        }
                    }
                    current.structures.add(structure)
                }
            }

            // 排序
            root.sortRecursively()

            return root
        }

        fun hasPathSeparator(path: String): Boolean {
            return path.contains('/') || path.contains('_')
        }

        fun getLeafName(structure: StructureEntry): String {
            val path = structure.path()
            val parts = SEPARATOR_PATTERN.split(path)
            return if (parts.isNotEmpty()) parts[parts.size - 1] else path
        }
    }

    private fun sortRecursively() {
        structures.sortWith(Comparator.comparing { it.path().lowercase(Locale.ROOT) })
        for (child in children.values) {
            child.sortRecursively()
        }
    }

    fun getAllStructures(): List<StructureEntry> {
        val result = ArrayList(structures)
        for (child in children.values) {
            result.addAll(child.getAllStructures())
        }
        return result
    }

    fun getAllStructureIds(): Set<String> {
        val result = LinkedHashSet<String>()
        structures.forEach { result.add(it.id().toString()) }
        children.values.forEach { result.addAll(it.getAllStructureIds()) }
        return result
    }
    
    /**
     * 获取该节点下的结构总数（递归）
     */
    fun getTotalStructureCount(): Int {
        var count = structures.size
        for (child in children.values) {
            count += child.getTotalStructureCount()
        }
        return count
    }

    /**
     * 是否应该作为文件夹显示
     * 
     * 改进逻辑：
     * 1. 如果总结构数只有 1 个，不管层级多深，都不作为文件夹，直接打平显示那个结构。
     * 2. 如果 structures 为空，且 children 只有一个，且该 child 也不显示的文件夹，那还是递归下去比较好。
     * 
     * 简单粗暴规则：递归后的总结构数 > 1 才显示为文件夹。
     * 如果总数 == 1，说明这整个分支只有 1 个结构，没必要折叠。
     */
    fun shouldShowAsFolder(): Boolean {
        if (getTotalStructureCount() <= 1) {
            return false
        }
        // 如果结构数 > 1，但都在同一层级（没有子文件夹），且数量 < 3，也可以不折叠
        if (children.isEmpty() && structures.size < 3) {
            return false
        }
        return true
    }
}
