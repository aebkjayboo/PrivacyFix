package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.PrivacyConfig;
import dev.sai.privacyfix.PrivacyFix;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.client.telemetry.TelemetryEventSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla only lets you opt out of the "extra" telemetry; this turns off all of it. */
@Mixin(ClientTelemetryManager.class)
public abstract class ClientTelemetryManagerMixin {
    @Inject(method = "createEventSender", at = @At("HEAD"), cancellable = true)
    private void privacyfix$noTelemetry(CallbackInfoReturnable<TelemetryEventSender> cir) {
        PrivacyConfig c = PrivacyFix.config();
        if (c.enabled && c.blockTelemetry) {
            cir.setReturnValue(TelemetryEventSender.DISABLED);
        }
    }
}
