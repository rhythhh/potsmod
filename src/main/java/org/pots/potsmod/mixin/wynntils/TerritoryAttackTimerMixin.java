package org.pots.potsmod.mixin.wynntils;

import com.wynntils.models.territories.TerritoryAttackTimer;
import net.minecraft.ChatFormatting;
import org.pots.potsmod.managers.QueuerTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TerritoryAttackTimer.class, remap = false)
public abstract class TerritoryAttackTimerMixin {
    @Shadow
    public abstract String territoryName();

    @Inject(method = "asString", at = @At("RETURN"), cancellable = true)
    private void rmod$appendQueuerName(CallbackInfoReturnable<String> cir) {
        String queuer = QueuerTracker.getQueuer(territoryName());
        if (queuer != null && !queuer.isEmpty()) {
            cir.setReturnValue(cir.getReturnValue() + " §f" + queuer);
        }
    }
}
