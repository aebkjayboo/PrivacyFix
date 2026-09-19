package dev.sai.privacyfix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import dev.sai.privacyfix.packets.PacketNode;
import dev.sai.privacyfix.packets.PacketRule;

/**
 * config/saisprivacyfix.json. Every field is a plain toggle so it can be
 * edited by hand; {@code /privacyfix reload} re-reads it in-game.
 *
 * Defaults are "maximum privacy": the client looks exactly like an
 * unmodified vanilla client to every server, unless that server is listed
 * in {@link #serverExceptions}.
 */
public final class PrivacyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("saisprivacyfix.json");

    /** Master switch. */
    public boolean enabled = true;

    /** Rewrite the minecraft:brand payload ("fabric", "lunarclient:v..." etc) to {@link #brand}. */
    public boolean spoofBrand = true;
    public String brand = "vanilla";

    /**
     * Drop every outbound custom payload a vanilla client would never send.
     * A vanilla client only ever sends minecraft:brand, so this covers
     * Fabric's minecraft:register channel list, c:version / c:register,
     * fabric:registry/sync replies, lunar:apollo, lunarclient:pm, and any
     * channel any mod registers -- present or future -- with no per-mod work.
     */
    public boolean blockModChannels = true;
    /** Channels that may pass even when blockModChannels is on, e.g. "lunar:apollo". */
    public List<String> allowedChannels = new ArrayList<>();

    /**
     * Servers can detect mods with zero networking by opening a sign editor
     * (or anvil) pre-filled with a translatable component using a mod's lang
     * key: the client translates it and sends the result back. With this on,
     * keys that are not in vanilla's own en_us.json are echoed back as the
     * raw key, which is exactly what an unmodified client would send.
     */
    public boolean probeGuard = true;

    /** Send allowsListing=false: you are left out of server-list player samples. */
    public boolean hideFromServerListings = true;
    /**
     * Report the standard client settings rather than yours: language en_us,
     * particles at full, text filtering off. All three are invisible to
     * other players and exist only as fingerprint bits in the server's log.
     * Skin parts and main hand are deliberately left alone -- they are part
     * of how you look to everyone in the world, not private data.
     */
    public boolean normalizeLanguage = true;

    /**
     * Never upload the chat signing key, and send chat and commands
     * unsigned. The key is issued by Mojang against your account, and a
     * signature proves to anyone -- not just the server -- that you typed a
     * particular line, which is what makes chat reports portable.
     *
     * Off by default: a server started with enforce-secure-profile=true
     * refuses unsigned chat, so your messages would silently not arrive.
     */
    public boolean blockChatSigning = false;

    /** Return TelemetryEventSender.DISABLED so no session telemetry is sent to Mojang. */
    public boolean blockTelemetry = true;

    /**
     * Refuse ClientboundStoreCookie. Off by default: proxies (Velocity, etc)
     * use cookies for legitimate server-to-server transfers.
     */
    public boolean blockCookies = false;

    /**
     * Server-pushed resource packs are downloaded with X-Minecraft-Username and
     * X-Minecraft-UUID headers. Packs are usually hosted by a third party
     * (Dropbox, a CDN, mc-packs.net) that then learns exactly who downloaded
     * what from which IP. Strip the two identity headers; version/pack-format
     * headers stay so hosts that check them still serve the pack.
     */
    public boolean stripPackDownloadHeaders = true;

    /**
     * A server can silently hand you to another host (ClientboundTransfer),
     * carrying cookies with your identity. Ask first.
     */
    public boolean confirmTransfers = true;

    /**
     * The title screen polls the Realms API on a timer (availability, news,
     * invites) whether you use Realms or not. Turn that off. The Realms button
     * then shows an error if clicked; flip this off if you actually use Realms.
     */
    public boolean blockRealms = true;

    /**
     * Adds an X to the "this server requires a resource pack" prompt. X tells
     * the server the pack was accepted, downloaded and loaded without
     * downloading anything, and remembers the server in packSkipServers so
     * later joins skip it silently. A chat line with a download link is shown
     * on join in case you do want it.
     */
    public boolean packSkip = true;
    /** Servers (canonical address) whose packs are auto-skipped. Managed by the X button and /privacyfix packskip. */
    public List<String> packSkipServers = new ArrayList<>();

    // ---- names -------------------------------------------------------

    /**
     * Replace your own name with "annon" everywhere the client shows it:
     * tab list, the nametag above your head, chat, scoreboards, and any
     * other text the game draws, including the window title. The real name
     * still goes to the server, because you are authenticated with it.
     */
    public boolean hideOwnName = false;

    /**
     * Replace every other player's name with annon1, annon2, ... Aliases are
     * stable for as long as you stay connected, they are learned from the
     * player list, chat and the server's own completions, and typing one
     * back ("/msg annon3") is translated to the real name on the way out.
     */
    public boolean hideOtherNames = false;

    /**
     * Replace the address of every server with "annon.gg" wherever it is
     * drawn: the server list, the connecting and disconnect screens, chat,
     * and the window title, which is what other people see if you share
     * your screen or a recording.
     */
    public boolean hideServerAddress = false;

    // ---- anti-trap ---------------------------------------------------

    /**
     * Hold Esc + the "force" key (Controls -> PrivacyFix, default F) to close
     * whatever the server keeps opening (sign, book, container, dialog) and
     * refuse every server-opened screen for forceLockSeconds, then land in the
     * pause menu so Disconnect is reachable.
     */
    public boolean forceClose = true;
    public int forceLockSeconds = 20;

    /**
     * Keep the chat screen usable while standing in a nether portal and across
     * the dimension switches of a portal loop (vanilla closes it every tick
     * and replaces it with the loading screen).
     */
    public boolean portalChat = true;

    // ---- overhead ----------------------------------------------------

    /**
     * Vanilla re-sends the whole ClientInformation packet on join and on any
     * options change, even when nothing relevant changed. Drop exact repeats.
     */
    public boolean dedupeClientInfo = true;

    /**
     * Cap the view distance reported to the server (0 = off). The server
     * streams min(server, client) chunks, and chunk data is by far the biggest
     * thing you receive: capping at 8 when your render distance is 16 cuts
     * chunk traffic by roughly three quarters. You will see fewer chunks.
     */
    public int viewDistanceCap = 0;

    /**
     * Server addresses (as typed in the multiplayer list, e.g. "play.example.net"
     * or "play.example.net:25565") where ALL rewriting is skipped. Use this
     * for modded servers whose mods genuinely need to talk to your client.
     */
    public List<String> serverExceptions = new ArrayList<>();

    /** The packet graph built in the Packets screen. */
    public List<PacketNode> packetNodes = new ArrayList<>();

    /** Older flat rules, still honoured so existing configs keep working. */
    public List<PacketRule> packetRules = new ArrayList<>();

    /** Print what was rewritten/dropped to the game log. */
    public boolean auditLog = true;
    /** Also print a one-line summary (and any probe attempts) to chat on join. */
    public boolean auditChat = true;

    // ---------------------------------------------------------------

    public static PrivacyConfig load() {
        if (Files.exists(PATH)) {
            try {
                PrivacyConfig c = GSON.fromJson(Files.readString(PATH), PrivacyConfig.class);
                if (c != null) {
                    c.normalize();
                    c.save(); // re-write so newly added fields show up in the file
                    return c;
                }
            } catch (Exception e) {
                PrivacyFix.LOGGER.error("Could not read {} -- using defaults", PATH, e);
            }
        }
        PrivacyConfig c = new PrivacyConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            PrivacyFix.LOGGER.error("Could not write {}", PATH, e);
        }
    }

    private void normalize() {
        if (allowedChannels == null) allowedChannels = new ArrayList<>();
        if (serverExceptions == null) serverExceptions = new ArrayList<>();
        if (packSkipServers == null) packSkipServers = new ArrayList<>();
        if (packetRules == null) packetRules = new ArrayList<>();
        if (packetNodes == null) packetNodes = new ArrayList<>();
        packSkipServers.replaceAll(PrivacyConfig::canonicalServer);
        if (brand == null || brand.isBlank()) brand = "vanilla";
        allowedChannels.replaceAll(s -> s.toLowerCase(Locale.ROOT).trim());
        serverExceptions.replaceAll(PrivacyConfig::canonicalServer);
    }

    /** Lower-case, trimmed, and without a trailing default port so "x" and "x:25565" match. */
    public static String canonicalServer(String s) {
        if (s == null) return "";
        s = s.toLowerCase(Locale.ROOT).trim();
        if (s.endsWith(":25565")) s = s.substring(0, s.length() - 6);
        return s;
    }

    public boolean isServerExcepted(String address) {
        return serverExceptions.contains(canonicalServer(address));
    }

    public boolean isChannelAllowed(String id) {
        return allowedChannels.contains(id.toLowerCase(Locale.ROOT));
    }
}
