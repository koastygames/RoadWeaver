package net.shiroha233.roadweaver.config.structure;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 单个结构的条目信息
 * 
 * 用于在 GUI 中展示和选择结构
 */
public final class StructureEntry implements Comparable<StructureEntry> {
    private final ResourceLocation id;
    private final String displayName;
    private final boolean isVanilla;
    
    public StructureEntry(ResourceLocation id, String displayName, boolean isVanilla) {
        this.id = id;
        this.displayName = displayName;
        this.isVanilla = isVanilla;
    }
    
    public ResourceLocation id() {
        return id;
    }
    
    public String displayName() {
        return displayName;
    }
    
    public boolean isVanilla() {
        return isVanilla;
    }
    
    /**
     * 获取结构的命名空间
     */
    public String namespace() {
        return id.getNamespace();
    }
    
    /**
     * 获取结构的路径（不含命名空间）
     */
    public String path() {
        return id.getPath();
    }
    
    @Override
    public int compareTo(StructureEntry other) {
        // 原版优先，然后按命名空间，最后按路径排序
        if (this.isVanilla != other.isVanilla) {
            return this.isVanilla ? -1 : 1;
        }
        int nsCompare = this.namespace().compareTo(other.namespace());
        if (nsCompare != 0) return nsCompare;
        return this.path().compareTo(other.path());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StructureEntry that)) return false;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return id.toString();
    }
}
