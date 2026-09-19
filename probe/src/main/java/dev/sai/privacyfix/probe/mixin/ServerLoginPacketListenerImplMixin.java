package dev.sai.privacyfix.probe.mixin;

import dev.sai.privacyfix.probe.ProbeServer;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {
    @Inject(method = "handleCustomQueryPacket", at = @At("HEAD"))
    private void probe$query(ServerboundCustomQueryAnswerPacket packet, CallbackInfo ci) {
        ProbeServer.LOG.info("[login] QUERY ANSWER id={} payload={}", packet.transactionId(), packet.payload());
    }
}
