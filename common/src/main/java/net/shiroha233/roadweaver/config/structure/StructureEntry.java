package net.shiroha233.roadweaver.config.structure;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 单个结构条目，用于 GUI 展示和选择
 */
public final class StructureEntry implements Comparable<StructureEntry> {
    private final Identifier id;
    private final String displayName;
    private final boolean isVanilla;
    private final Set<Identifier> dimensions;

    public StructureEntry(Identifier id, String displayName, boolean isVanilla) {
        this(id, displayName, isVanilla, Set.of());
    }

    public StructureEntry(Identifier id, String displayName, boolean isVanilla, Set<Identifier> dimensions) {
        this.id = id;
        this.displayName = displayName;
        this.isVanilla = isVanilla;
        this.dimensions = dimensions == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(dimensions));
    }

    public Identifier id() { return id; }
    public String displayName() { return displayName; }
    public boolean isVanilla() { return isVanilla; }
    public Set<Identifier> dimensions() { return dimensions; }
    public String namespace() { return id.getNamespace(); }
    public String path() { return id.getPath(); }

    @Override
    public int compareTo(StructureEntry other) {
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
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() { return id.toString(); }
}
