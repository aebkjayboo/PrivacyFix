package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.PrivacyConfig;
import dev.sai.privacyfix.PrivacyFix;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla closes any screen that is not allowed in a portal every tick you stand in one. Chat is allowed now. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "isAllowedInPortal", at = @At("HEAD"), cancellable = true)
    private void privacyfix$chatInPortal(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ChatScreen) {
            PrivacyConfig c = PrivacyFix.config();
            if (c.enabled && c.portalChat) cir.setReturnValue(true);
        }
    }
}
