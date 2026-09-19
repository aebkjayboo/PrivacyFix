package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.names.Screens;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The window title is visible outside the game, so it is masked too. */
@Mixin(Minecraft.class)
public abstract class WindowTitleMixin {
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void privacyfix$mask(CallbackInfoReturnable<String> cir) {
        String masked = Screens.mask(cir.getReturnValue());
        if (!masked.equals(cir.getReturnValue())) cir.setReturnValue(masked);
    }
}
