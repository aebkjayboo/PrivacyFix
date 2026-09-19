package dev.sai.privacyfix.probe.mixin;

import dev.sai.privacyfix.probe.ProbeServer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Inject(method = "handleResourcePackResponse", at = @At("HEAD"))
    private void probe$pack(ServerboundResourcePackPacket packet, CallbackInfo ci) {
        ProbeServer.LOG.info("RESOURCE PACK STATUS from client: {} for {}", packet.action(), packet.id());
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"))
    private void probe$log(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        Object payload = packet.payload();
        String phase = getClass().getSimpleName().replace("PacketListenerImpl", "");
        if (payload instanceof BrandPayload b) {
            ProbeServer.LOG.info("[{}] CLIENT BRAND = \"{}\"", phase, b.brand());
        } else {
            ProbeServer.LOG.info("[{}] CUSTOM PAYLOAD channel={} payload={}", phase, packet.payload().type().id(), payload);
        }
    }
}
