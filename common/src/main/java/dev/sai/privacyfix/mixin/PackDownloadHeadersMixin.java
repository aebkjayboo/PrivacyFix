package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.Audit;
import dev.sai.privacyfix.PrivacyFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * DownloadedPackSource's anonymous download queue attaches
 * X-Minecraft-Username / X-Minecraft-UUID / X-Minecraft-Version /
 * X-Minecraft-Version-ID / X-Minecraft-Pack-Format to every resource pack
 * download. The pack host is usually not the game server. Drop the two
 * identity headers; keep the rest so hosts that validate pack format still
 * serve the file. (Same anonymous class index in 1.21.11 and 26.2, checked.)
 */
@Mixin(targets = "net.minecraft.client.resources.server.DownloadedPackSource$4")
public abstract class PackDownloadHeadersMixin {
    @Inject(method = "createDownloadHeaders", at = @At("RETURN"), cancellable = true)
    private void privacyfix$stripIdentity(CallbackInfoReturnable<Map<String, String>> cir) {
        if (!PrivacyFix.active(PrivacyFix.config().stripPackDownloadHeaders)) return;
        Map<String, String> headers = new HashMap<>(cir.getReturnValue());
        boolean removed = headers.remove("X-Minecraft-Username") != null | headers.remove("X-Minecraft-UUID") != null;
        if (removed) Audit.log("stripped X-Minecraft-Username / X-Minecraft-UUID from resource pack download");
        cir.setReturnValue(headers);
    }
}
