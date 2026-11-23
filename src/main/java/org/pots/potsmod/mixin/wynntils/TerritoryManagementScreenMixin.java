package org.pots.potsmod.mixin.wynntils;

import com.wynntils.core.components.Models;
import com.wynntils.models.items.items.gui.TerritoryItem;
import com.wynntils.models.territories.profile.TerritoryProfile;
import com.wynntils.screens.territorymanagement.TerritoryManagementHolder;
import com.wynntils.screens.territorymanagement.TerritoryManagementScreen;
import com.wynntils.utils.colors.CustomColor;
import com.wynntils.utils.render.MapRenderer;
import com.wynntils.utils.render.RenderUtils;
import com.wynntils.utils.type.Pair;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.pots.potsmod.managers.TerritoryBaseManager;
import org.pots.potsmod.models.records.TerritoryBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(value = TerritoryManagementScreen.class, remap = false)
public abstract class TerritoryManagementScreenMixin {

    @Shadow
    private boolean mapMode;

    @Shadow
    private TerritoryManagementHolder holder;

    @Unique
    private float rmodTargetZoom = -1.0f;
    @Unique
    private float rmodTargetCenterX = Float.NaN;
    @Unique
    private float rmodTargetCenterZ = Float.NaN;

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = true)
    private void onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (!org.pots.potsmod.config.PotsmodConfig.isCustomEconomyMenuEnabled()) return;
        if (!this.mapMode || verticalAmount == 0) return;

        AbstractMapScreenAccessor accessor = (AbstractMapScreenAccessor) this;
        float currentZoom = accessor.getZoomLevel();
        float currentCenterX = accessor.getMapCenterX();
        float currentCenterZ = accessor.getMapCenterZ();
        float screenCenterX = accessor.getCenterX();
        float screenCenterZ = accessor.getCenterZ();

        if (rmodTargetZoom < 0) {
            rmodTargetZoom = currentZoom;
            rmodTargetCenterX = currentCenterX;
            rmodTargetCenterZ = currentCenterZ;
        }

        float curScale = MapRenderer.getZoomRenderScaleFromLevel(rmodTargetZoom);
        float worldX = rmodTargetCenterX + (float) (mouseX - screenCenterX) / curScale;
        float worldZ = rmodTargetCenterZ + (float) (mouseY - screenCenterZ) / curScale;

        float delta = (float) verticalAmount * 2.5f;
        rmodTargetZoom = Math.max(1.0f, Math.min(100.0f, rmodTargetZoom + delta));
        float futureScale = MapRenderer.getZoomRenderScaleFromLevel(rmodTargetZoom);

        rmodTargetCenterX = worldX - (float) (mouseX - screenCenterX) / futureScale;
        rmodTargetCenterZ = worldZ - (float) (mouseY - screenCenterZ) / futureScale;

        cir.setReturnValue(true);
    }

    @Inject(method = "doRender", at = @At("HEAD"), remap = true)
    private void onDoRender(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!org.pots.potsmod.config.PotsmodConfig.isCustomEconomyMenuEnabled()) return;
        if (!this.mapMode) return;

        AbstractMapScreenAccessor accessor = (AbstractMapScreenAccessor) this;
        if (rmodTargetZoom >= 0) {
            float curZoom = accessor.getZoomLevel();
            float curCx = accessor.getMapCenterX();
            float curCz = accessor.getMapCenterZ();

            float zoomDiff = rmodTargetZoom - curZoom;
            float cxDiff = rmodTargetCenterX - curCx;
            float czDiff = rmodTargetCenterZ - curCz;

            if (Math.abs(zoomDiff) > 0.05f || Math.abs(cxDiff) > 0.5f || Math.abs(czDiff) > 0.5f) {
                float factor = 0.35f;
                accessor.callSetZoomLevel(curZoom + zoomDiff * factor);
                accessor.setMapCenterX(curCx + cxDiff * factor);
                accessor.setMapCenterZ(curCz + czDiff * factor);
            } else {
                accessor.callSetZoomLevel(rmodTargetZoom);
                accessor.setMapCenterX(rmodTargetCenterX);
                accessor.setMapCenterZ(rmodTargetCenterZ);
                rmodTargetZoom = -1.0f;
            }
        }
    }

    @Inject(
            method = "renderMapScreen",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/wynntils/screens/territorymanagement/TerritoryManagementScreen;renderPois(Lnet/minecraft/client/gui/GuiGraphics;II)V"
            ),
            remap = true
    )
    private void onRenderMapScreenPois(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!org.pots.potsmod.config.PotsmodConfig.isCustomEconomyMenuEnabled()) return;
        AbstractMapScreenAccessor accessor = (AbstractMapScreenAccessor) this;
        float mapCenterX = accessor.getMapCenterX();
        float mapCenterZ = accessor.getMapCenterZ();
        float centerX = accessor.getCenterX();
        float centerZ = accessor.getCenterZ();
        float zoomRenderScale = accessor.getZoomRenderScale();

        renderOwnedConnections(g, mapCenterX, mapCenterZ, centerX, centerZ, zoomRenderScale);
        renderOwnedHqTriangles(g, mapCenterX, mapCenterZ, centerX, centerZ, zoomRenderScale);
    }

    @Unique
    private void renderOwnedConnections(GuiGraphics g, float mapCenterX, float mapCenterZ, float centerX, float centerZ, float zoomRenderScale) {
        if (zoomRenderScale < 0.08f || holder == null || holder.territoryItems() == null) return;

        Set<String> owned = new HashSet<>();
        String hqName = null;
        for (Pair<ItemStack, TerritoryItem> pair : holder.territoryItems()) {
            if (pair != null && pair.b() != null) {
                String name = pair.b().getName();
                owned.add(name);
                if (pair.b().isHeadquarters()) {
                    hqName = name;
                }
            }
        }
        if (owned.isEmpty()) return;

        Map<String, TerritoryBase> catalog = TerritoryBaseManager.getTerritories();
        if (catalog == null) return;

        float fade = Math.min(1.0f, (zoomRenderScale - 0.08f) / 0.12f);
        float lineWidth = Math.max(1.1f, Math.min(2.2f, 1.5f * zoomRenderScale * 2.2f));
        Set<String> drawn = new HashSet<>();

        for (String terrName : owned) {
            TerritoryBase base = catalog.get(terrName);
            if (base == null || base.connections() == null) continue;

            TerritoryProfile p1 = Models.Territory.getTerritoryProfile(terrName);
            if (p1 == null) continue;

            float x1 = MapRenderer.getRenderX((p1.getStartX() + p1.getEndX()) / 2, mapCenterX, centerX, zoomRenderScale);
            float z1 = MapRenderer.getRenderZ((p1.getStartZ() + p1.getEndZ()) / 2, mapCenterZ, centerZ, zoomRenderScale);

            boolean isHq1 = terrName.equalsIgnoreCase(hqName);

            for (String connName : base.connections()) {
                if (!owned.contains(connName)) continue;

                String pairKey = terrName.compareTo(connName) < 0 ? terrName + "<->" + connName : connName + "<->" + terrName;
                if (!drawn.add(pairKey)) continue;

                TerritoryProfile p2 = Models.Territory.getTerritoryProfile(connName);
                if (p2 == null) continue;

                float x2 = MapRenderer.getRenderX((p2.getStartX() + p2.getEndX()) / 2, mapCenterX, centerX, zoomRenderScale);
                float z2 = MapRenderer.getRenderZ((p2.getStartZ() + p2.getEndZ()) / 2, mapCenterZ, centerZ, zoomRenderScale);

                boolean isHq2 = connName.equalsIgnoreCase(hqName);
                boolean connectedToHq = isHq1 || isHq2;

                int baseColor = connectedToHq ? 0x001E90FF : 0x00FFFACD;
                int lineAlpha = (int) (0xDD * fade);
                int lineColor = (lineAlpha << 24) | baseColor;

                RenderUtils.drawLine(g, CustomColor.fromInt(lineColor), x1, z1, x2, z2, lineWidth);
            }
        }
    }

    @Unique
    private void renderOwnedHqTriangles(GuiGraphics g, float mapCenterX, float mapCenterZ, float centerX, float centerZ, float zoomRenderScale) {
        if (zoomRenderScale < 0.25f || holder == null || holder.territoryItems() == null) return;

        String hqName = null;
        Set<String> owned = new HashSet<>();
        for (Pair<ItemStack, TerritoryItem> pair : holder.territoryItems()) {
            if (pair != null && pair.b() != null) {
                String name = pair.b().getName();
                owned.add(name);
                if (pair.b().isHeadquarters()) {
                    hqName = name;
                }
            }
        }
        if (hqName == null) return;

        TerritoryBase base = TerritoryBaseManager.getTerritory(hqName);
        if (base == null || base.connections() == null) return;

        TerritoryProfile hqProf = Models.Territory.getTerritoryProfile(hqName);
        if (hqProf == null) return;

        float alphaFactor = Math.min(1.0f, (zoomRenderScale - 0.25f) / 0.25f);
        int alpha = (int) (0xFF * alphaFactor);
        int triColor = (alpha << 24) | 0x00FFD700;

        float hqMinX = MapRenderer.getRenderX(Math.min(hqProf.getStartX(), hqProf.getEndX()), mapCenterX, centerX, zoomRenderScale);
        float hqMaxX = MapRenderer.getRenderX(Math.max(hqProf.getStartX(), hqProf.getEndX()), mapCenterX, centerX, zoomRenderScale);
        float hqMinZ = MapRenderer.getRenderZ(Math.min(hqProf.getStartZ(), hqProf.getEndZ()), mapCenterZ, centerZ, zoomRenderScale);
        float hqMaxZ = MapRenderer.getRenderZ(Math.max(hqProf.getStartZ(), hqProf.getEndZ()), mapCenterZ, centerZ, zoomRenderScale);

        float cx1 = (hqMinX + hqMaxX) / 2.0f;
        float cz1 = (hqMinZ + hqMaxZ) / 2.0f;

        for (String connName : base.connections()) {
            if (!owned.contains(connName)) continue;

            TerritoryProfile p2 = Models.Territory.getTerritoryProfile(connName);
            if (p2 == null) continue;

            float cx2 = MapRenderer.getRenderX((p2.getStartX() + p2.getEndX()) / 2, mapCenterX, centerX, zoomRenderScale);
            float cz2 = MapRenderer.getRenderZ((p2.getStartZ() + p2.getEndZ()) / 2, mapCenterZ, centerZ, zoomRenderScale);

            float dx = cx2 - cx1;
            float dz = cz2 - cz1;
            float len = (float) Math.hypot(dx, dz);
            if (len < 1.0f) continue;
            float ndx = dx / len;
            float ndz = dz / len;

            float hw = (hqMaxX - hqMinX) / 2.0f;
            float hh = (hqMaxZ - hqMinZ) / 2.0f;
            if (hw <= 0 || hh <= 0) continue;

            float scaleX = Math.abs(ndx) > 1e-5f ? hw / Math.abs(ndx) : Float.MAX_VALUE;
            float scaleZ = Math.abs(ndz) > 1e-5f ? hh / Math.abs(ndz) : Float.MAX_VALUE;
            float t = Math.min(scaleX, scaleZ);

            float borderX = cx1 + ndx * t;
            float borderZ = cz1 + ndz * t;

            drawOutboundTriangle(g, borderX, borderZ, ndx, ndz, 5.0f, triColor);
        }
    }

    @Unique
    private void drawOutboundTriangle(GuiGraphics g, float tipX, float tipZ, float dirX, float dirZ, float size, int argb) {
        float perpX = -dirZ;
        float perpZ = dirX;

        float baseCenterX = tipX - dirX * size;
        float baseCenterZ = tipZ - dirZ * size;
        float halfBase = size * 0.7f;

        float leftX = baseCenterX + perpX * halfBase;
        float leftZ = baseCenterZ + perpZ * halfBase;
        float rightX = baseCenterX - perpX * halfBase;
        float rightZ = baseCenterZ - perpZ * halfBase;

        CustomColor col = CustomColor.fromInt(argb);
        RenderUtils.drawLine(g, col, tipX, tipZ, leftX, leftZ, 1.5f);
        RenderUtils.drawLine(g, col, tipX, tipZ, rightX, rightZ, 1.5f);
        RenderUtils.drawLine(g, col, leftX, leftZ, rightX, rightZ, 1.5f);
        RenderUtils.drawLine(g, col, tipX, tipZ, (leftX + rightX) / 2.0f, (leftZ + rightZ) / 2.0f, 1.5f);
    }
}
