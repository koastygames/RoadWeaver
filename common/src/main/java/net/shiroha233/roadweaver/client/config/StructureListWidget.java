package net.shiroha233.roadweaver.client.config;

import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.config.structure.StructureEntry;
import net.shiroha233.roadweaver.config.structure.StructureTagEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 结构列表组件。
 */
public class StructureListWidget extends ContainerObjectSelectionList<StructureListWidget.Entry> {
    private static final Map<String, Optional<Identifier>> MOD_ICON_CACHE = new HashMap<>();
    private static final int ROW_HEIGHT = 24;
    private static final int CHECKBOX_SIZE = 10;

    public StructureListWidget(Minecraft minecraft, int width, int height, int top) {
        super(minecraft, width, height, top, ROW_HEIGHT);
        this.centerListVertically = false;
    }

    public void clearEntries() {
        super.clearEntries();
    }

    public int doAddEntry(Entry entry) {
        return super.addEntry(entry);
    }

    @Override
    public int getRowWidth() {
        return this.width - 40;
    }

    @Override
    protected int scrollBarX() {
        return this.getRowLeft() + this.getRowWidth() + 6;
    }

    static String getLocalizedStructureName(StructureEntry structure) {
        if (structure == null) {
            return "";
        }
        if (structure.isVanilla()) {
            Identifier id = structure.id();
            String key = "structure." + id.getNamespace() + "." + id.getPath();
            String translated = Component.translatable(key).getString();
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return structure.displayName();
    }

    static Identifier getModIconTexture(String modId) {
        if (modId == null || modId.isEmpty()) {
            return null;
        }
        Optional<Identifier> cached = MOD_ICON_CACHE.get(modId);
        if (cached != null) {
            return cached.orElse(null);
        }

        Identifier resolved = null;
        try {
            var optionalMod = Platform.getOptionalMod(modId);
            if (optionalMod.isPresent()) {
                var mod = optionalMod.get();
                var logoOptional = mod.getLogoFile(32);
                if (logoOptional.isPresent()) {
                    String logoPath = logoOptional.get();
                    List<Identifier> candidates = new ArrayList<>();
                    if (logoPath.startsWith("assets/")) {
                        String relative = logoPath.substring("assets/".length());
                        int slash = relative.indexOf('/');
                        if (slash >= 0) {
                            candidates.add(Identifier.fromNamespaceAndPath(relative.substring(0, slash), relative.substring(slash + 1)));
                        }
                    } else if (logoPath.indexOf(':') >= 0) {
                        Identifier identifier = Identifier.tryParse(logoPath);
                        if (identifier != null) {
                            candidates.add(identifier);
                        }
                    } else {
                        candidates.add(Identifier.fromNamespaceAndPath(modId, logoPath));
                        if (!logoPath.startsWith("textures/")) {
                            candidates.add(Identifier.fromNamespaceAndPath(modId, "textures/" + logoPath));
                            candidates.add(Identifier.fromNamespaceAndPath(modId, "textures/gui/" + logoPath));
                        }
                    }

                    var resourceManager = Minecraft.getInstance().getResourceManager();
                    for (Identifier candidate : candidates) {
                        if (candidate != null && resourceManager.getResource(candidate).isPresent()) {
                            resolved = candidate;
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        MOD_ICON_CACHE.put(modId, Optional.ofNullable(resolved));
        return resolved;
    }

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<StructureListWidget.Entry> {
        protected final StructureListWidget list;

        protected Entry(StructureListWidget list) {
            this.list = list;
        }
    }

    public static class ModHeaderEntry extends Entry {
        private final String modId;
        private final Component title;
        private final boolean expanded;
        private final boolean allEnabled;
        private final boolean partialEnabled;
        private final Consumer<String> onExpandToggle;
        private final Consumer<String> onSelectAll;

        public ModHeaderEntry(StructureListWidget list, String modId, Component title, boolean expanded, Consumer<String> onToggle) {
            this(list, modId, title, expanded, false, false, onToggle, ignored -> {
            });
        }

        public ModHeaderEntry(
                StructureListWidget list,
                String modId,
                Component title,
                boolean expanded,
                boolean allEnabled,
                boolean partialEnabled,
                Consumer<String> onExpandToggle,
                Consumer<String> onSelectAll
        ) {
            super(list);
            this.modId = modId;
            this.title = title;
            this.expanded = expanded;
            this.allEnabled = allEnabled;
            this.partialEnabled = partialEnabled;
            this.onExpandToggle = onExpandToggle;
            this.onSelectAll = onSelectAll;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int top, int left, boolean hovering, float partialTick) {
            top = this.getY();
            left = this.getX();
            Minecraft minecraft = Minecraft.getInstance();
            graphics.drawString(minecraft.font, expanded ? "v" : ">", left + 2, top + 7, 0xFFFFFFFF, false);

            int boxX = left + 14;
            int boxY = top + 6;
            graphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, 0xFF000000);
            graphics.fill(boxX + 1, boxY + 1, boxX + CHECKBOX_SIZE - 1, boxY + CHECKBOX_SIZE - 1, 0xFF888888);
            if (allEnabled) {
                graphics.fill(boxX + 2, boxY + 2, boxX + CHECKBOX_SIZE - 2, boxY + CHECKBOX_SIZE - 2, 0xFF55FF55);
            } else if (partialEnabled) {
                graphics.fill(boxX + 3, boxY + 3, boxX + CHECKBOX_SIZE - 3, boxY + CHECKBOX_SIZE - 3, 0xFFFFFF55);
            }

            int textX = left + 30;
            Identifier icon = getModIconTexture(modId);
            if (icon != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, icon, textX, top + 4, 0.0F, 0.0F, 16, 16, 16, 16);
                textX += 18;
            }

            graphics.drawString(minecraft.font, title, textX, top + 7, 0xFFFFFFFF, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0) {
                double mouseX = event.x();
                int rowLeft = this.list.getRowLeft();
                if (mouseX >= rowLeft && mouseX <= rowLeft + 14) {
                    onExpandToggle.accept(modId);
                    return true;
                }
                if (mouseX > rowLeft + 14 && mouseX <= rowLeft + 26) {
                    onSelectAll.accept(modId);
                    return true;
                }
                if (mouseX > rowLeft + 26 && mouseX <= rowLeft + this.list.getRowWidth()) {
                    onExpandToggle.accept(modId);
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    public static class MessageEntry extends Entry {
        private final Component message;

        public MessageEntry(StructureListWidget list, Component message) {
            super(list);
            this.message = message;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int top, int left, boolean hovering, float partialTick) {
            top = this.getY();
            left = this.getX();
            int width = list.getRowWidth();
            graphics.drawCenteredString(Minecraft.getInstance().font, message, left + width / 2, top + 5, 0xFFAAAAAA);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    public static class HeaderEntry extends Entry {
        private final Component title;

        public HeaderEntry(StructureListWidget list, Component title) {
            super(list);
            this.title = title;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int top, int left, boolean hovering, float partialTick) {
            top = this.getY();
            left = this.getX();
            graphics.drawString(Minecraft.getInstance().font, title, left + 5, top + 5, 0xFFFFFF00, false);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    public static class TagEntry extends Entry {
        private final StructureTagEntry tag;
        private final boolean enabled;
        private final boolean expanded;
        private final Consumer<StructureTagEntry> onToggle;
        private final Consumer<StructureTagEntry> onExpandToggle;

        public TagEntry(
                StructureListWidget list,
                StructureTagEntry tag,
                boolean enabled,
                boolean expanded,
                Consumer<StructureTagEntry> onToggle,
                Consumer<StructureTagEntry> onExpandToggle
        ) {
            super(list);
            this.tag = tag;
            this.enabled = enabled;
            this.expanded = expanded;
            this.onToggle = onToggle;
            this.onExpandToggle = onExpandToggle;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int top, int left, boolean hovering, float partialTick) {
            top = this.getY();
            left = this.getX();
            Minecraft minecraft = Minecraft.getInstance();
            int indent = 10;
            graphics.drawString(minecraft.font, expanded ? "v" : ">", left + indent, top + 7, 0xFFAAAAAA, false);

            int boxX = left + indent + 12;
            int boxY = top + 6;
            graphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, 0xFF000000);
            graphics.fill(boxX + 1, boxY + 1, boxX + CHECKBOX_SIZE - 1, boxY + CHECKBOX_SIZE - 1, 0xFF888888);
            if (enabled) {
                graphics.fill(boxX + 2, boxY + 2, boxX + CHECKBOX_SIZE - 2, boxY + CHECKBOX_SIZE - 2, 0xFF55FF55);
            }

            graphics.drawString(minecraft.font, tag.displayName(), boxX + 14, top + 7, 0xFFDDDDDD, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0) {
                double mouseX = event.x();
                int rowLeft = this.list.getRowLeft();
                int indent = 10;
                if (mouseX >= rowLeft + indent && mouseX <= rowLeft + indent + 10) {
                    onExpandToggle.accept(tag);
                    return true;
                }
                if (mouseX > rowLeft + indent + 10 && mouseX <= rowLeft + this.list.getRowWidth()) {
                    onToggle.accept(tag);
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    public static class PathFolderEntry extends Entry {
        private static final int INDENT_PER_LEVEL = 15;

        private final StructurePathNode pathNode;
        private final boolean expanded;
        private final boolean allEnabled;
        private final boolean partialEnabled;
        private final Consumer<StructurePathNode> onExpandToggle;
        private final Consumer<StructurePathNode> onSelectAllToggle;
        private final int baseIndent;

        public PathFolderEntry(
                StructureListWidget list,
                StructurePathNode pathNode,
                boolean expanded,
                boolean allEnabled,
                boolean partialEnabled,
                int baseIndent,
                Consumer<StructurePathNode> onExpandToggle,
                Consumer<StructurePathNode> onSelectAllToggle
        ) {
            super(list);
            this.pathNode = pathNode;
            this.expanded = expanded;
            this.allEnabled = allEnabled;
            this.partialEnabled = partialEnabled;
            this.baseIndent = baseIndent;
            this.onExpandToggle = onExpandToggle;
            this.onSelectAllToggle = onSelectAllToggle;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int top, int left, boolean hovering, float partialTick) {
            top = this.getY();
            left = this.getX();
            Minecraft minecraft = Minecraft.getInstance();
            int indent = baseIndent + (pathNode.depth() - 1) * INDENT_PER_LEVEL;

            graphics.drawString(minecraft.font, expanded ? "v" : ">", left + indent + 5, top + 7, 0xFFAAAAAA, false);

            int boxX = left + indent + 17;
            int boxY = top + 6;
            graphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, 0xFF000000);
            graphics.fill(boxX + 1, boxY + 1, boxX + CHECKBOX_SIZE - 1, boxY + CHECKBOX_SIZE - 1, 0xFF888888);
            if (allEnabled) {
                graphics.fill(boxX + 2, boxY + 2, boxX + CHECKBOX_SIZE - 2, boxY + CHECKBOX_SIZE - 2, 0xFF55FF55);
            } else if (partialEnabled) {
                graphics.fill(boxX + 3, boxY + 3, boxX + CHECKBOX_SIZE - 3, boxY + CHECKBOX_SIZE - 3, 0xFFFFFF55);
            }

            graphics.drawString(
                    minecraft.font,
                    pathNode.name() + " (" + pathNode.getTotalStructureCount() + ")",
                    boxX + 14,
                    top + 7,
                    0xFFDDDDDD,
                    false
            );
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0) {
                double mouseX = event.x();
                int rowLeft = this.list.getRowLeft();
                int indent = baseIndent + (pathNode.depth() - 1) * INDENT_PER_LEVEL;
                if (mouseX >= rowLeft + indent && mouseX <= rowLeft + indent + 10) {
                    onExpandToggle.accept(pathNode);
                    return true;
                }
                if (mouseX > rowLeft + indent + 10 && mouseX <= rowLeft + this.list.getRowWidth()) {
                    onSelectAllToggle.accept(pathNode);
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    public static class PathStructureEntry extends Entry {
        private static final int INDENT_PER_LEVEL = 15;

        private final StructureEntry structure;
        private final boolean enabled;
        private final Consumer<StructureEntry> onToggle;
        private final int baseIndent;
        private final int depth;

        public PathStructureEntry(
                StructureListWidget list,
                StructureEntry structure,
                boolean enabled,
                int baseIndent,
                int depth,
                Consumer<StructureEntry> onToggle
        ) {
            super(list);
            this.structure = structure;
            this.enabled = enabled;
            this.baseIndent = baseIndent;
            this.depth = depth;
            this.onToggle = onToggle;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int top, int left, boolean hovering, float partialTick) {
            top = this.getY();
            left = this.getX();
            Minecraft minecraft = Minecraft.getInstance();
            int indent = baseIndent + depth * INDENT_PER_LEVEL;
            int boxX = left + indent + 12;
            int boxY = top + 6;
            graphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, 0xFF000000);
            graphics.fill(boxX + 1, boxY + 1, boxX + CHECKBOX_SIZE - 1, boxY + CHECKBOX_SIZE - 1, 0xFF888888);
            if (enabled) {
                graphics.fill(boxX + 2, boxY + 2, boxX + CHECKBOX_SIZE - 2, boxY + CHECKBOX_SIZE - 2, 0xFF55FF55);
            }

            graphics.drawString(
                    minecraft.font,
                    StructurePathNode.getLeafName(structure),
                    boxX + 14,
                    top + 7,
                    structure.isVanilla() ? 0xFFFFFFFF : 0xFFFFAAAA,
                    false
            );

            if (hovering) {
                graphics.setTooltipForNextFrame(minecraft.font, Component.literal(structure.id().toString()), (int) Math.round(minecraft.mouseHandler.xpos()), (int) Math.round(minecraft.mouseHandler.ypos()));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0) {
                double mouseX = event.x();
                int rowLeft = this.list.getRowLeft();
                if (mouseX >= rowLeft && mouseX <= rowLeft + this.list.getRowWidth()) {
                    onToggle.accept(structure);
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }
}
