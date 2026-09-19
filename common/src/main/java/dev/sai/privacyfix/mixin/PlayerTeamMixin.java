package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.names.Names;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Everything the scoreboard draws goes through here: sidebar rows, the text
 * under a player's nametag, and team-coloured names. Servers put player names
 * in their sidebar ("Hi, Steve!"), which is otherwise the one place a name
 * still shows after the tab list and chat are covered.
 */
@Mixin(PlayerTeam.class)
public abstract class PlayerTeamMixin {
    @Inject(method = "formatNameForTeam", at = @At("RETURN"), cancellable = true)
    private static void privacyfix$alias(net.minecraft.world.scores.Team team, net.minecraft.network.chat.Component name,
                                         CallbackInfoReturnable<MutableComponent> cir) {
        MutableComponent value = cir.getReturnValue();
        net.minecraft.network.chat.Component sanitized = Names.sanitize(value);
        if (sanitized != value && sanitized instanceof MutableComponent m) cir.setReturnValue(m);
    }
}
