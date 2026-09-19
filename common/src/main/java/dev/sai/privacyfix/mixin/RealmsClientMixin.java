package dev.sai.privacyfix.mixin;

import com.mojang.realmsclient.client.RealmsClient;
import dev.sai.privacyfix.Audit;
import dev.sai.privacyfix.PrivacyConfig;
import dev.sai.privacyfix.PrivacyFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Minecraft builds a RealmsClient during startup, which immediately fetches
 * the Realms feature-flag list over HTTPS (authenticated: it sends your
 * session token). Answer with an empty set instead.
 */
@Mixin(RealmsClient.class)
public abstract class RealmsClientMixin {
    @Inject(method = "fetchFeatureFlags", at = @At("HEAD"), cancellable = true)
    private void privacyfix$noFeatureFlags(CallbackInfoReturnable<Set<String>> cir) {
        PrivacyConfig c = PrivacyFix.config();
        if (!c.enabled || !c.blockRealms) return;
        Audit.log("skipped Realms feature-flag request");
        cir.setReturnValue(Set.of());
    }
}
