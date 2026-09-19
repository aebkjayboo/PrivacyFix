package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.names.Names;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The nametag above a player. Like the tab list, the profile is known, so
 * the alias comes straight from it instead of from the rendered text.
 */
@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void privacyfix$alias(CallbackInfoReturnable<Component> cir) {
        Player self = (Player) (Object) this;
        Component replaced = Names.aliasOf(self.getGameProfile(), null);
        if (replaced != null) {
            cir.setReturnValue(replaced);
            return;
        }
        Component sanitized = Names.sanitize(cir.getReturnValue());
        if (sanitized != cir.getReturnValue()) cir.setReturnValue(sanitized);
    }
}
