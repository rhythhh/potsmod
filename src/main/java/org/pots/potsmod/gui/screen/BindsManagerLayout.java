package org.pots.potsmod.gui.screen;

import org.pots.potsmod.gui.render.UiRect;

record BindsManagerLayout(int panelX, int panelY, int panelW, int panelH) {
    public static final int PANEL_RADIUS = 12;
    public static final int HEADER_HEIGHT = 54;
    public static final int ROW_HEIGHT = 56;
    public static final int ROW_GAP = 8;
    public static final int SCROLLBAR_WIDTH = 2;
    public static final int SCROLLBAR_HIT_WIDTH = 10;

    private static final int PANEL_MAX_WIDTH = 620;
    private static final int PANEL_MAX_HEIGHT = 440;
    private static final int PANEL_MARGIN = 20;

    public static BindsManagerLayout fromScreen(int screenWidth, int screenHeight) {
        int availableWidth = Math.max(1, screenWidth - PANEL_MARGIN * 2);
        int availableHeight = Math.max(1, screenHeight - PANEL_MARGIN * 2);
        int panelWidth = Math.min(PANEL_MAX_WIDTH, availableWidth);
        int panelHeight = Math.min(PANEL_MAX_HEIGHT, availableHeight);
        return new BindsManagerLayout((screenWidth - panelWidth) / 2, (screenHeight - panelHeight) / 2, panelWidth, panelHeight);
    }

    public UiRect panelBounds() {
        return new UiRect(panelX, panelY, panelW, panelH);
    }

    public UiRect headerBounds() {
        return new UiRect(panelX, panelY, panelW, HEADER_HEIGHT);
    }

    public UiRect listBounds() {
        return new UiRect(panelX + 14, listTop(), panelW - 28, listHeight());
    }

    public UiRect addBindButton() {
        int btnW = 90;
        int btnH = 22;
        return new UiRect(panelX + 16, panelY + (HEADER_HEIGHT - btnH) / 2, btnW, btnH);
    }

    public UiRect rowBounds(int index, double scroll) {
        UiRect list = listBounds();
        int y = (int) Math.round(list.y() + scroll + index * (ROW_HEIGHT + ROW_GAP));
        return new UiRect(list.x(), y, list.width(), ROW_HEIGHT);
    }

    public UiRect scrollTrack() {
        return new UiRect(panelX + panelW - 10, listTop(), SCROLLBAR_HIT_WIDTH, listHeight());
    }

    public UiRect scrollThumb(int maxScroll, double scroll) {
        UiRect track = scrollTrack();
        int height = thumbHeight(maxScroll);
        int y = track.y() + Math.round((track.height() - height) * (float) (-scroll / maxScroll));
        return new UiRect(track.x(), y, track.width(), height);
    }

    public int thumbHeight(int maxScroll) {
        int listHeight = listHeight();
        return Math.max(22, Math.round(listHeight * (float) listHeight / (listHeight + maxScroll)));
    }

    public int maxScroll(int count) {
        int contentHeight = count == 0 ? 0 : count * ROW_HEIGHT + (count - 1) * ROW_GAP;
        return Math.max(0, contentHeight - listHeight());
    }

    public boolean containsPanel(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH;
    }

    public int listTop() {
        return panelY + HEADER_HEIGHT + 10;
    }

    public int listBottom() {
        return panelY + panelH - 12;
    }

    public int listHeight() {
        return panelH - HEADER_HEIGHT - 22;
    }
}
