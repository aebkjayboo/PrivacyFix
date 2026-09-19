package dev.sai.privacyfix.probe.mixin;

import dev.sai.privacyfix.probe.ProbeServer;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ServerConfigurationPacketListenerImplMixin {
    @Inject(method = "handleClientInformation", at = @At("HEAD"))
    private void probe$info(ServerboundClientInformationPacket packet, CallbackInfo ci) {
        ProbeServer.LOG.info("[config] CLIENT INFO = {}", packet.information());
    }
}
