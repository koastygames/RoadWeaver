package net.shiroha233.roadweaver.client.map.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图笔记编辑界面。
 */
public class NoteEditScreen extends Screen {
    private static final Identifier BOOK_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/book.png");
    private static final int BOOK_WIDTH = 192;
    private static final int BOOK_HEIGHT = 192;
    private static final int TEXT_WIDTH = 114;
    private static final int TEXT_X_OFFSET = 36;
    private static final int TEXT_Y_OFFSET = 32;
    private static final int MAX_LINES = 14;
    private static final int LINE_HEIGHT = 9;

    private final BlockPos targetPos;
    private final Screen parent;
    private final List<String> lines = new ArrayList<>();

    private int cursorLine = 0;
    private int cursorPos = 0;
    private int bookX;
    private int bookY;
    private long lastBlink;
    private boolean cursorVisible = true;

    public NoteEditScreen(BlockPos targetPos, Screen parent) {
        super(Component.translatable("gui.roadweaver.map.note.title"));
        this.targetPos = targetPos;
        this.parent = parent;

        List<String> existing = ClientMapNotes.getNotes(targetPos);
        if (!existing.isEmpty()) {
            this.lines.addAll(existing);
        } else {
            this.lines.add("");
        }
    }

    @Override
    protected void init() {
        this.bookX = (this.width - BOOK_WIDTH) / 2;
        this.bookY = (this.height - BOOK_HEIGHT) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.roadweaver.common.save"), button -> save())
                .bounds(bookX + BOOK_WIDTH / 2 - 50, bookY + BOOK_HEIGHT + 4, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BOOK_TEXTURE, bookX, bookY, 0.0F, 0.0F, BOOK_WIDTH, BOOK_HEIGHT, BOOK_WIDTH, BOOK_HEIGHT);

        String alias = ClientMapNotes.getAlias(targetPos);
        String title = alias != null && !alias.isEmpty()
                ? alias
                : String.format("(%d, %d)", targetPos.getX(), targetPos.getZ());
        graphics.drawString(this.font, title, bookX + TEXT_X_OFFSET, bookY + 18, 0xFF000000, false);

        int textX = bookX + TEXT_X_OFFSET;
        int textY = bookY + TEXT_Y_OFFSET;
        for (int i = 0; i < Math.min(lines.size(), MAX_LINES); i++) {
            graphics.drawString(this.font, lines.get(i), textX, textY + i * LINE_HEIGHT, 0xFF000000, false);
        }

        updateCursorBlink();
        if (cursorVisible && cursorLine < lines.size()) {
            String currentLine = lines.get(cursorLine);
            int cursorX = textX + this.font.width(currentLine.substring(0, Math.min(cursorPos, currentLine.length())));
            int cursorY = textY + cursorLine * LINE_HEIGHT;
            graphics.fill(cursorX, cursorY, cursorX + 1, cursorY + LINE_HEIGHT, 0xFF000000);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void updateCursorBlink() {
        long now = System.currentTimeMillis();
        if (now - lastBlink > 500) {
            cursorVisible = !cursorVisible;
            lastBlink = now;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == 256) {
            this.minecraft.setScreen(parent);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            if (lines.size() < MAX_LINES) {
                String current = lines.get(cursorLine);
                String before = current.substring(0, Math.min(cursorPos, current.length()));
                String after = current.substring(Math.min(cursorPos, current.length()));
                lines.set(cursorLine, before);
                lines.add(cursorLine + 1, after);
                cursorLine++;
                cursorPos = 0;
            }
            return true;
        }
        if (keyCode == 259) {
            if (cursorPos > 0) {
                String current = lines.get(cursorLine);
                lines.set(cursorLine, current.substring(0, cursorPos - 1) + current.substring(cursorPos));
                cursorPos--;
            } else if (cursorLine > 0) {
                String current = lines.remove(cursorLine);
                cursorLine--;
                String previous = lines.get(cursorLine);
                cursorPos = previous.length();
                lines.set(cursorLine, previous + current);
            }
            return true;
        }
        if (keyCode == 261) {
            String current = lines.get(cursorLine);
            if (cursorPos < current.length()) {
                lines.set(cursorLine, current.substring(0, cursorPos) + current.substring(cursorPos + 1));
            } else if (cursorLine < lines.size() - 1) {
                String next = lines.remove(cursorLine + 1);
                lines.set(cursorLine, current + next);
            }
            return true;
        }
        if (keyCode == 263) {
            if (cursorPos > 0) {
                cursorPos--;
            } else if (cursorLine > 0) {
                cursorLine--;
                cursorPos = lines.get(cursorLine).length();
            }
            return true;
        }
        if (keyCode == 262) {
            String current = lines.get(cursorLine);
            if (cursorPos < current.length()) {
                cursorPos++;
            } else if (cursorLine < lines.size() - 1) {
                cursorLine++;
                cursorPos = 0;
            }
            return true;
        }
        if (keyCode == 265) {
            if (cursorLine > 0) {
                cursorLine--;
                cursorPos = Math.min(cursorPos, lines.get(cursorLine).length());
            }
            return true;
        }
        if (keyCode == 264) {
            if (cursorLine < lines.size() - 1) {
                cursorLine++;
                cursorPos = Math.min(cursorPos, lines.get(cursorLine).length());
            }
            return true;
        }
        if (keyCode == 268) {
            cursorPos = 0;
            return true;
        }
        if (keyCode == 269) {
            cursorPos = lines.get(cursorLine).length();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char value = (char) event.codepoint();
        if (Character.isISOControl(value)) {
            return false;
        }
        String current = lines.get(cursorLine);
        String newLine = current.substring(0, cursorPos) + value + current.substring(cursorPos);
        if (this.font.width(newLine) <= TEXT_WIDTH) {
            lines.set(cursorLine, newLine);
            cursorPos++;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int textX = bookX + TEXT_X_OFFSET;
        int textY = bookY + TEXT_Y_OFFSET;
        if (mouseX >= textX && mouseX < textX + TEXT_WIDTH
                && mouseY >= textY && mouseY < textY + MAX_LINES * LINE_HEIGHT) {
            int clickedLine = (int) ((mouseY - textY) / LINE_HEIGHT);
            if (clickedLine < lines.size()) {
                cursorLine = clickedLine;
                String line = lines.get(cursorLine);
                int relativeX = (int) (mouseX - textX);
                cursorPos = 0;
                for (int i = 0; i <= line.length(); i++) {
                    if (this.font.width(line.substring(0, i)) >= relativeX) {
                        cursorPos = Math.max(0, i - 1);
                        break;
                    }
                    cursorPos = i;
                }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void save() {
        ClientMapNotes.clearNotes(targetPos);
        for (String line : lines) {
            if (!line.isEmpty()) {
                ClientMapNotes.addNote(targetPos, line);
            }
        }
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
