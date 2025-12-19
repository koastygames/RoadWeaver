package net.shiroha233.roadweaver.client.config;

import net.shiroha233.roadweaver.config.structure.StructureEntry;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 结构路径节点
 * 
 * 用于将结构 ID 按分隔符（/、_）解析成树形结构，支持折叠显示。
 * 例如：mvs:house/small/1 会被解析为 house -> small -> 1
 */
public final class StructurePathNode {
    // 支持的分隔符：/ 和 _
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[/_]");
    
    private final String name;           // 当前节点名称
    private final String fullPath;       // 完整路径（用于唯一标识）
    private final int depth;             // 深度（用于缩进）
    private final Map<String, StructurePathNode> children = new LinkedHashMap<>();
    private final List<StructureEntry> structures = new ArrayList<>();  // 叶子结构
    
    public StructurePathNode(String name, String fullPath, int depth) {
        this.name = name;
        this.fullPath = fullPath;
        this.depth = depth;
    }
    
    /**
     * 从结构列表构建路径树
     * 
     * @param structures 结构列表
     * @param namespace 命名空间（用于构建完整路径标识）
     * @return 根节点（虚拟节点，children 是顶层路径）
     */
    public static StructurePathNode buildTree(List<StructureEntry> structures, String namespace) {
        StructurePathNode root = new StructurePathNode("", namespace + ":", 0);
        
        for (StructureEntry structure : structures) {
            String path = structure.path();
            String[] parts = SEPARATOR_PATTERN.split(path);
            
            if (parts.length <= 1) {
                // 没有分隔符，直接作为叶子节点
                root.structures.add(structure);
            } else {
                // 有分隔符，构建树
                StructurePathNode current = root;
                StringBuilder pathBuilder = new StringBuilder(namespace).append(":");
                
                for (int i = 0; i < parts.length - 1; i++) {
                    String part = parts[i];
                    if (part.isEmpty()) continue;
                    
                    if (pathBuilder.length() > namespace.length() + 1) {
                        pathBuilder.append("/");
                    }
                    pathBuilder.append(part);
                    
                    String childPath = pathBuilder.toString();
                    final int currentDepth = current.depth;
                    current = current.children.computeIfAbsent(part, 
                            k -> new StructurePathNode(part, childPath, currentDepth + 1));
                }
                
                // 最后一个部分作为叶子结构
                current.structures.add(structure);
            }
        }
        
        // 对子节点和结构进行排序
        sortRecursively(root);
        
        return root;
    }
    
    private static void sortRecursively(StructurePathNode node) {
        // 按名称排序结构
        node.structures.sort(Comparator.comparing(s -> s.path().toLowerCase(Locale.ROOT)));
        
        // 递归排序子节点
        for (StructurePathNode child : node.children.values()) {
            sortRecursively(child);
        }
    }
    
    /**
     * 获取节点名称
     */
    public String name() {
        return name;
    }
    
    /**
     * 获取完整路径（用于唯一标识）
     */
    public String fullPath() {
        return fullPath;
    }
    
    /**
     * 获取深度
     */
    public int depth() {
        return depth;
    }
    
    /**
     * 获取子节点
     */
    public Collection<StructurePathNode> children() {
        return children.values();
    }
    
    /**
     * 获取直接挂载的结构（叶子）
     */
    public List<StructureEntry> structures() {
        return Collections.unmodifiableList(structures);
    }
    
    /**
     * 是否有子节点或结构
     */
    public boolean hasContent() {
        return !children.isEmpty() || !structures.isEmpty();
    }
    
    /**
     * 是否只有叶子结构（没有子文件夹）
     */
    public boolean isLeafFolder() {
        return children.isEmpty() && !structures.isEmpty();
    }
    
    /**
     * 递归获取所有结构（包括子节点中的）
     */
    public List<StructureEntry> getAllStructures() {
        List<StructureEntry> result = new ArrayList<>(structures);
        for (StructurePathNode child : children.values()) {
            result.addAll(child.getAllStructures());
        }
        return result;
    }
    
    /**
     * 递归获取所有结构 ID
     */
    public Set<String> getAllStructureIds() {
        Set<String> result = new LinkedHashSet<>();
        for (StructureEntry structure : structures) {
            result.add(structure.id().toString());
        }
        for (StructurePathNode child : children.values()) {
            result.addAll(child.getAllStructureIds());
        }
        return result;
    }
    
    /**
     * 获取该节点下的结构总数（递归）
     */
    public int getTotalStructureCount() {
        int count = structures.size();
        for (StructurePathNode child : children.values()) {
            count += child.getTotalStructureCount();
        }
        return count;
    }
    
    /**
     * 检查是否需要显示为可折叠的文件夹
     * 只有当子节点数量 > 0 或结构数量 > 阈值时才显示为文件夹
     */
    public boolean shouldShowAsFolder() {
        // 有子文件夹时总是显示
        if (!children.isEmpty()) {
            return true;
        }
        // 同一路径下结构数量 >= 3 时才显示为可折叠
        return structures.size() >= 3;
    }
    
    /**
     * 检查结构路径是否包含分隔符（判断是否需要路径折叠）
     */
    public static boolean hasPathSeparator(String path) {
        return path.contains("/") || path.contains("_");
    }
    
    /**
     * 获取结构的显示名称（去掉路径前缀）
     */
    public static String getLeafName(StructureEntry structure) {
        String path = structure.path();
        String[] parts = SEPARATOR_PATTERN.split(path);
        return parts.length > 0 ? parts[parts.length - 1] : path;
    }
}
