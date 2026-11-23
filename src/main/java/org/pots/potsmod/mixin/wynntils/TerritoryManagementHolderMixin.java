package org.pots.potsmod.mixin.wynntils;

import com.wynntils.models.items.items.gui.TerritoryItem;
import com.wynntils.screens.territorymanagement.TerritoryManagementHolder;
import com.wynntils.utils.type.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import net.minecraft.world.item.ItemStack;
import org.pots.potsmod.features.economysandbox.AutoTerritorySyncHandler;
import org.pots.potsmod.features.economysandbox.LiveTerritoryDataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = TerritoryManagementHolder.class, remap = false)
public class TerritoryManagementHolderMixin {

    @Shadow
    private Int2ObjectSortedMap<Pair<ItemStack, TerritoryItem>> territories;

    @Inject(method = "updateRenderedItems", at = @At("TAIL"))
    private void onUpdateRenderedItems(CallbackInfo ci) {
        if (this.territories == null || this.territories.isEmpty()) return;
        List<TerritoryItem> list = new ArrayList<>();
        for (Pair<ItemStack, TerritoryItem> pair : this.territories.values()) {
            if (pair != null && pair.b() != null) {
                list.add(pair.b());
            }
        }
        LiveTerritoryDataManager.updateFromWynntils(list);
        if (AutoTerritorySyncHandler.isActive()) {
            AutoTerritorySyncHandler.onTerritoriesCaptured(list.size());
        }
    }
}
