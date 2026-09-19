package dev.sai.privacyfix.mixin;

import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import dev.sai.privacyfix.PrivacyConfig;
import dev.sai.privacyfix.PrivacyFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The title screen embeds this invisible screen, which subscribes a
 * DataFetcher that polls the Realms API (news, invites, trial status) on a
 * timer for as long as you sit on the title screen. Skip init and tick so
 * the fetcher is never started.
 */
@Mixin(RealmsNotificationsScreen.class)
public abstract class RealmsNotificationsScreenMixin {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void privacyfix$noInit(CallbackInfo ci) {
        PrivacyConfig c = PrivacyFix.config();
        if (c.enabled && c.blockRealms) ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void privacyfix$noTick(CallbackInfo ci) {
        PrivacyConfig c = PrivacyFix.config();
        if (c.enabled && c.blockRealms) ci.cancel();
    }
}
