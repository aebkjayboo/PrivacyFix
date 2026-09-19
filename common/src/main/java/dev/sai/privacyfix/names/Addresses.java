package dev.sai.privacyfix.names;

import dev.sai.privacyfix.PacketFilter;
import dev.sai.privacyfix.PrivacyConfig;
import dev.sai.privacyfix.PrivacyFix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server addresses, replaced with a single stand-in wherever they are drawn.
 *
 * The addresses come from the server you are on and from your saved server
 * list, so a screenshot or a stream never shows which servers you play on,
 * including the ones you are not connected to. The real address is still
 * used to connect; only what is displayed changes.
 */
public final class Addresses {
    public static final String MASK = "annon.gg";

    /**
     * Cached, because building this reads servers.dat. {@link Names} asks
     * for it while rebuilding its own list and invalidates us when the
     * connection changes or enough time has passed.
     */
    private static volatile List<Map.Entry<String, String>> cache;

    private Addresses() {}

    /** Forces the next call to re-read the current server and the saved list. */
    public static void invalidate() {
        cache = null;
    }

    /** Every address worth masking, longest first so "x.net:25565" wins over "x.net". */
    public static List<Map.Entry<String, String>> replacements() {
        PrivacyConfig c = PrivacyFix.config();
        if (!c.enabled || !c.hideServerAddress) return List.of();
        List<Map.Entry<String, String>> local = cache;
        if (local != null) return local;
        Set<String> seen = new LinkedHashSet<>();
        collect(seen, PacketFilter.currentServerAddress());
        Minecraft mc = Minecraft.getInstance();
        ServerData current = mc.getCurrentServer();
        if (current != null) {
            collect(seen, current.ip);
            collect(seen, current.name);
        }
        // the saved list too: the multiplayer screen shows all of them at once
        try {
            ServerList list = new ServerList(mc);
            list.load();
            for (int i = 0; i < list.size(); i++) collect(seen, list.get(i).ip);
        } catch (Throwable ignored) {
            // a missing or unreadable servers.dat is not worth failing over
        }
        List<Map.Entry<String, String>> out = new ArrayList<>(seen.size());
        for (String address : seen) out.add(Map.entry(address, MASK));
        out.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        cache = List.copyOf(out);
        return cache;
    }

    /** Adds the address and its bare host, so "play.x.net:25565" and "play.x.net" both match. */
    private static void collect(Set<String> into, String address) {
        if (address == null || address.isBlank()) return;
        String trimmed = address.trim();
        into.add(trimmed);
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0) into.add(trimmed.substring(0, colon));
    }

}
