package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.Audit;
import dev.sai.privacyfix.Panic;
import dev.sai.privacyfix.compat.ChatCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Play-phase hooks: refuse server-opened screens while force mode is
 * locked, and keep the chat text across a dimension switch (portal loops).
 * Each handler first re-dispatches itself to the main thread, so all of
 * these only act once they are on it.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleOpenSignEditor", at = @At("HEAD"), cancellable = true)
    private void privacyfix$lockSign(ClientboundOpenSignEditorPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread() && Panic.locked()) {
            Audit.log("force lock: refused sign editor at {}", packet.getPos());
            ci.cancel();
        }
    }

    @Inject(method = "handleOpenBook", at = @At("HEAD"), cancellable = true)
    private void privacyfix$lockBook(ClientboundOpenBookPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread() && Panic.locked()) {
            Audit.log("force lock: refused book screen");
            ci.cancel();
        }
    }

    @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true)
    private void privacyfix$lockContainer(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread() && Panic.locked()) {
            Audit.log("force lock: refused container screen #{}", packet.getContainerId());
            // Keep the server's bookkeeping straight: tell it we closed it.
            ((ClientPacketListener) (Object) this).send(new ServerboundContainerClosePacket(packet.getContainerId()));
            ci.cancel();
        }
    }

    /**
     * Mint aliases the moment the server announces a player, so the
     * "X joined the game" message that follows is already aliased.
     */
    @Inject(method = "handlePlayerInfoUpdate", at = @At("TAIL"))
    private void privacyfix$learnNames(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) dev.sai.privacyfix.names.Names.learnOnlinePlayers();
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void privacyfix$keepChat(ClientboundRespawnPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return;
        if (ChatCompat.currentScreen() instanceof ChatScreen chat) {
            Panic.beforeDimensionSwitch(((ChatScreenAccessor) chat).privacyfix$input().getValue());
        }
    }
}
