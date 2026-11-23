package org.pots.potsmod.mixin.wynntils;

import com.wynntils.screens.maps.AbstractMapScreen;
import com.wynntils.services.map.pois.Poi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractMapScreen.class)
public interface AbstractMapScreenAccessor {

    @Accessor("mapCenterX")
    float getMapCenterX();

    @Accessor("mapCenterZ")
    float getMapCenterZ();

    @Accessor("centerX")
    float getCenterX();

    @Accessor("centerZ")
    float getCenterZ();

    @Accessor("zoomRenderScale")
    float getZoomRenderScale();

    @Accessor("hovered")
    Poi getHovered();

    @Accessor("zoomLevel")
    float getZoomLevel();

    @Accessor("mapCenterX")
    void setMapCenterX(float mapCenterX);

    @Accessor("mapCenterZ")
    void setMapCenterZ(float mapCenterZ);

    @Invoker("setZoomLevel")
    void callSetZoomLevel(float zoomLevel);

    @Invoker("updateMapCenter")
    void callUpdateMapCenter(float x, float z);

    @Invoker("addMapButton")
    void callAddMapButton(com.wynntils.screens.maps.widgets.MapButton button);
}
