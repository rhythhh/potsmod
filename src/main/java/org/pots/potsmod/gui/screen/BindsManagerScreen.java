package org.pots.potsmod.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.gui.GuiStyle;
import org.pots.potsmod.gui.render.UiRect;
import org.pots.potsmod.gui.theme.GuiTheme;
import org.pots.potsmod.models.bind.CustomBindEntry;
import org.pots.potsmod.render.Render2D;
import org.pots.potsmod.util.KeyUtil;

import java.util.ArrayList;
import java.util.List;

public class BindsManagerScreen extends Screen {
    public static final int MAX_BINDS = 50;

    private static final int COLOR_ACCENT = 0xFFD48B30;
    private static final int COLOR_ACCENT_BRIGHT = 0xFFF5B545;
    private static final int COLOR_REMOVE = 0xFFFF6B6B;
    private static final int COLOR_EDIT = 0xFF60A5FA;
    private static final int COLOR_KEYBIND = 0xFFF59E0B;

    private final Screen parent;
    private final List<CustomBindEntry> binds = new ArrayList<>();
    private final List<EditBox> activeInputBoxes = new ArrayList<>();

    private BindsManagerLayout layout;
    private double scroll;
    private double scrollTarget;
    private boolean draggingScrollbar;
    private double scrollbarDragOffset;

    // Editing title state
    private String editingBindId = null;
    private EditBox titleEditBox = null;

    // Capturing keybind state
    private String capturingBindId = null;

    public BindsManagerScreen(Screen parent) {
        super(Component.literal("Binds"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout = BindsManagerLayout.fromScreen(width, height);
        reloadBinds();
        rebuildInputs();
    }

    private void reloadBinds() {
        binds.clear();
        binds.addAll(PotsmodConfig.getData().getCustomBinds());
        clampScroll();
    }

    private void rebuildInputs() {
        for (EditBox box : activeInputBoxes) {
            removeWidget(box);
        }
        activeInputBoxes.clear();

        if (titleEditBox != null) {
            removeWidget(titleEditBox);
            titleEditBox = null;
        }

        if (editingBindId != null) {
            CustomBindEntry editing = getBindById(editingBindId);
            if (editing != null) {
                titleEditBox = new EditBox(font, 0, 0, 150, 16, GuiStyle.styled("Bind Name"));
                titleEditBox.setBordered(false);
                titleEditBox.setHeight(16);
                titleEditBox.setTextColor(0xFFFFFFFF);
                titleEditBox.setMaxLength(32);
                titleEditBox.setValue(editing.getName());
                titleEditBox.setResponder(val -> {
                    editing.setName(val);
                    PotsmodConfig.save();
                });
                addRenderableWidget(titleEditBox);
                titleEditBox.setFocused(true);
                setFocused(titleEditBox);
            } else {
                editingBindId = null;
            }
        }

        for (int i = 0; i < binds.size(); i++) {
            CustomBindEntry bind = binds.get(i);
            EditBox cmdBox = new EditBox(font, 0, 0, 100, 16, GuiStyle.styled("Command"));
            cmdBox.setBordered(false);
            cmdBox.setHeight(16);
            cmdBox.setTextColor(0xFFE2E8F0);
            cmdBox.setTextColorUneditable(0x82FFFFFF);
            cmdBox.setMaxLength(256);
            GuiStyle.applyFont(cmdBox, "Type command (without /)...");
            cmdBox.setValue(bind.getCommand());
            cmdBox.setResponder(val -> {
                String clean = val;
                while (clean.startsWith("/")) {
                    clean = clean.substring(1);
                }
                bind.setCommand(clean);
                PotsmodConfig.save();
            });
            activeInputBoxes.add(cmdBox);
            addRenderableWidget(cmdBox);
        }
    }

    private CustomBindEntry getBindById(String id) {
        if (id == null) return null;
        for (CustomBindEntry b : binds) {
            if (id.equals(b.getId())) return b;
        }
        return null;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
        GuiTheme theme = GuiStyle.configuredTheme();
        updateScroll();

        // Background panel
        Render2D.dropShadow(g, layout.panelBounds(), 8, theme.shadowColor(), BindsManagerLayout.PANEL_RADIUS);
        Render2D.shaderRoundedRect(g, layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH(), BindsManagerLayout.PANEL_RADIUS, theme.background());
        Render2D.shaderRoundedRect(g, layout.panelX(), layout.panelY(), layout.panelW(), BindsManagerLayout.HEADER_HEIGHT, BindsManagerLayout.PANEL_RADIUS, theme.secondary());
        g.nextStratum();

        drawTopBar(g, mouseX, mouseY, theme);
        drawBinds(g, mouseX, mouseY, theme);
        drawScrollbar(g, theme);

        super.render(g, mouseX, mouseY, delta);
    }

    private void drawTopBar(GuiGraphics g, int mouseX, int mouseY, GuiTheme theme) {
        // Title in header center
        float titleScale = 1.3f;
        Component title = GuiStyle.styled("Custom Binds (" + binds.size() + "/" + MAX_BINDS + ")");
        int titleW = Math.round(font.width(title) * titleScale);
        int titleX = layout.panelX() + (layout.panelW() - titleW) / 2;
        int titleY = Math.round(layout.panelY() + (BindsManagerLayout.HEADER_HEIGHT - font.lineHeight * titleScale) / 2.0f);
        drawBig(g, title, titleX, titleY, titleScale, theme.textColor());

        // Back Button
        UiRect backBtn = layout.backButton();
        boolean backHovered = inside(backBtn, mouseX, mouseY);
        int backFill = backHovered ? Render2D.withAlpha(theme.secondary(), 250) : Render2D.withAlpha(theme.secondary(), 200);
        int backBorder = backHovered ? 0xFFFFFFFF : 0x40FFFFFF;
        Render2D.shaderRoundedSurface(g, backBtn.x(), backBtn.y(), backBtn.width(), backBtn.height(), 6, backFill, backBorder);
        String backLabel = "← Back";
        int blw = font.width(GuiStyle.styled(backLabel));
        int bly = backBtn.y() + (backBtn.height() - font.lineHeight) / 2 + 1;
        g.drawString(font, GuiStyle.styled(backLabel), backBtn.x() + (backBtn.width() - blw) / 2, bly, backHovered ? 0xFFFFFFFF : theme.textColor(), false);

        // ADD BIND Button on left
        UiRect addBtn = layout.addBindButton();
        boolean canAdd = binds.size() < MAX_BINDS;
        boolean addHovered = canAdd && inside(addBtn, mouseX, mouseY);

        int btnFill = !canAdd ? Render2D.withAlpha(theme.secondary(), 120)
                : addHovered ? Render2D.withAlpha(COLOR_ACCENT, 60) : Render2D.withAlpha(theme.secondary(), 220);
        int btnBorder = !canAdd ? 0x22FFFFFF
                : addHovered ? COLOR_ACCENT_BRIGHT : Render2D.withAlpha(COLOR_ACCENT, 140);
        int textColor = !canAdd ? Render2D.withAlpha(theme.trinaryText(), 120)
                : addHovered ? 0xFFFFFFFF : COLOR_ACCENT_BRIGHT;

        Render2D.shaderRoundedSurface(g, addBtn.x(), addBtn.y(), addBtn.width(), addBtn.height(), 6, btnFill, btnBorder);
        String label = "+ Add Bind";
        int labelW = font.width(GuiStyle.styled(label));
        int labelY = addBtn.y() + (addBtn.height() - font.lineHeight) / 2 + 1;
        g.drawString(font, GuiStyle.styled(label), addBtn.x() + (addBtn.width() - labelW) / 2, labelY, textColor, false);
    }

    private void drawBinds(GuiGraphics g, int mouseX, int mouseY, GuiTheme theme) {
        int top = layout.listTop();
        int bottom = layout.listBottom();
        UiRect list = layout.listBounds();

        if (binds.isEmpty()) {
            String empty = "No custom binds added yet. Click '+ Add Bind' above!";
            int ew = font.width(GuiStyle.styled(empty));
            g.drawString(font, GuiStyle.styled(empty), layout.panelX() + (layout.panelW() - ew) / 2, top + 40, theme.trinaryText(), false);
            return;
        }

        g.enableScissor(list.x(), top, list.right(), bottom);

        for (int i = 0; i < binds.size(); i++) {
            CustomBindEntry bind = binds.get(i);
            UiRect row = layout.rowBounds(i, scroll);

            // Hide/position editboxes outside scissor
            boolean visible = row.bottom() >= top && row.y() <= bottom;

            drawBindRow(g, bind, row, i, mouseX, mouseY, theme, visible);
        }

        g.disableScissor();
    }

    private void drawBindRow(GuiGraphics g, CustomBindEntry bind, UiRect row, int index, int mouseX, int mouseY, GuiTheme theme, boolean visible) {
        boolean hovered = inside(row, mouseX, mouseY);
        int rowFill = hovered ? Render2D.withAlpha(theme.overlay(), 185) : theme.overlay();
        int rowBorder = Render2D.withAlpha(0xFFFFFF, hovered ? 36 : 18);
        Render2D.shaderRoundedSurface(g, row.x(), row.y(), row.width(), row.height(), 7, rowFill, rowBorder);

        int leftX = row.x() + 10;
        int firstRowY = row.y() + 6;
        int secondRowY = row.y() + 29;

        // Action Buttons on Right: [Edit] | [Remove] | [Set keybind / Key]
        int rightMargin = row.right() - 8;
        int btnH = 16;
        int btnActionY = firstRowY;

        // Keybind button
        boolean isCapturing = bind.getId().equals(capturingBindId);
        String keyDisplay = isCapturing ? "> Press Key <" : KeyUtil.getDisplayName(bind.getKey());
        int keyBtnW = Math.max(70, font.width(GuiStyle.styled(keyDisplay)) + 12);
        int keyBtnX = rightMargin - keyBtnW;
        UiRect keyRect = new UiRect(keyBtnX, btnActionY, keyBtnW, btnH);

        // Remove button
        int removeW = font.width(GuiStyle.styled("Remove")) + 10;
        int removeX = keyBtnX - removeW - 5;
        UiRect removeRect = new UiRect(removeX, btnActionY, removeW, btnH);

        // Edit button
        boolean isEditingName = bind.getId().equals(editingBindId);
        String editLabel = isEditingName ? "Done" : "Edit";
        int editW = font.width(GuiStyle.styled(editLabel)) + 10;
        int editX = removeX - editW - 5;
        UiRect editRect = new UiRect(editX, btnActionY, editW, btnH);

        // First Row Left: Bind Name or EditBox
        int maxTitleW = editX - leftX - 10;
        if (isEditingName && titleEditBox != null) {
            titleEditBox.visible = visible;
            titleEditBox.setX(leftX);
            titleEditBox.setY(firstRowY);
            titleEditBox.setWidth(Math.max(60, maxTitleW));
            Render2D.shaderRoundedSurface(g, leftX - 3, firstRowY - 2, maxTitleW + 6, btnH + 4, 4, 0xFF14151E, COLOR_ACCENT);
        } else {
            String nameText = fit(bind.getName(), maxTitleW);
            g.drawString(font, GuiStyle.styled(nameText), leftX, firstRowY + 3, theme.textColor(), false);
        }

        // Render Action Buttons
        // 1. Edit Button
        boolean editHovered = inside(editRect, mouseX, mouseY);
        int editBg = editHovered ? Render2D.withAlpha(COLOR_EDIT, 50) : Render2D.withAlpha(theme.secondary(), 200);
        int editBorder = Render2D.withAlpha(COLOR_EDIT, editHovered ? 180 : 80);
        int editTextColor = isEditingName ? COLOR_ACCENT_BRIGHT : (editHovered ? 0xFFFFFFFF : COLOR_EDIT);
        Render2D.shaderRoundedSurface(g, editRect.x(), editRect.y(), editRect.width(), editRect.height(), 4, editBg, editBorder);
        int ew = font.width(GuiStyle.styled(editLabel));
        g.drawString(font, GuiStyle.styled(editLabel), editRect.x() + (editRect.width() - ew) / 2, editRect.y() + 4, editTextColor, false);

        // 2. Remove Button
        boolean removeHovered = inside(removeRect, mouseX, mouseY);
        int removeBg = removeHovered ? Render2D.withAlpha(COLOR_REMOVE, 50) : Render2D.withAlpha(theme.secondary(), 200);
        int removeBorder = Render2D.withAlpha(COLOR_REMOVE, removeHovered ? 180 : 80);
        int removeTextColor = removeHovered ? 0xFFFFFFFF : COLOR_REMOVE;
        Render2D.shaderRoundedSurface(g, removeRect.x(), removeRect.y(), removeRect.width(), removeRect.height(), 4, removeBg, removeBorder);
        int rw = font.width(GuiStyle.styled("Remove"));
        g.drawString(font, GuiStyle.styled("Remove"), removeRect.x() + (removeRect.width() - rw) / 2, removeRect.y() + 4, removeTextColor, false);

        // 3. Keybind Button
        boolean keyHovered = inside(keyRect, mouseX, mouseY);
        int keyBg = isCapturing ? Render2D.withAlpha(COLOR_KEYBIND, 60) : (keyHovered ? Render2D.withAlpha(theme.secondary(), 240) : Render2D.withAlpha(theme.secondary(), 200));
        int keyBorder = isCapturing ? COLOR_KEYBIND : (keyHovered ? 0xFFFFFFFF : Render2D.withAlpha(0xFFFFFF, 40));
        int keyTextColor = isCapturing ? COLOR_KEYBIND : (keyHovered ? 0xFFFFFFFF : theme.textColor());
        Render2D.shaderRoundedSurface(g, keyRect.x(), keyRect.y(), keyRect.width(), keyRect.height(), 4, keyBg, keyBorder);
        int kw = font.width(GuiStyle.styled(keyDisplay));
        g.drawString(font, GuiStyle.styled(keyDisplay), keyRect.x() + (keyRect.width() - kw) / 2, keyRect.y() + 4, keyTextColor, false);

        // Second Row: Slash prefix "/" and Command input box
        int slashW = font.width(GuiStyle.styled("/"));
        g.drawString(font, GuiStyle.styled("/"), leftX, secondRowY + 4, COLOR_ACCENT_BRIGHT, false);

        int inputX = leftX + slashW + 5;
        int inputW = row.width() - (inputX - row.x()) - 10;

        if (index < activeInputBoxes.size()) {
            EditBox cmdBox = activeInputBoxes.get(index);
            boolean boxFocused = cmdBox.isFocused();
            int boxFill = boxFocused ? 0xFF14151E : 0xFF191C26;
            int boxBorder = boxFocused ? COLOR_ACCENT : 0x25FFFFFF;
            Render2D.shaderRoundedSurface(g, inputX - 4, secondRowY, inputW + 4, 18, 4, boxFill, boxBorder);

            cmdBox.visible = visible;
            cmdBox.active = visible;
            GuiStyle.layoutBorderlessEditBox(cmdBox, font, inputX, secondRowY + 2, inputW - 4, 14);
        }
    }

    private void drawScrollbar(GuiGraphics g, GuiTheme theme) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return;

        UiRect track = layout.scrollTrack();
        UiRect thumb = layout.scrollThumb(maxScroll, scroll);
        int thumbX = track.x() + (track.width() - BindsManagerLayout.SCROLLBAR_WIDTH) / 2;
        g.fill(thumbX, thumb.y(), thumbX + BindsManagerLayout.SCROLLBAR_WIDTH, thumb.bottom(), theme.scrollbarColor());
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.input();

        // Handle capturing keybind click
        if (capturingBindId != null) {
            CustomBindEntry capturing = getBindById(capturingBindId);
            if (capturing != null) {
                if (button == 1) { // Right click unbinds
                    capturing.setKey(KeyUtil.UNBOUND);
                    PotsmodConfig.save();
                    capturingBindId = null;
                    return true;
                } else if (button >= 2) { // Middle click / extra mouse button
                    InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(button);
                    capturing.setKey(mouseKey.getName());
                    PotsmodConfig.save();
                    capturingBindId = null;
                    return true;
                }
            }
            capturingBindId = null;
            return true;
        }

        // Back button
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(layout.backButton(), mouseX, mouseY)) {
            org.pots.potsmod.util.ModSounds.playClick();
            onClose();
            return true;
        }

        // Add bind button
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(layout.addBindButton(), mouseX, mouseY)) {
            if (binds.size() < MAX_BINDS) {
                CustomBindEntry newBind = CustomBindEntry.createDefault(binds.size() + 1);
                PotsmodConfig.getData().getCustomBinds().add(newBind);
                PotsmodConfig.save();
                reloadBinds();
                rebuildInputs();
                // Auto scroll to bottom
                scrollTarget = -maxScroll();
            }
            return true;
        }

        // Scrollbar drag
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && grabScrollbar(mouseX, mouseY)) {
            return true;
        }

        // List row clicks
        if (mouseY >= layout.listTop() && mouseY <= layout.listBottom()) {
            for (int i = 0; i < binds.size(); i++) {
                CustomBindEntry bind = binds.get(i);
                UiRect row = layout.rowBounds(i, scroll);
                if (!inside(row, mouseX, mouseY)) continue;

                int firstRowY = row.y() + 6;
                int btnH = 16;
                int rightMargin = row.right() - 8;

                // Keybind button
                String keyDisplay = bind.getId().equals(capturingBindId) ? "> Press Key <" : KeyUtil.getDisplayName(bind.getKey());
                int keyBtnW = Math.max(70, font.width(GuiStyle.styled(keyDisplay)) + 12);
                int keyBtnX = rightMargin - keyBtnW;
                UiRect keyRect = new UiRect(keyBtnX, firstRowY, keyBtnW, btnH);

                // Remove button
                int removeW = font.width(GuiStyle.styled("Remove")) + 10;
                int removeX = keyBtnX - removeW - 5;
                UiRect removeRect = new UiRect(removeX, firstRowY, removeW, btnH);

                // Edit button
                String editLabel = bind.getId().equals(editingBindId) ? "Done" : "Edit";
                int editW = font.width(GuiStyle.styled(editLabel)) + 10;
                int editX = removeX - editW - 5;
                UiRect editRect = new UiRect(editX, firstRowY, editW, btnH);

                if (inside(editRect, mouseX, mouseY)) {
                    if (bind.getId().equals(editingBindId)) {
                        editingBindId = null;
                    } else {
                        editingBindId = bind.getId();
                    }
                    rebuildInputs();
                    return true;
                }

                if (inside(removeRect, mouseX, mouseY)) {
                    PotsmodConfig.getData().getCustomBinds().removeIf(b -> b.getId().equals(bind.getId()));
                    PotsmodConfig.save();
                    if (bind.getId().equals(editingBindId)) {
                        editingBindId = null;
                    }
                    if (bind.getId().equals(capturingBindId)) {
                        capturingBindId = null;
                    }
                    reloadBinds();
                    rebuildInputs();
                    return true;
                }

                if (inside(keyRect, mouseX, mouseY)) {
                    capturingBindId = bind.getId();
                    return true;
                }
            }
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NotNull MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            dragScrollbarTo(event.y(), scrollbarDragOffset);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent event) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (layout.containsPanel(mouseX, mouseY)) {
            scrollTarget += verticalAmount * 28;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (capturingBindId != null) {
            CustomBindEntry capturing = getBindById(capturingBindId);
            if (capturing != null) {
                if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_BACKSPACE || event.key() == GLFW.GLFW_KEY_DELETE) {
                    capturing.setKey(KeyUtil.UNBOUND);
                } else {
                    InputConstants.Key key = InputConstants.getKey(event);
                    capturing.setKey(key.getName());
                }
                PotsmodConfig.save();
            }
            capturingBindId = null;
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (editingBindId != null) {
                editingBindId = null;
                rebuildInputs();
                return true;
            }
            onClose();
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_ENTER && editingBindId != null) {
            editingBindId = null;
            rebuildInputs();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent event) {
        if (capturingBindId != null) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        draggingScrollbar = false;
        capturingBindId = null;
        editingBindId = null;
        PotsmodConfig.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean grabScrollbar(double mouseX, double mouseY) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0 || !inside(layout.scrollTrack(), mouseX, mouseY)) {
            return false;
        }

        UiRect thumb = layout.scrollThumb(maxScroll, scroll);
        draggingScrollbar = true;
        scrollbarDragOffset = inside(thumb, mouseX, mouseY) ? mouseY - thumb.y() : thumb.height() / 2.0;
        dragScrollbarTo(mouseY, scrollbarDragOffset);
        return true;
    }

    private void dragScrollbarTo(double mouseY, double thumbOffset) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            scroll = scrollTarget = 0;
            draggingScrollbar = false;
            return;
        }

        UiRect track = layout.scrollTrack();
        int height = layout.thumbHeight(maxScroll);
        double travel = Math.max(1.0, track.height() - height);
        double thumbTop = mouseY - thumbOffset;
        double progress = (thumbTop - track.y()) / travel;
        progress = Math.clamp(progress, 0.0, 1.0);
        scroll = scrollTarget = -maxScroll * progress;
    }

    private void updateScroll() {
        scroll += (scrollTarget - scroll) * 0.35;
        if (Math.abs(scrollTarget - scroll) < 0.2) {
            scroll = scrollTarget;
        }
        clampScroll();
    }

    private void clampScroll() {
        int maxScroll = maxScroll();
        scrollTarget = Math.clamp(scrollTarget, -maxScroll, 0);
        scroll = Math.clamp(scroll, -maxScroll, 0);
    }

    private int maxScroll() {
        return layout.maxScroll(binds.size());
    }

    private boolean inside(UiRect rect, double mouseX, double mouseY) {
        return mouseX >= rect.x() && mouseX <= rect.right() && mouseY >= rect.y() && mouseY <= rect.bottom();
    }

    private String fit(String text, int maxWidth) {
        if (font.width(GuiStyle.styled(text)) <= maxWidth) {
            return text;
        }
        String trimmed = text;
        while (!trimmed.isEmpty() && font.width(GuiStyle.styled(trimmed + "...")) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    private void drawBig(GuiGraphics g, Component text, int x, int y, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }
}
