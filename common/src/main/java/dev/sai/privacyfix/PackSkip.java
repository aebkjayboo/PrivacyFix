package dev.sai.privacyfix;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "Skip" for server resource packs. The server is told the pack was
 * accepted, downloaded and loaded; nothing is downloaded. The pack requests
 * are kept so the player can pull them for real later from a chat link
 * ({@code /privacyfix packdownload}). Servers where the X was pressed are
 * remembered in {@link PrivacyConfig#packSkipServers} and skipped without a
 * prompt on later joins.
 */
public final class PackSkip {
    public record Pending(UUID id, URL url, String hash) {}

    private static final List<Pending> pending = new ArrayList<>();
    /** Whichever common listener (configuration or play) last received a pack push. */
    private static volatile ClientCommonPacketListenerImpl listener;
    private static boolean noticePending;

    private PackSkip() {}

    public static void resetConnection() {
        synchronized (pending) {
            pending.clear();
            noticePending = false;
        }
        listener = null;
    }

    public static void setListener(ClientCommonPacketListenerImpl l) {
        listener = l;
    }

    public static boolean hasPending() {
        synchronized (pending) { return !pending.isEmpty(); }
    }

    /** Tell the server the pack is in place, remember it for a later real download. */
    public static void skip(ClientCommonPacketListenerImpl l, List<Pending> requests, boolean remember) {
        if (l == null) l = listener;
        if (l == null) {
            PrivacyFix.LOGGER.warn("pack skip requested but no packet listener is known; ignoring");
            return;
        }
        for (Pending p : requests) {
            l.send(new ServerboundResourcePackPacket(p.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
            l.send(new ServerboundResourcePackPacket(p.id(), ServerboundResourcePackPacket.Action.DOWNLOADED));
            l.send(new ServerboundResourcePackPacket(p.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            Audit.log("resource pack {} ({}) skipped: told the server it is loaded", p.id(), p.url());
        }
        synchronized (pending) {
            pending.addAll(requests);
            noticePending = true;
        }
        if (remember) {
            String server = PrivacyConfig.canonicalServer(PacketFilter.currentServerAddress());
            PrivacyConfig c = PrivacyFix.config();
            if (!server.isEmpty() && !c.packSkipServers.contains(server)) {
                c.packSkipServers.add(server);
                c.save();
            }
        }
        // In the play phase chat exists already; otherwise the JOIN event flushes it.
        if (Minecraft.getInstance().player != null) flushNotice();
    }

    /** Called on join (and right after a play-phase skip): the chat line with the download link. */
    public static void flushNotice() {
        synchronized (pending) {
            if (!noticePending || pending.isEmpty()) return;
            noticePending = false;
        }
        Component link = Component.literal("[Download it]").withStyle(s -> s
                .withColor(ChatFormatting.GREEN).withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/privacyfix packdownload"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Download and apply this server's resource pack now (this session only)"))));
        Audit.chat(Audit.prefix()
                .append(Component.literal("You don't have this server's resource pack downloaded. ").withStyle(ChatFormatting.YELLOW))
                .append(link));
    }

    /** The real thing: hand the remembered packs to vanilla's downloader. */
    public static int download() {
        List<Pending> list;
        synchronized (pending) {
            list = new ArrayList<>(pending);
            pending.clear();
        }
        if (list.isEmpty()) {
            Audit.chat(Audit.prefix().append(Component.literal("no skipped resource pack on this connection").withStyle(ChatFormatting.GRAY)));
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            var source = mc.getDownloadedPackSource();
            source.allowServerPacks();
            for (Pending p : list) {
                Audit.log("downloading skipped resource pack {} from {}", p.id(), p.url());
                source.pushPack(p.id(), p.url(), p.hash());
            }
        });
        Audit.chat(Audit.prefix().append(Component.literal("downloading " + list.size() + " resource pack(s)...").withStyle(ChatFormatting.GREEN)));
        return list.size();
    }
}
