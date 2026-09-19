package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.names.Names;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Turns an alias you can see back into the real name before the text is
 * signed and sent, so "/msg anon3 hi" reaches the right player. Hooked here
 * rather than on the packet because chat is signed after this point, and
 * editing a signed message would invalidate the signature.
 */
@Mixin(ClientPacketListener.class)
public abstract class SendChatMixin {
    @ModifyVariable(method = "sendChat", at = @At("HEAD"), argsOnly = true)
    private String privacyfix$resolveChat(String message) {
        return Names.resolveForSend(message);
    }

    @ModifyVariable(method = "sendCommand", at = @At("HEAD"), argsOnly = true)
    private String privacyfix$resolveCommand(String command) {
        return Names.resolveForSend(command);
    }
}
