package org.pots.potsmod.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.wynntils.utils.render.RenderUtils;
import org.lwjgl.glfw.GLFW;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.config.choices.SettingCategory;
import org.pots.potsmod.config.setting.SettingSection;
import org.pots.potsmod.gui.GuiStyle;
import org.pots.potsmod.gui.animation.Animation;
import org.pots.potsmod.gui.animation.Easing;
import org.pots.potsmod.gui.component.DropdownControl;
import org.pots.potsmod.gui.component.KeybindChip;
import org.pots.potsmod.gui.component.SettingCard;
import org.pots.potsmod.gui.theme.ThemeTokens;
import org.pots.potsmod.render.Render2D;

import java.util.*;

public class ConfigScreen extends Screen {
    private static final Identifier POTATO_ICON = Identifier.fromNamespaceAndPath("potsmod", "textures/gui/pot.png");
    private static final int BASE_WIDTH = 660;
    private static final int BASE_HEIGHT = 420;
    private static final int SIDEBAR_WIDTH = 38;
    private static final int HEADER_HEIGHT = 36;
    private static final int AVATAR_SIZE = 22;

    private final Screen parent;
    private final Animation openAnim = new Animation(Easing.EASE_OUT_CUBIC, 220);
    private final Animation underlineAnim = new Animation(Easing.EASE_OUT_CUBIC, 180);

    // Categories / Pages
    private enum SidebarPage {
        GENERAL("General", SettingCategory.GENERAL),
        WAR("War", SettingCategory.WAR),
        SOCIAL("Social", SettingCategory.SOCIAL),
        OVERLAYS("HUD", null),
        IGNORE("Ignore", null);

        final String label;
        final SettingCategory category;

        SidebarPage(String label, SettingCategory category) {
            this.label = label;
            this.category = category;
        }
    }

    private SidebarPage activePage = SidebarPage.GENERAL;
    private final Map<SidebarPage, List<SettingCard>> pageCards = new EnumMap<>(SidebarPage.class);
    private final List<EditBox> textInputs = new ArrayList<>();

    // Sub-category tabs in header
    private final List<String> currentTabs = new ArrayList<>();
    private int selectedTab = 0;
    private float underlineX = 0;
    private float underlineTargetX = 0;
    private double tabScroll = 0;
    private double tabScrollTarget = 0;

    // Search filter
    private boolean searchOpen = false;
    private EditBox searchBox;
    private String searchQuery = "";

    // Scrolling & Layout
    private double scroll = 0;
    private double scrollTarget = 0;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private long lastFrameTime;

    public ConfigScreen(Screen parent) {
        super(Component.literal("potsmod"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        panelW = Math.min(BASE_WIDTH, width - 20);
        panelH = Math.min(BASE_HEIGHT, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        initSearchBox();
        buildAllPages();
        updateTabsForActivePage();
        openAnim.setValue(0.0);
        openAnim.run(1.0);
        lastFrameTime = System.currentTimeMillis();
    }

    private void initSearchBox() {
        searchBox = new EditBox(font, 0, 0, 110, 16, GuiStyle.styled("Search"));
        searchBox.setBordered(false);
        searchBox.setHeight(16);
        searchBox.setTextColor(ThemeTokens.TEXT_TITLE);
        GuiStyle.applyFont(searchBox, "Search...");
        searchBox.setResponder(q -> {
            searchQuery = q.trim().toLowerCase(Locale.ROOT);
            scroll = scrollTarget = 0;
        });
        searchBox.visible = false;
        addRenderableWidget(searchBox);
    }

    private void buildAllPages() {
        pageCards.clear();
        textInputs.clear();
        if (searchBox != null) textInputs.add(searchBox);

        for (SidebarPage page : SidebarPage.values()) {
            if (page.category == null) continue;

            List<SettingCard> cards = new ArrayList<>();
            for (SettingSection sec : PotsmodConfig.getSections(page.category)) {
                SettingCard card = SettingCard.ofSection(sec, sec.title().toUpperCase(Locale.ROOT));
                card.initEditBoxes(font, textInputs);
                for (EditBox box : textInputs) {
                    if (!children().contains(box)) {
                        addRenderableWidget(box);
                    }
                }
                cards.add(card);
            }
            pageCards.put(page, cards);
        }
    }

    private void updateTabsForActivePage() {
        currentTabs.clear();
        currentTabs.add("All");

        List<SettingCard> cards = pageCards.get(activePage);
        if (cards != null) {
            for (SettingCard card : cards) {
                if (!currentTabs.contains(card.getTitle())) {
                    currentTabs.add(card.getTitle());
                }
            }
        }
        selectedTab = 0;
        underlineX = underlineTargetX = 0;
        scroll = scrollTarget = 0;
        tabScroll = tabScrollTarget = 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        long frameDt = lastFrameTime > 0 ? Math.min(now - lastFrameTime, 100L) : 16L;
        lastFrameTime = now;
        updateScrollPosition(frameDt);

        float animProgress = (float) openAnim.getValue();

        // Screen backdrop tint (warm dark potato ambiance)
        int backdropAlpha = Math.clamp((int) (0x65 * animProgress), 0, 0x65);
        g.fill(0, 0, width, height, (backdropAlpha << 24) | 0x140E0A);

        g.pose().pushMatrix();
        if (animProgress < 1.0f) {
            float cx = panelX + panelW / 2.0f;
            float cy = panelY + panelH / 2.0f;
            float scale = 0.93f + 0.07f * animProgress;
            g.pose().translate(cx * (1.0f - scale), cy * (1.0f - scale));
            g.pose().scale(scale, scale);
        }

        renderWindow(g, mouseX, mouseY, delta);

        g.pose().popMatrix();
    }

    private void renderWindow(GuiGraphics g, int mx, int my, float delta) {
        int px = panelX;
        int py = panelY;

        // Main Window Frame (flat monochrome, no shadows/gradients)
        Render2D.shaderRoundedSurface(g, px, py, panelW, panelH, ThemeTokens.RADIUS_WINDOW,
                ThemeTokens.WINDOW_BG, ThemeTokens.WINDOW_BORDER);

        // Sidebar Frame
        Render2D.shaderRoundedSurface(g, px, py, SIDEBAR_WIDTH, panelH, ThemeTokens.RADIUS_WINDOW,
                ThemeTokens.SIDEBAR_BG, ThemeTokens.WINDOW_BORDER);

        // Divider line between sidebar and content
        Render2D.verticalLine(g, px + SIDEBAR_WIDTH, py + 1, panelH - 2, ThemeTokens.DIVIDER_LINE);

        // Content Area dimensions
        int contentX = px + SIDEBAR_WIDTH + 1;
        int contentW = panelW - SIDEBAR_WIDTH - 2;

        // Header divider line
        Render2D.horizontalLine(g, contentX, py + HEADER_HEIGHT, contentW, ThemeTokens.DIVIDER_LINE);

        // Render Sidebar
        renderSidebar(g, px, py, mx, my);

        // Render Header
        renderHeader(g, contentX, py, contentW, mx, my);

        // Render Two-Column Setting Cards
        renderContentColumns(g, contentX, py + HEADER_HEIGHT + 1, contentW, panelH - HEADER_HEIGHT - 2, mx, my);

        // Render any open dropdown popup on top z-order (outside scroll clip!)
        renderDropdownPopup(g, mx, my);
    }

    private void renderSidebar(GuiGraphics g, int px, int py, int mx, int my) {
        // Logo at top: Stylized ringed-planet / 'r' icon with golden potato accents
        int logoX = px + SIDEBAR_WIDTH / 2;
        int logoY = py + 14;

        // Clean geometric ringed planet logo with potato colors
        Render2D.circle(g, logoX, logoY, 9, 0xFFE5A84B);
        Render2D.circle(g, logoX, logoY, 7, ThemeTokens.SIDEBAR_BG);
        Render2D.circle(g, logoX, logoY, 4, 0xFFF5B545);
        g.fill(logoX - 7, logoY, logoX + 7, logoY + 1, 0xFFE5A84B);

        // Category icon buttons
        int btnSize = 24;
        int btnX = px + (SIDEBAR_WIDTH - btnSize) / 2;
        int startY = py + 34;

        SidebarPage[] pages = SidebarPage.values();
        for (int i = 0; i < pages.length; i++) {
            SidebarPage page = pages[i];
            int by = startY + i * 28;
            if (i >= 3) by += 6; // Small gap before HUD / Ignore

            boolean active = page == activePage;
            boolean hovered = mx >= btnX && mx <= btnX + btnSize && my >= by && my <= by + btnSize;

            if (active) {
                Render2D.shaderRoundedRect(g, btnX, by, btnSize, btnSize, 4, ThemeTokens.ICON_ACTIVE_BG);
            } else if (hovered) {
                Render2D.shaderRoundedRect(g, btnX, by, btnSize, btnSize, 4, 0xFF2A1D15);
            }

            int iconColor = active ? ThemeTokens.ICON_ACTIVE : (hovered ? ThemeTokens.ICON_HOVER : ThemeTokens.ICON_INACTIVE);
            renderPageIcon(g, page, btnX + btnSize / 2, by + btnSize / 2, iconColor);
        }

        // Bottom left: Potato icon with gentle animation & hover glow
        int avatarX = px + (SIDEBAR_WIDTH - AVATAR_SIZE) / 2;
        int avatarY = py + panelH - AVATAR_SIZE - 8;

        boolean potHovered = mx >= avatarX - 2 && mx <= avatarX + AVATAR_SIZE + 2
                && my >= avatarY - 2 && my <= avatarY + AVATAR_SIZE + 2;

        float bounce = potHovered ? -1.0f : (float) (Math.sin((System.currentTimeMillis() % 2400L) / 2400.0 * Math.PI * 2) * 1.0f);
        int drawY = (int) (avatarY + bounce);

        int potBg = potHovered ? 0xFF352418 : 0xFF221710;
        int potBorder = potHovered ? 0xFFE5A84B : 0x40D48B30;
        Render2D.shaderRoundedSurface(g, avatarX - 2, drawY - 2, AVATAR_SIZE + 4, AVATAR_SIZE + 4, 5, potBg, potBorder);

        try {
            RenderUtils.drawScalingTexturedRect(g, POTATO_ICON, avatarX, drawY, AVATAR_SIZE, AVATAR_SIZE, 1500, 1500);
        } catch (Exception e) {
            Render2D.shaderRoundedRect(g, avatarX, drawY, AVATAR_SIZE, AVATAR_SIZE, 4, 0xFF8B592D);
        }
    }

    private void renderPageIcon(GuiGraphics g, SidebarPage page, int cx, int cy, int color) {
        switch (page) {
            case GENERAL -> { // Minimalist sliders icon
                g.fill(cx - 5, cy - 3, cx + 5, cy - 2, color);
                g.fill(cx - 3, cy - 5, cx - 1, cy - 1, color);

                g.fill(cx - 5, cy, cx + 5, cy + 1, color);
                g.fill(cx + 1, cy - 2, cx + 3, cy + 2, color);

                g.fill(cx - 5, cy + 3, cx + 5, cy + 4, color);
                g.fill(cx - 2, cy + 1, cx, cy + 5, color);
            }
            case WAR -> { // Minimalist shield
                g.fill(cx - 4, cy - 4, cx + 5, cy - 3, color);
                g.fill(cx - 4, cy - 3, cx - 3, cy + 1, color);
                g.fill(cx + 4, cy - 3, cx + 5, cy + 1, color);
                g.fill(cx - 3, cy + 1, cx - 2, cy + 3, color);
                g.fill(cx + 3, cy + 1, cx + 4, cy + 3, color);
                g.fill(cx - 2, cy + 3, cx - 1, cy + 4, color);
                g.fill(cx + 2, cy + 3, cx + 3, cy + 4, color);
                g.fill(cx - 1, cy + 4, cx + 2, cy + 5, color);
            }
            case SOCIAL -> { // Clean message bubble
                g.fill(cx - 4, cy - 4, cx + 5, cy + 2, color);
                g.fill(cx - 3, cy - 3, cx + 4, cy + 1, ThemeTokens.SIDEBAR_BG);
                g.fill(cx - 4, cy + 2, cx - 2, cy + 5, color);
                g.fill(cx - 2, cy + 2, cx - 1, cy + 4, color);
            }
            case OVERLAYS -> { // 2x2 grid
                g.fill(cx - 4, cy - 4, cx - 1, cy - 1, color);
                g.fill(cx + 1, cy - 4, cx + 4, cy - 1, color);
                g.fill(cx - 4, cy + 1, cx - 1, cy + 4, color);
                g.fill(cx + 1, cy + 1, cx + 4, cy + 4, color);
            }
            case IGNORE -> { // Circle with slash
                g.fill(cx - 4, cy - 1, cx + 5, cy + 1, color);
                g.fill(cx - 3, cy - 4, cx + 4, cy - 3, color);
                g.fill(cx - 3, cy + 3, cx + 4, cy + 4, color);
                g.fill(cx - 4, cy - 3, cx - 3, cy + 3, color);
                g.fill(cx + 4, cy - 3, cx + 5, cy + 3, color);
            }
        }
    }

    private void renderHeader(GuiGraphics g, int x, int y, int width, int mx, int my) {
        // Header Right Action Buttons (Search magnifier, Folder import, Play run)
        int btnSize = 20;
        int rightMargin = x + width - 12;

        int playX = rightMargin - btnSize;
        int folderX = playX - btnSize - 6;
        int searchBtnX = folderX - btnSize - 6;
        int btnY = y + (HEADER_HEIGHT - btnSize) / 2;

        int actionsStartX = searchOpen ? (searchBtnX - 120 + btnSize) : searchBtnX;
        int tabStartX = x + 12;
        int tabAreaW = Math.max(40, actionsStartX - 10 - tabStartX);

        // Calculate tabs scroll & width
        int totalTabsW = 0;
        for (String tabName : currentTabs) {
            totalTabsW += font.width(GuiStyle.styled(tabName)) + 14;
        }
        int maxTabScroll = Math.max(0, totalTabsW - tabAreaW);
        tabScrollTarget = Math.clamp(tabScrollTarget, -maxTabScroll, 0);
        tabScroll += (tabScrollTarget - tabScroll) * 0.35;
        if (Math.abs(tabScrollTarget - tabScroll) < 0.2) tabScroll = tabScrollTarget;

        // Scissored tabs rendering to prevent overflowing header & screen
        g.enableScissor(tabStartX, y, tabStartX + tabAreaW, y + HEADER_HEIGHT);
        int tabX = tabStartX + (int) Math.round(tabScroll);
        int tabY = y + (HEADER_HEIGHT - font.lineHeight) / 2;

        for (int i = 0; i < currentTabs.size(); i++) {
            String tabName = currentTabs.get(i);
            int tw = font.width(GuiStyle.styled(tabName));
            boolean active = i == selectedTab;
            boolean hovered = mx >= tabX && mx <= tabX + tw && my >= y && my <= y + HEADER_HEIGHT
                    && mx >= tabStartX && mx <= tabStartX + tabAreaW;

            int color = active ? ThemeTokens.TEXT_ACTIVE : (hovered ? 0xFFE5A84B : ThemeTokens.TEXT_INACTIVE);
            g.drawString(font, GuiStyle.styled(tabName), tabX, tabY, color, false);

            if (active) {
                underlineTargetX = tabX;
            }

            tabX += tw + 14;
        }

        // Animated sliding underline (18px wide)
        underlineX += (underlineTargetX - underlineX) * 0.25f;
        int ulX = Math.round(underlineX);
        int ulY = y + HEADER_HEIGHT - 3;
        g.fill(ulX, ulY, ulX + 18, ulY + 2, ThemeTokens.TEXT_ACTIVE);

        g.disableScissor();

        // Play button (run action)
        boolean hoverPlay = mx >= playX && mx <= playX + btnSize && my >= btnY && my <= btnY + btnSize;
        Render2D.shaderRoundedSurface(g, playX, btnY, btnSize, btnSize, 4, hoverPlay ? 0xFF2F2116 : ThemeTokens.INPUT_BG, ThemeTokens.INPUT_BORDER);
        renderPlayIcon(g, playX, btnY, hoverPlay ? 0xFFFFF5E4 : ThemeTokens.TEXT_INACTIVE);

        // Folder import button
        boolean hoverFolder = mx >= folderX && mx <= folderX + btnSize && my >= btnY && my <= btnY + btnSize;
        Render2D.shaderRoundedSurface(g, folderX, btnY, btnSize, btnSize, 4, hoverFolder ? 0xFF2F2116 : ThemeTokens.INPUT_BG, ThemeTokens.INPUT_BORDER);
        renderFolderIcon(g, folderX, btnY, hoverFolder ? 0xFFFFF5E4 : ThemeTokens.TEXT_INACTIVE);

        // Magnifier / Search button
        if (searchOpen) {
            int inputW = 120;
            int inputX = searchBtnX - inputW + btnSize;
            Render2D.shaderRoundedSurface(g, inputX, btnY, inputW, btnSize, 4, ThemeTokens.INPUT_BG, ThemeTokens.INPUT_BORDER_FOCUSED);
            searchBox.visible = true;
            searchBox.active = true;
            searchBox.setEditable(true);
            GuiStyle.layoutBorderlessEditBox(searchBox, font, inputX + 6, btnY, inputW - 12, btnSize);
            searchBox.render(g, mx, my, 0);
        } else {
            searchBox.visible = false;
            searchBox.active = false;
            boolean hoverSearch = mx >= searchBtnX && mx <= searchBtnX + btnSize && my >= btnY && my <= btnY + btnSize;
            Render2D.shaderRoundedSurface(g, searchBtnX, btnY, btnSize, btnSize, 4, hoverSearch ? 0xFF2F2116 : ThemeTokens.INPUT_BG, ThemeTokens.INPUT_BORDER);
            renderSearchIcon(g, searchBtnX, btnY, hoverSearch ? 0xFFFFF5E4 : ThemeTokens.TEXT_INACTIVE);
        }
    }

    private void renderPlayIcon(GuiGraphics g, int bx, int by, int color) {
        int cx = bx + 8;
        int cy = by + 6;
        g.fill(cx, cy, cx + 1, cy + 9, color);
        g.fill(cx + 1, cy + 1, cx + 2, cy + 8, color);
        g.fill(cx + 2, cy + 2, cx + 3, cy + 7, color);
        g.fill(cx + 3, cy + 3, cx + 4, cy + 6, color);
        g.fill(cx + 4, cy + 4, cx + 5, cy + 5, color);
    }

    private void renderFolderIcon(GuiGraphics g, int bx, int by, int color) {
        int cx = bx + 10;
        int cy = by + 10;
        g.fill(cx - 5, cy - 4, cx - 2, cy - 3, color);
        g.fill(cx - 5, cy - 3, cx + 5, cy + 4, color);
        g.fill(cx - 4, cy - 2, cx + 4, cy + 3, ThemeTokens.INPUT_BG);
        g.fill(cx, cy - 1, cx + 1, cy + 2, color);
        g.fill(cx - 1, cy + 1, cx + 2, cy + 2, color);
    }

    private void renderSearchIcon(GuiGraphics g, int bx, int by, int color) {
        int cx = bx + 9;
        int cy = by + 9;
        g.fill(cx - 3, cy - 4, cx + 2, cy - 3, color);
        g.fill(cx - 3, cy + 1, cx + 2, cy + 2, color);
        g.fill(cx - 4, cy - 3, cx - 3, cy + 1, color);
        g.fill(cx + 2, cy - 3, cx + 3, cy + 1, color);
        g.fill(cx + 2, cy + 2, cx + 4, cy + 4, color);
        g.fill(cx + 3, cy + 3, cx + 5, cy + 5, color);
    }

    private void renderContentColumns(GuiGraphics g, int x, int y, int width, int height, int mx, int my) {
        List<SettingCard> cards = pageCards.get(activePage);
        if (cards == null || cards.isEmpty()) return;

        // Filter cards by tab & search query
        List<SettingCard> filtered = new ArrayList<>();
        String tabFilter = selectedTab > 0 && selectedTab < currentTabs.size() ? currentTabs.get(selectedTab) : null;

        for (SettingCard card : cards) {
            if (tabFilter != null && !card.getTitle().equals(tabFilter)) {
                continue;
            }
            if (!searchQuery.isEmpty()) {
                String t = card.getTitle().toLowerCase(Locale.ROOT);
                String d = card.getDescription() != null ? card.getDescription().toLowerCase(Locale.ROOT) : "";
                if (!t.contains(searchQuery) && !d.contains(searchQuery)) {
                    continue;
                }
            }
            filtered.add(card);
        }

        // Two columns calculation
        int gutter = ThemeTokens.COLUMN_GUTTER;
        int colW = (width - 24 - gutter) / 2;
        int leftColX = x + 12;
        int rightColX = leftColX + colW + gutter;

        // Balance cards into two columns by height
        List<SettingCard> leftCards = new ArrayList<>();
        List<SettingCard> rightCards = new ArrayList<>();
        int leftH = 0;
        int rightH = 0;

        for (SettingCard card : filtered) {
            int ch = card.calculateHeight(font, colW);
            if (leftH <= rightH) {
                leftCards.add(card);
                leftH += ch + ThemeTokens.CARD_GAP;
            } else {
                rightCards.add(card);
                rightH += ch + ThemeTokens.CARD_GAP;
            }
        }

        int maxContentH = Math.max(leftH, rightH);
        int maxScroll = Math.max(0, maxContentH - height + 16);
        clampScroll(maxScroll);

        // Scissor clip for content area
        g.enableScissor(x + 2, y, x + width - 2, y + height);

        // Render Left Column
        int curLeftY = y + 8 + (int) Math.round(scroll);
        for (SettingCard card : leftCards) {
            card.setPosition(leftColX, curLeftY, colW);
            card.render(g, font, mx, my);
            curLeftY += card.getHeight() + ThemeTokens.CARD_GAP;
        }

        // Render Right Column
        int curRightY = y + 8 + (int) Math.round(scroll);
        for (SettingCard card : rightCards) {
            card.setPosition(rightColX, curRightY, colW);
            card.render(g, font, mx, my);
            curRightY += card.getHeight() + ThemeTokens.CARD_GAP;
        }

        g.disableScissor();

        // Thin auto-hiding scrollbar
        if (maxScroll > 0) {
            int barX = x + width - 6;
            int barH = Math.max(16, Math.round(height * (float) height / (height + maxScroll)));
            float progress = (float) (-scroll / maxScroll);
            int thumbY = y + Math.round((height - barH) * progress);
            Render2D.shaderRoundedRect(g, barX, thumbY, 3, barH, 1, 0x50FFFFFF);
        }
    }

    private void renderDropdownPopup(GuiGraphics g, int mx, int my) {
        List<SettingCard> cards = pageCards.get(activePage);
        if (cards == null) return;
        for (SettingCard card : cards) {
            DropdownControl dd = card.getOpenDropdown();
            if (dd != null) {
                dd.renderPopup(g, font, mx, my);
                return;
            }
        }
    }

    private void clampScroll(int maxScroll) {
        scrollTarget = Math.clamp(scrollTarget, -maxScroll, 0);
    }

    private void updateScrollPosition(long dt) {
        double lerp = 1.0 - Math.pow(0.005, dt / 1000.0);
        scroll += (scrollTarget - scroll) * Math.min(lerp, 0.6);
        if (Math.abs(scrollTarget - scroll) < 0.2) {
            scroll = scrollTarget;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= panelY && mouseY <= panelY + HEADER_HEIGHT) {
            tabScrollTarget += verticalAmount * 28;
            return true;
        }
        scrollTarget += verticalAmount * 28;
        return true;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        // Dropdown popup top layer check
        List<SettingCard> cards = pageCards.get(activePage);
        if (cards != null) {
            for (SettingCard card : cards) {
                DropdownControl dd = card.getOpenDropdown();
                if (dd != null && dd.isOverPopup(mx, my)) {
                    return dd.mouseClicked(mx, my, button);
                }
            }
        }

        // Sidebar clicks
        if (mx >= panelX && mx <= panelX + SIDEBAR_WIDTH) {
            int btnSize = 24;
            int btnX = panelX + (SIDEBAR_WIDTH - btnSize) / 2;
            int startY = panelY + 34;
            SidebarPage[] pages = SidebarPage.values();

            for (int i = 0; i < pages.length; i++) {
                int by = startY + i * 28;
                if (i >= 3) by += 6;
                if (mx >= btnX && mx <= btnX + btnSize && my >= by && my <= by + btnSize) {
                    SidebarPage target = pages[i];
                    if (target == SidebarPage.OVERLAYS) {
                        org.pots.potsmod.managers.OverlayManager.openConfig();
                        return true;
                    }
                    if (target == SidebarPage.IGNORE) {
                        minecraft.setScreen(new IgnoreManagerScreen(this, org.pots.potsmod.managers.FeatureManager.getIgnoreFeature()));
                        return true;
                    }
                    activePage = target;
                    updateTabsForActivePage();
                    return true;
                }
            }
        }

        // Header clicks
        int contentX = panelX + SIDEBAR_WIDTH + 1;
        int contentW = panelW - SIDEBAR_WIDTH - 2;

        if (my >= panelY && my <= panelY + HEADER_HEIGHT) {
            int tabStartX = contentX + 12;
            int rightMargin = contentX + contentW - 12;
            int btnSize = 20;
            int playX = rightMargin - btnSize;
            int folderX = playX - btnSize - 6;
            int searchBtnX = folderX - btnSize - 6;
            int actionsStartX = searchOpen ? (searchBtnX - 120 + btnSize) : searchBtnX;
            int tabAreaW = Math.max(40, actionsStartX - 10 - tabStartX);

            // Tabs inside scissored tabAreaW
            if (mx >= tabStartX && mx <= tabStartX + tabAreaW) {
                int tabX = tabStartX + (int) Math.round(tabScroll);
                for (int i = 0; i < currentTabs.size(); i++) {
                    int tw = font.width(GuiStyle.styled(currentTabs.get(i)));
                    if (mx >= tabX && mx <= tabX + tw) {
                        selectedTab = i;
                        scroll = scrollTarget = 0;
                        return true;
                    }
                    tabX += tw + 14;
                }
            }

            // Right action buttons
            if (mx >= playX && mx <= playX + btnSize) {
                // Play / Run action trigger
                return true;
            }

            if (mx >= folderX && mx <= folderX + btnSize) {
                // Folder / Import action
                return true;
            }

            if (mx >= searchBtnX && mx <= searchBtnX + btnSize) {
                searchOpen = !searchOpen;
                if (searchOpen) {
                    searchBox.setFocused(true);
                    this.setFocused(searchBox);
                } else {
                    searchBox.setValue("");
                    searchQuery = "";
                }
                return true;
            }
        }

        // Setting cards click
        int contentY = panelY + HEADER_HEIGHT;
        if (cards != null && my >= contentY && my <= panelY + panelH && mx >= contentX && mx <= contentX + contentW) {
            String tabFilter = selectedTab > 0 && selectedTab < currentTabs.size() ? currentTabs.get(selectedTab) : null;
            for (SettingCard card : cards) {
                if (tabFilter != null && !card.getTitle().equals(tabFilter)) {
                    continue;
                }
                if (!searchQuery.isEmpty()) {
                    String t = card.getTitle().toLowerCase(Locale.ROOT);
                    String d = card.getDescription() != null ? card.getDescription().toLowerCase(Locale.ROOT) : "";
                    if (!t.contains(searchQuery) && !d.contains(searchQuery)) {
                        continue;
                    }
                }
                if (card.mouseClicked(mx, my, button)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (mouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        List<SettingCard> cards = pageCards.get(activePage);
        if (cards != null) {
            for (SettingCard card : cards) {
                if (card.mouseReleased(mouseX, mouseY, button)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (mouseReleased(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseReleased(event);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        List<SettingCard> cards = pageCards.get(activePage);
        if (cards != null) {
            for (SettingCard card : cards) {
                card.mouseDragged(mouseX, mouseY, button);
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        if (mouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (KeybindChip.getActiveCapturing() != null) {
            return KeybindChip.getActiveCapturing().keyPressed(event);
        }

        // ESC behavior: closes dropdown/search first, then screen
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            List<SettingCard> cards = pageCards.get(activePage);
            if (cards != null) {
                for (SettingCard card : cards) {
                    DropdownControl dd = card.getOpenDropdown();
                    if (dd != null) {
                        dd.setOpen(false);
                        return true;
                    }
                }
            }

            if (searchOpen) {
                searchOpen = false;
                searchBox.setValue("");
                searchQuery = "";
                return true;
            }

            onClose();
            return true;
        }

        List<SettingCard> cards = pageCards.get(activePage);
        if (cards != null) {
            for (SettingCard card : cards) {
                if (card.keyPressed(event)) return true;
            }
        }

        return super.keyPressed(event);
    }

    public boolean shouldPreparePortalSnapshot() {
        return false;
    }

    public void preparePortalSnapshotOffscreen() {
    }

    public void renderPortalTransition(GuiGraphics g) {
    }

    @Override
    public void onClose() {
        KeybindChip.clearActiveCapturing();
        PotsmodConfig.save();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
