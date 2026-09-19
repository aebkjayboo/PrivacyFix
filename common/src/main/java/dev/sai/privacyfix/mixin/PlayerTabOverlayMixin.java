package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.names.Names;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tab list names. The entry belongs to a known profile, so the alias comes
 * from that rather than from matching text: a server-set nickname or rank
 * prefix cannot leak a real name through here.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void privacyfix$alias(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        Component replaced = Names.aliasOf(info.getProfile(), null);
        if (replaced != null) {
            cir.setReturnValue(replaced);
            return;
        }
        Component sanitized = Names.sanitize(cir.getReturnValue());
        if (sanitized != cir.getReturnValue()) cir.setReturnValue(sanitized);
    }
}
