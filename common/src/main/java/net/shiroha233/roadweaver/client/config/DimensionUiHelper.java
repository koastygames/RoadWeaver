package net.shiroha233.roadweaver.client.config;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import net.shiroha233.roadweaver.config.structure.StructureEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class DimensionUiHelper {
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final ResourceLocation THE_NETHER = ResourceLocation.withDefaultNamespace("the_nether");
    private static final ResourceLocation THE_END = ResourceLocation.withDefaultNamespace("the_end");

    private DimensionUiHelper() {
    }

    public static StructureDiscoveryService.DiscoveryResult getLatestDiscoveryResult() {
        StructureDiscoveryService.tryDiscoverFromCurrentContext();
        return StructureDiscoveryService.getResult();
    }

    public static List<ResourceLocation> collectDimensions(StructureDiscoveryService.DiscoveryResult result, Collection<ResourceLocation> extras) {
        LinkedHashSet<ResourceLocation> ordered = new LinkedHashSet<>();

        if (result != null) {
            ordered.addAll(result.dimensions());
            for (StructureEntry entry : result.allStructures()) {
                ordered.addAll(entry.dimensions());
            }
        }

        if (extras != null) {
            for (ResourceLocation extra : extras) {
                if (extra != null) {
                    ordered.add(extra);
                }
            }
        }

        ordered.add(OVERWORLD);
        ordered.add(THE_NETHER);
        ordered.add(THE_END);

        List<ResourceLocation> dimensions = new ArrayList<>(ordered);
        dimensions.sort(DimensionUiHelper::compareDimensions);
        return dimensions;
    }

    public static List<DimensionListWidget.Row> buildRows(Collection<ResourceLocation> dimensions, boolean includeAllOption) {
        List<DimensionListWidget.Row> rows = new ArrayList<>();
        if (includeAllOption) {
            rows.add(new DimensionListWidget.Row(null,
                    Component.translatable("config.roadweaver.structure_selection.dimension.all"),
                    null));
        }

        if (dimensions == null) {
            return rows;
        }

        for (ResourceLocation dimId : dimensions) {
            if (dimId == null) {
                continue;
            }
            Component title = getDisplayName(dimId);
            Component subtitle = Component.literal(dimId.toString());
            rows.add(new DimensionListWidget.Row(dimId, title,
                    !Objects.equals(title.getString(), subtitle.getString()) ? subtitle : null));
        }
        return rows;
    }

    public static Component getDisplayName(ResourceLocation dimId) {
        if (dimId == null) {
            return Component.translatable("config.roadweaver.structure_selection.dimension.all");
        }

        String key = "dimension." + dimId.getNamespace() + "." + dimId.getPath();
        Component translated = Component.translatable(key);
        if (!Objects.equals(translated.getString(), key)) {
            return translated;
        }
        return Component.literal(dimId.toString());
    }

    private static int compareDimensions(ResourceLocation a, ResourceLocation b) {
        int aRank = dimensionRank(a);
        int bRank = dimensionRank(b);
        if (aRank != bRank) {
            return Integer.compare(aRank, bRank);
        }
        return a.toString().compareTo(b.toString());
    }

    private static int dimensionRank(ResourceLocation id) {
        if (OVERWORLD.equals(id)) {
            return 0;
        }
        if (THE_NETHER.equals(id)) {
            return 1;
        }
        if (THE_END.equals(id)) {
            return 2;
        }
        return 10;
    }
}
