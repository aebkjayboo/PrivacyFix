package dev.sai.privacyfix.mixin;

import com.mojang.authlib.GameProfile;
import dev.sai.privacyfix.names.Names;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Learns the sender of every player chat message. The tab list only holds
 * the players the server chose to show you, so on a network where chat is
 * global most senders are never in it; their name would otherwise render
 * unaliased. The profile on this packet is authoritative.
 */
@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
    @Inject(method = "handlePlayerChatMessage", at = @At("HEAD"))
    private void privacyfix$learnSender(PlayerChatMessage message, GameProfile sender, ChatType.Bound bound, CallbackInfo ci) {
        if (sender != null && sender.name() != null) Names.alias(sender.name());
    }
}
