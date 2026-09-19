package dev.sai.privacyfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.server.level.ClientInformation;

/**
 * The single decision point for outbound packets. Called from
 * {@code Connection.send(...)} on whatever thread the game sends from, so it
 * must not touch world state -- it only inspects the packet and the config.
 */
public final class PacketFilter {
    private PacketFilter() {}

    /** Last ClientInformation actually sent on this connection (after rewriting), for dedupe. */
    private static volatile ClientInformation lastClientInfo;

    /** Called when a new outbound connection starts. */
    public static void resetConnection() {
        lastClientInfo = null;
    }

    /**
     * The single decision point. Returns a {@link dev.sai.privacyfix.packets.PacketRules.Result}:
     * a (possibly rewritten) packet plus any extra packets to send after it.
     * A null packet means drop.
     */
    public static dev.sai.privacyfix.packets.PacketRules.Result filter(Packet<?> packet) {
        PrivacyConfig c = PrivacyFix.config();
        if (c == null || !c.enabled || isExceptedServer(c))
            return dev.sai.privacyfix.packets.PacketRules.Result.pass(packet);

        Packet<?> result = packet;
        if (packet instanceof ServerboundCustomPayloadPacket p) {
            result = filterCustomPayload(c, p);
        } else if (packet instanceof ServerboundClientInformationPacket p) {
            result = filterClientInformation(c, p);
        } else if (c.blockChatSigning && packet instanceof ServerboundChatSessionUpdatePacket) {
            Audit.log("dropped chat session update (chat signing key not uploaded)");
            result = null;
        } else if (c.blockChatSigning && packet instanceof ServerboundChatPacket p) {
            result = unsign(p);
        } else if (c.blockChatSigning && packet instanceof ServerboundChatCommandSignedPacket p) {
            result = unsign(p);
        } else if (packet instanceof ServerboundCustomQueryAnswerPacket p) {
            if (c.blockModChannels && p.payload() != null) {
                Audit.droppedChannel("login-query#" + p.transactionId());
                result = new ServerboundCustomQueryAnswerPacket(p.transactionId(), null);
            }
        }
        if (result == null) return new dev.sai.privacyfix.packets.PacketRules.Result(null, java.util.Collections.emptyList());
        // the graph runs on whatever survived the built-in filters, then any
        // legacy flat rules from an older config
        dev.sai.privacyfix.packets.PacketRules.Result graphed =
                dev.sai.privacyfix.packets.PacketGraph.run(result, c.packetNodes);
        if (graphed.packet() == null) return graphed;
        dev.sai.privacyfix.packets.PacketRules.Result ruled =
                dev.sai.privacyfix.packets.PacketRules.apply(graphed.packet(), c.packetRules);
        if (graphed.extra().isEmpty()) return ruled;
        java.util.List<net.minecraft.network.protocol.Packet<?>> extra =
                new java.util.ArrayList<>(graphed.extra());
        extra.addAll(ruled.extra());
        return new dev.sai.privacyfix.packets.PacketRules.Result(ruled.packet(), extra);
    }

    private static Packet<?> filterCustomPayload(PrivacyConfig c, ServerboundCustomPayloadPacket p) {
        CustomPacketPayload payload = p.payload();
        if (payload instanceof BrandPayload brand) {
            if (!c.spoofBrand || brand.brand().equals(c.brand)) return p;
            Audit.brand(brand.brand(), c.brand);
            return new ServerboundCustomPayloadPacket(new BrandPayload(c.brand));
        }
        if (!c.blockModChannels) return p;
        String id = payload.type().id().toString();
        if (c.isChannelAllowed(id)) return p;
        // Includes minecraft:register / minecraft:unregister: those come from
        // Fabric API, a vanilla client never sends them.
        Audit.droppedChannel(id);
        return null;
    }

    /**
     * The same message with nothing tying it to your account: no salt, no
     * signature. A server that does not enforce secure chat treats it like
     * any other line.
     */
    private static Packet<?> unsign(ServerboundChatPacket p) {
        if (p.signature() == null && p.salt() == 0L) return p;
        Audit.log("sent chat unsigned");
        return new ServerboundChatPacket(p.message(), p.timeStamp(), 0L, null, p.lastSeenMessages());
    }

    /** A signed command, resent as the plain unsigned form. */
    private static Packet<?> unsign(ServerboundChatCommandSignedPacket p) {
        if (p.argumentSignatures() == null || p.argumentSignatures().entries().isEmpty()) return p;
        Audit.log("sent command unsigned");
        return new ServerboundChatCommandPacket(p.command());
    }

    private static Packet<?> filterClientInformation(PrivacyConfig c, ServerboundClientInformationPacket p) {
        ClientInformation i = p.information();
        String language = i.language();
        boolean allowsListing = i.allowsListing();
        int viewDistance = i.viewDistance();
        StringBuilder what = new StringBuilder();

        if (c.viewDistanceCap > 0 && viewDistance > c.viewDistanceCap) {
            what.append("viewDistance ").append(viewDistance).append(" -> ").append(c.viewDistanceCap).append("; ");
            viewDistance = c.viewDistanceCap;
        }

        boolean textFiltering = i.textFilteringEnabled();
        net.minecraft.server.level.ParticleStatus particles = i.particleStatus();

        if (c.normalizeLanguage && !"en_us".equals(language)) {
            what.append("language ").append(language).append(" -> en_us; ");
            language = "en_us";
        }
        // Neither of these is visible to another player; they are pure
        // client-configuration bits that only the server ever sees.
        if (c.normalizeLanguage && particles != net.minecraft.server.level.ParticleStatus.ALL) {
            what.append("particles ").append(particles).append(" -> ALL; ");
            particles = net.minecraft.server.level.ParticleStatus.ALL;
        }
        if (c.normalizeLanguage && textFiltering) {
            what.append("text filtering true -> false; ");
            textFiltering = false;
        }
        if (c.hideFromServerListings && allowsListing) {
            what.append("allowsListing true -> false; ");
            allowsListing = false;
        }
        ClientInformation out = what.isEmpty() ? i : new ClientInformation(
                language, viewDistance, i.chatVisibility(), i.chatColors(), i.modelCustomisation(),
                i.mainHand(), textFiltering, allowsListing, particles);
        if (c.dedupeClientInfo && out.equals(lastClientInfo)) {
            Audit.log("dropped repeated client information packet (identical to the last one sent)");
            return null;
        }
        lastClientInfo = out;
        if (what.isEmpty()) return p;
        Audit.clientInfo(what.toString().trim());
        return new ServerboundClientInformationPacket(out);
    }

    // ---------------------------------------------------------------

    /** Address captured at ConnectScreen.startConnecting; getCurrentServer() is null before the play phase. */
    private static volatile String connecting = "";

    public static void setConnecting(String address) {
        connecting = address == null ? "" : address;
    }

    public static void clearConnecting() {
        connecting = "";
    }

    /** The address the player typed for the server currently being joined, or "" (singleplayer). */
    public static String currentServerAddress() {
        ServerData sd = Minecraft.getInstance().getCurrentServer();
        if (sd != null && sd.ip != null && !sd.ip.isBlank()) return sd.ip;
        return connecting;
    }

    public static boolean isExceptedServer(PrivacyConfig c) {
        String addr = currentServerAddress();
        return !addr.isEmpty() && c.isServerExcepted(addr);
    }
}
