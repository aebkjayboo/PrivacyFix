package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.Audit;
import dev.sai.privacyfix.PrivacyFix;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import dev.sai.privacyfix.PackSkip;
import dev.sai.privacyfix.PrivacyConfig;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import java.net.URL;
import java.util.List;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cookies (optional), cookie requests (optional) and server transfers (confirm). */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
    @Shadow public abstract void send(Packet<?> packet);
    @Shadow public abstract void handleTransfer(ClientboundTransferPacket packet);

    @Unique private ClientboundTransferPacket privacyfix$approvedTransfer;

    /**
     * Resource pack pushes: remember the listener (the X button needs it to
     * talk to the server from a screen) and auto-skip on remembered servers.
     */
    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void privacyfix$packPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return; // vanilla re-dispatches to the main thread; act there only
        PackSkip.setListener((ClientCommonPacketListenerImpl) (Object) this);
        PrivacyConfig c = PrivacyFix.config();
        if (!PrivacyFix.active(c.packSkip)) return;
        String server = PrivacyConfig.canonicalServer(dev.sai.privacyfix.PacketFilter.currentServerAddress());
        if (server.isEmpty() || !c.packSkipServers.contains(server)) return;
        URL url;
        try {
            url = new URL(packet.url());
        } catch (Exception e) {
            return; // let vanilla report INVALID_URL
        }
        Audit.log("auto-skipping resource pack {} on remembered server {}", packet.id(), server);
        PackSkip.skip((ClientCommonPacketListenerImpl) (Object) this,
                List.of(new PackSkip.Pending(packet.id(), url, packet.hash())), false);
        ci.cancel();
    }

    @Inject(method = "handleRequestCookie", at = @At("HEAD"), cancellable = true)
    private void privacyfix$answerCookieRequest(ClientboundCookieRequestPacket packet, CallbackInfo ci) {
        if (PrivacyFix.active(PrivacyFix.config().blockCookies)) {
            // Vanilla answers with whatever it stored under that key; we store
            // nothing, so answer "nothing" instead of leaving the server waiting.
            Audit.log("answered cookie request {} with nothing", packet.key());
            send(new ServerboundCookieResponsePacket(packet.key(), null));
            ci.cancel();
        }
    }

    @Inject(method = "handleTransfer", at = @At("HEAD"), cancellable = true)
    private void privacyfix$confirmTransfer(ClientboundTransferPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        // First hit is on the netty thread (vanilla re-dispatches to the main
        // thread itself); only act once we are on the main thread.
        if (!mc.isSameThread() || !PrivacyFix.active(PrivacyFix.config().confirmTransfers)) return;
        if (privacyfix$approvedTransfer == packet) return;
        ci.cancel();
        String target = packet.host() + ":" + packet.port();
        Audit.log("server asked to transfer us to {} -- asking", target);
        mc.setScreenAndShow(new ConfirmScreen(yes -> {
            if (yes) {
                privacyfix$approvedTransfer = packet;
                handleTransfer(packet);
            } else {
                Audit.log("transfer to {} refused", target);
                mc.setScreenAndShow(null);
            }
        }, Component.literal("Server transfer"),
           Component.literal("This server wants to send you to " + target + ". Cookies it stored on your client go along. Continue?")));
    }

    @Inject(method = "handleStoreCookie", at = @At("HEAD"), cancellable = true)
    private void privacyfix$blockCookies(ClientboundStoreCookiePacket packet, CallbackInfo ci) {
        if (PrivacyFix.active(PrivacyFix.config().blockCookies)) {
            Audit.log("refused cookie {} ({} bytes)", packet.key(), packet.payload().length);
            ci.cancel();
        }
    }
}
