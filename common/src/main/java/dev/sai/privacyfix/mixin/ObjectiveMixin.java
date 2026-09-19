package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.names.Names;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The sidebar title, which servers also fill with per-player text. */
@Mixin(Objective.class)
public abstract class ObjectiveMixin {
    @Inject(method = "getFormattedDisplayName", at = @At("RETURN"), cancellable = true)
    private void privacyfix$alias(CallbackInfoReturnable<Component> cir) {
        Component sanitized = Names.sanitize(cir.getReturnValue());
        if (sanitized != cir.getReturnValue()) cir.setReturnValue(sanitized);
    }
}
