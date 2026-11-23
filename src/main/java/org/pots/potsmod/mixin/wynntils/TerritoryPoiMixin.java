package org.pots.potsmod.mixin.wynntils;

import com.wynntils.models.territories.TerritoryInfo;
import com.wynntils.models.territories.type.GuildResourceValues;
import com.wynntils.services.map.pois.TerritoryPoi;
import com.wynntils.utils.colors.CustomColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(TerritoryPoi.class)
public abstract class TerritoryPoiMixin {

    @Shadow
    public abstract TerritoryInfo getTerritoryInfo();

    @ModifyVariable(
            method = "renderAt",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;stream()Ljava/util/stream/Stream;"
            ),
            ordinal = 0
    )
    private List<CustomColor> modifyBoxColors(List<CustomColor> colors) {
        return colors;
    }
}
