package dev.sai.privacyfix.gui;

import dev.sai.privacyfix.PrivacyConfig;
import dev.sai.privacyfix.PrivacyFix;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * One declarative list of every user-facing setting, consumed by both the
 * Sodium config page and the fallback screen so the two never drift.
 */
public final class Settings {
    public enum Page { PRIVACY, NAME, OVERHEAD }

    public enum Preset { MAX_PRIVACY, BALANCED, OFF, CUSTOM }

    public sealed interface Setting permits Bool, Int, Choice {
        String id();
        Page page();
        String name();
        String tooltip();
    }

    public record Bool(String id, Page page, String name, String tooltip,
                       Function<PrivacyConfig, Boolean> get, BiConsumer<PrivacyConfig, Boolean> set) implements Setting {}

    public record Int(String id, Page page, String name, String tooltip, int min, int max, int step,
                      Function<PrivacyConfig, Integer> get, BiConsumer<PrivacyConfig, Integer> set) implements Setting {}

    /** Cycles through a fixed list of values, like Sodium's cycling control. */
    public record Choice(String id, Page page, String name, String tooltip, List<String> values,
                         Function<PrivacyConfig, String> get, BiConsumer<PrivacyConfig, String> set) implements Setting {}

    /** Brands a vanilla-looking client could plausibly report. */
    public static final List<String> BRANDS = List.of("vanilla", "fabric", "forge", "neoforge", "quilt", "optifine");

    public static final List<Setting> ALL = List.of(
            new Bool("enabled", Page.PRIVACY, "PrivacyFix enabled", "Master switch. Off = the mod does nothing at all.",
                    c -> c.enabled, (c, v) -> c.enabled = v),
            new Choice("brand", Page.PRIVACY, "Client brand", "What the client calls itself in the minecraft:brand packet. Servers read this to tell a modded client from a vanilla one; \"vanilla\" is what an unmodified client sends.",
                    BRANDS, c -> c.brand, (c, v) -> c.brand = v),
            new Bool("channels", Page.PRIVACY, "Hide installed mods", "Servers can ask which plugin channels your client speaks, which lists your mods by name. This sends the empty list a vanilla client sends. Mods that talk to the server (voice chat, world map) stop working unless you add the server to serverExceptions.",
                    c -> c.blockModChannels, (c, v) -> c.blockModChannels = v),
            new Bool("probe", Page.PRIVACY, "Block mod detection", "Servers open an invisible sign or anvil filled with text only a specific mod can translate, then read back what your client returns. This answers exactly like an unmodified client, so mods with no networking of their own stay invisible.",
                    c -> c.probeGuard, (c, v) -> c.probeGuard = v),
            new Bool("listing", Page.PRIVACY, "Hide from server lists", "Keeps you out of the player samples shown when someone pings a server you are on.",
                    c -> c.hideFromServerListings, (c, v) -> c.hideFromServerListings = v),
            new Bool("language", Page.PRIVACY, "Report standard settings", "Report the settings a stock client reports instead of yours: language en_us, particles at full, text filtering off. Nobody in the world can see any of them; they exist only as details that make your client identifiable. Server messages then arrive in English. Your skin parts and main hand are left alone, because those are part of how you look to everyone.",
                    c -> c.normalizeLanguage, (c, v) -> c.normalizeLanguage = v),
            new Bool("signing", Page.PRIVACY, "Do not sign chat", "Your client normally uploads a chat key Mojang signed against your account, and stamps every message with it. That signature proves to anyone holding the message, not just the server, that you typed it, which is what makes a chat report carry to Mojang. This uploads nothing and sends chat and commands unsigned. Off by default: a server running enforce-secure-profile refuses unsigned chat, so your messages would not arrive.",
                    c -> c.blockChatSigning, (c, v) -> c.blockChatSigning = v),
            new Bool("telemetry", Page.PRIVACY, "Block Mojang telemetry", "Disable all session telemetry, not just the optional part vanilla lets you turn off.",
                    c -> c.blockTelemetry, (c, v) -> c.blockTelemetry = v),
            new Bool("realms", Page.PRIVACY, "Block Realms requests", "Stop the client polling the Realms API for flags, availability and news while you sit on the title screen. The Realms button errors while this is on.",
                    c -> c.blockRealms, (c, v) -> c.blockRealms = v),
            new Bool("packheaders", Page.PRIVACY, "Anonymous pack downloads", "Resource packs are usually hosted by a third party, and vanilla sends your username and UUID in the download request. This removes both.",
                    c -> c.stripPackDownloadHeaders, (c, v) -> c.stripPackDownloadHeaders = v),
            new Bool("transfers", Page.PRIVACY, "Confirm server transfers", "A server can silently move you to another server, carrying any cookies it stored. This asks first.",
                    c -> c.confirmTransfers, (c, v) -> c.confirmTransfers = v),
            new Bool("cookies", Page.PRIVACY, "Block server cookies", "Refuse the small data blobs servers can store on your client and read back later. Off by default: proxies use them for legitimate transfers.",
                    c -> c.blockCookies, (c, v) -> c.blockCookies = v),

            new Bool("hidename", Page.NAME, "Hide my name", "Show your own name as \"annon\" everywhere the client draws it: tab list, the nametag above your head, chat, scoreboards, and the window title other people see if you share your screen. The server still knows who you are, because you are logged in as you.",
                    c -> c.hideOwnName, (c, v) -> c.hideOwnName = v),
            new Bool("hideothers", Page.NAME, "Hide other names", "Show everyone else as annon1, annon2, ... Each alias sticks to the same player while you are connected, names are picked up from the player list, from chat and from the server's own command completions, and typing an alias back (\"/msg annon3\") reaches the right player. Anything left unrecognised is masked rather than shown.",
                    c -> c.hideOtherNames, (c, v) -> c.hideOtherNames = v),
            new Bool("hideaddress", Page.NAME, "Hide server address", "Replace the address of every server with annon.gg wherever it appears: the server list, the connecting screen, chat, and the window title.",
                    c -> c.hideServerAddress, (c, v) -> c.hideServerAddress = v),

            new Bool("force", Page.PRIVACY, "Force close (Esc + key)", "Hold Esc and press the force key (Controls -> PrivacyFix, default F) to close a sign, book, container or dialog a server keeps re-opening on you, and refuse new ones for a while.",
                    c -> c.forceClose, (c, v) -> c.forceClose = v),
            new Int("forcelock", Page.PRIVACY, "Force close lasts", "How many seconds server-opened screens stay refused after a force close.", 5, 120, 5,
                    c -> c.forceLockSeconds, (c, v) -> c.forceLockSeconds = v),

            new Bool("dedupe", Page.OVERHEAD, "Drop duplicate packets", "Vanilla re-sends the identical client-information packet on join and on every options change. This sends it once.",
                    c -> c.dedupeClientInfo, (c, v) -> c.dedupeClientInfo = v)
    );

    /**
     * Headings drawn inside a page, keyed by the setting they sit above.
     * A page with one of these reads as two blocks rather than one long
     * list, which is where the anti-trap settings live now: they belong
     * with privacy, not in a tab of their own.
     */
    private static final java.util.Map<String, String> SECTIONS = java.util.Map.of("force", "Crisis");

    /** The heading to draw above this setting, or null for none. */
    public static String sectionBefore(String id) {
        return SECTIONS.get(id);
    }

    private Settings() {}

    /** Copies every setting listed here from one config to another (working-copy model). */
    public static void copy(PrivacyConfig from, PrivacyConfig to) {
        for (Setting s : ALL) {
            if (s instanceof Bool b) b.set.accept(to, b.get.apply(from));
            else if (s instanceof Int i) i.set.accept(to, i.get.apply(from));
            else if (s instanceof Choice ch) ch.set.accept(to, ch.get.apply(from));
        }
    }

    /** True when any listed setting differs between the two configs. */
    public static boolean differs(PrivacyConfig a, PrivacyConfig b) {
        for (Setting s : ALL) {
            if (s instanceof Bool bo && !bo.get.apply(a).equals(bo.get.apply(b))) return true;
            if (s instanceof Int i && !i.get.apply(a).equals(i.get.apply(b))) return true;
            if (s instanceof Choice ch && !ch.get.apply(a).equals(ch.get.apply(b))) return true;
        }
        return false;
    }

    public static String pageName(Page p) {
        return switch (p) {
            case PRIVACY -> "Privacy";
            case NAME -> "Name";
            case OVERHEAD -> "Overhead";
        };
    }

    public static String presetName(Preset p) {
        return switch (p) {
            case MAX_PRIVACY -> "Max privacy";
            case BALANCED -> "Balanced";
            case OFF -> "Off";
            case CUSTOM -> "Custom";
        };
    }

    /**
     * Every switch here reads so that on is the more protective setting, so
     * "max privacy" is simply all of them on -- including the ones that ship
     * off because they change how the game behaves. "Balanced" is what the
     * mod does out of the box.
     */
    public static Preset currentPreset(PrivacyConfig c) {
        if (!c.enabled) return Preset.OFF;
        PrivacyConfig defaults = new PrivacyConfig();
        boolean allOn = true;
        boolean asShipped = true;
        for (Setting s : ALL) {
            if (s instanceof Bool b) {
                boolean v = b.get.apply(c);
                if (!v) allOn = false;
                if (v != b.get.apply(defaults)) asShipped = false;
            }
        }
        if (allOn) return Preset.MAX_PRIVACY;
        if (asShipped) return Preset.BALANCED;
        return Preset.CUSTOM;
    }

    /** Apply a preset to the live config (CUSTOM is a no-op). */
    public static void applyPreset(PrivacyConfig c, Preset p) {
        switch (p) {
            case MAX_PRIVACY -> {
                // including the ones that ship off: names and address hidden,
                // cookies refused, chat unsigned. Each costs something, which
                // is why they are not the default.
                for (Setting s : ALL) if (s instanceof Bool b) b.set.accept(c, true);
            }
            case BALANCED -> {
                PrivacyConfig d = new PrivacyConfig();
                for (Setting s : ALL) if (s instanceof Bool b) b.set.accept(c, b.get.apply(d));
            }
            case OFF -> c.enabled = false;
            case CUSTOM -> { }
        }
        PrivacyFix.LOGGER.info("preset applied: {}", p);
    }
}
