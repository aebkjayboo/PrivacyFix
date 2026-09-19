package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.Audit;
import dev.sai.privacyfix.PacketFilter;
import dev.sai.privacyfix.PrivacyFix;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The one place every outbound packet passes through. The 1- and 2-arg
 * send(...) overloads both delegate to the 3-arg one (checked in the
 * bytecode of both supported versions), so hooking only that one sees each
 * packet exactly once. Queued packets (sent before the channel is up) are
 * queued from inside send(...) too, so they are covered as well.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Shadow public abstract PacketFlow getReceiving();
    @Shadow public abstract boolean isMemoryConnection();
    @Shadow public abstract void send(Packet<?> packet, ChannelFutureListener listener, boolean flush);

    /** True only for the client's connection to a remote server. */
    private boolean privacyfix$isRemoteClientConnection() {
        // The integrated server's side of a singleplayer connection receives
        // SERVERBOUND; the client side receives CLIENTBOUND. In-memory
        // (singleplayer) connections are left alone so mods keep working
        // against the integrated server.
        return getReceiving() == PacketFlow.CLIENTBOUND && !isMemoryConnection();
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void privacyfix$filterOutbound(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (!privacyfix$isRemoteClientConnection()) return;
        dev.sai.privacyfix.packets.PacketRules.Result result;
        try {
            result = PacketFilter.filter(packet);
        } catch (Throwable t) {
            // Never let a filter bug take the connection down: fail open.
            PrivacyFix.LOGGER.error("Packet filter failed for {}; sending unmodified", packet.getClass().getSimpleName(), t);
            return;
        }
        if (result.packet() == packet && result.extra().isEmpty()) return;
        ci.cancel();
        if (result.packet() != null) {
            // Re-enter send() with the replacement; it passes the filter unchanged.
            send(result.packet(), listener, flush);
        }
        for (Packet<?> copy : result.extra()) {
            send(copy, null, flush);
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"))
    private void privacyfix$auditInbound(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ClientboundCustomPayloadPacket p && privacyfix$isRemoteClientConnection()) {
            String id = p.payload().type().id().toString();
            if (!id.equals("minecraft:brand")) Audit.inboundChannel(id);
        }
    }
}
