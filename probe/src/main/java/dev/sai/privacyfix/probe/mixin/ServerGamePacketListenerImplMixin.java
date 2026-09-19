package dev.sai.privacyfix.probe.mixin;

import dev.sai.privacyfix.probe.ProbeServer;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Inject(method = "handleSignUpdate", at = @At("HEAD"))
    private void probe$sign(ServerboundSignUpdatePacket packet, CallbackInfo ci) {
        ProbeServer.LOG.info("SIGN PROBE RESULT lines= {}", String.join(" | ", packet.getLines()));
    }

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"))
    private void probe$slot(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        ProbeServer.LOG.info("HOTBAR SLOT received from client: {}", packet.getSlot());
    }

    @Inject(method = "handleChat", at = @At("HEAD"))
    private void probe$chat(ServerboundChatPacket packet, CallbackInfo ci) {
        ProbeServer.LOG.info("CHAT from client: {}", packet.message());
    }

    @Inject(method = "handleRenameItem", at = @At("HEAD"))
    private void probe$anvil(ServerboundRenameItemPacket packet, CallbackInfo ci) {
        ProbeServer.LOG.info("ANVIL PROBE RESULT name= {}", packet.getName());
    }

    @Inject(method = "handleClientInformation", at = @At("HEAD"))
    private void probe$info(ServerboundClientInformationPacket packet, CallbackInfo ci) {
        ProbeServer.LOG.info("[play] CLIENT INFO = {}", packet.information());
    }
}
