package net.shiroha233.roadweaver.features.path.decoration.system;

import net.shiroha233.roadweaver.features.path.decoration.base.Decoration;
import net.shiroha233.roadweaver.features.path.decoration.material.BiomeWoodAware;
import net.shiroha233.roadweaver.features.path.decoration.material.WoodSelector;

import java.util.Iterator;
import java.util.Set;

/**
 * 装饰执行器：负责实际放置装饰物。
 */
public final class DecorationExecutor {
    private DecorationExecutor() {}

    public static void tryPlaceDecorations(Set<Decoration> positions) {
        if (positions.isEmpty()) return;
        Iterator<Decoration> it = positions.iterator();
        while (it.hasNext()) {
            Decoration dec = it.next();
            if (dec == null) { it.remove(); continue; }
            if (dec instanceof BiomeWoodAware aware) {
                aware.setWoodType(WoodSelector.forBiome(dec.getWorld(), dec.getPos()));
            }
            dec.place();
            it.remove();
        }
    }
}
