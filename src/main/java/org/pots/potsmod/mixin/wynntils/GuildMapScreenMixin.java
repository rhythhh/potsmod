package org.pots.potsmod.mixin.wynntils;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.wynntils.screens.maps.GuildMapScreen;
import com.wynntils.screens.maps.widgets.MapButton;
import com.wynntils.services.map.pois.TerritoryPoi;
import com.wynntils.utils.render.Texture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.pots.potsmod.eventbus.REventBus;
import org.pots.potsmod.models.events.HoveredTerritoryInfoRenderEvent;
import org.pots.potsmod.models.events.TerritoryTooltipHeightEvent;
import org.pots.potsmod.models.events.TerritoryTooltipRenderEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuildMapScreen.class)
public class GuildMapScreenMixin {
    @Unique
    private static MapButton colorModeButton;

    @Inject(method = "doInit", at = @At("TAIL"))
    private void addColorModeButton(CallbackInfo ci) {
        // Restore remembered zoom and center if available
        if (!Float.isNaN(org.pots.potsmod.features.territorymap.WynntilsMapState.lastZoomLevel)) {
            AbstractMapScreenAccessor accessor = (AbstractMapScreenAccessor) this;
            accessor.callSetZoomLevel(org.pots.potsmod.features.territorymap.WynntilsMapState.lastZoomLevel);
            if (!Float.isNaN(org.pots.potsmod.features.territorymap.WynntilsMapState.lastMapCenterX)
                    && !Float.isNaN(org.pots.potsmod.features.territorymap.WynntilsMapState.lastMapCenterZ)) {
                accessor.callUpdateMapCenter(
                        org.pots.potsmod.features.territorymap.WynntilsMapState.lastMapCenterX,
                        org.pots.potsmod.features.territorymap.WynntilsMapState.lastMapCenterZ
                );
            }
        }

        colorModeButton = new MapButton(
                Texture.DEFENSE_FILTER_ICON,
                button -> {
                    org.pots.potsmod.features.territorymap.WynntilsMapState.setColorMode(
                            (org.pots.potsmod.features.territorymap.WynntilsMapState.getColorMode() + 1) % 2);
                    updateColorModeTooltip();
                },
                getColorModeTooltip()
        );
        ((AbstractMapScreenAccessor) this).callAddMapButton(colorModeButton);
    }

    @Unique
    private static void updateColorModeTooltip() {
        if (colorModeButton != null) {
            colorModeButton.setTooltip(getColorModeTooltip());
        }
    }

    @Unique
    private static List<Component> getColorModeTooltip() {
        boolean isDefense = org.pots.potsmod.features.territorymap.WynntilsMapState.getColorMode() == 1;
        return List.of(
                Component.literal("Territory Color Mode: " + (isDefense ? "Defense Tier" : "Guild Color"))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(isDefense
                        ? "Currently displaying defense tier colors. Click to switch to guild owner colors."
                        : "Currently displaying guild owner colors. Click to switch to defense tier colors.")
                        .withStyle(ChatFormatting.GRAY)
        );
    }

    @Inject(method = "renderTerritoryTooltip", at = @At(ordinal = 4, value = "INVOKE", target = "Lcom/wynntils/utils/render/FontRenderer;renderText(Lnet/minecraft/client/gui/GuiGraphics;Lcom/wynntils/core/text/StyledText;FFLcom/wynntils/utils/colors/CustomColor;Lcom/wynntils/utils/render/type/HorizontalAlignment;Lcom/wynntils/utils/render/type/VerticalAlignment;Lcom/wynntils/utils/render/type/TextShadow;)V", shift = At.Shift.AFTER))
    private static void renderEstimatedDefenses(
            GuiGraphics guiGraphics,
            int xOffset,
            int yOffset,
            TerritoryPoi territoryPoi,
            CallbackInfo ci,
            @Local(name = "renderYOffset") LocalFloatRef renderYOffset) {
        REventBus.dispatch(new TerritoryTooltipRenderEvent(guiGraphics, xOffset, yOffset, territoryPoi, renderYOffset));
    }

    @ModifyVariable(method = "renderTerritoryTooltip", at = @At("STORE"), name = "centerHeight")
    private static float increaseTooltipHeight(float centerHeight, @Local(argsOnly = true) TerritoryPoi territoryPoi) {
        TerritoryTooltipHeightEvent event = new TerritoryTooltipHeightEvent(territoryPoi);
        REventBus.dispatch(event);
        return centerHeight + event.getAdditionalHeight();
    }

    @Inject(
            method = "doRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/wynntils/screens/maps/GuildMapScreen;renderPois(Lnet/minecraft/client/gui/GuiGraphics;II)V",
                    shift = At.Shift.AFTER
            )
    )
    private void renderHoveredTerritoryPaths(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        AbstractMapScreenAccessor accessor = (AbstractMapScreenAccessor) this;
        if (!(accessor.getHovered() instanceof TerritoryPoi territoryPoi)) {
            return;
        }

        float mapCenterX = accessor.getMapCenterX();
        float mapCenterZ = accessor.getMapCenterZ();
        float centerX = accessor.getCenterX();
        float centerZ = accessor.getCenterZ();
        float zoomRenderScale = accessor.getZoomRenderScale();

        REventBus.dispatch(new HoveredTerritoryInfoRenderEvent(
                guiGraphics,
                territoryPoi,
                mapCenterX,
                mapCenterZ,
                centerX,
                centerZ,
                zoomRenderScale
        ));
    }

    @Inject(method = "doRender", at = @At("HEAD"))
    private void trackCurrentMapState(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        AbstractMapScreenAccessor accessor = (AbstractMapScreenAccessor) this;
        org.pots.potsmod.features.territorymap.WynntilsMapState.lastZoomLevel = accessor.getZoomLevel();
        org.pots.potsmod.features.territorymap.WynntilsMapState.lastMapCenterX = accessor.getMapCenterX();
        org.pots.potsmod.features.territorymap.WynntilsMapState.lastMapCenterZ = accessor.getMapCenterZ();
    }
}
