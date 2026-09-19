package dev.sai.privacyfix.mixin;

import com.mojang.realmsclient.RealmsAvailability;
import dev.sai.privacyfix.Audit;
import dev.sai.privacyfix.PrivacyConfig;
import dev.sai.privacyfix.PrivacyFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * RealmsAvailability.get() is called every time the title screen is built and
 * re-queries the Realms API every few minutes (compatible-client check plus
 * an authenticated "is this player allowed" call). Answer locally instead.
 */
@Mixin(RealmsAvailability.class)
public abstract class RealmsAvailabilityMixin {
    @Inject(method = "check", at = @At("HEAD"), cancellable = true)
    private static void privacyfix$noAvailabilityCheck(CallbackInfoReturnable<CompletableFuture<RealmsAvailability.Result>> cir) {
        PrivacyConfig c = PrivacyFix.config();
        if (!c.enabled || !c.blockRealms) return;
        Audit.log("skipped Realms availability request");
        cir.setReturnValue(CompletableFuture.completedFuture(new RealmsAvailability.Result(RealmsAvailability.Type.AUTHENTICATION_ERROR)));
    }
}
