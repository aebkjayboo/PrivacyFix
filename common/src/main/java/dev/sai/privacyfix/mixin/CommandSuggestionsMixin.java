package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.names.NameHarvest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swallows the reply to our own name-harvest completion so it never reaches
 * the chat screen; everything else is left for vanilla to handle.
 */
@Mixin(ClientPacketListener.class)
public abstract class CommandSuggestionsMixin {
    @Inject(method = "handleCommandSuggestions", at = @At("HEAD"), cancellable = true)
    private void privacyfix$harvest(ClientboundCommandSuggestionsPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread() && NameHarvest.handleSuggestions(packet)) ci.cancel();
    }
}
