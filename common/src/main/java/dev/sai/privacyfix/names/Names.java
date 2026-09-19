package dev.sai.privacyfix.names;

import dev.sai.privacyfix.PrivacyConfig;
import dev.sai.privacyfix.PrivacyFix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player-name aliasing. Your own name becomes "annon"; everyone else becomes
 * anon1, anon2, ... in the order the client first sees them, and an alias
 * sticks to that player for the whole connection.
 *
 * Aliases are applied where the client draws a name (tab list, nametag,
 * chat) and reversed on the way out (chat and commands), so typing the alias
 * you can see reaches the right player. What the server sees is unchanged:
 * you are logged in under your real name and it has to stay that way.
 *
 * Names are learned from three places, because no single one is complete:
 * the player list, the profile attached to a real player-chat packet, and
 * the position of the name in a server-formatted chat line.
 */
public final class Names {
    public static final String OWN_ALIAS = "annon";
    /** Shown when a name cannot be resolved to an alias and must not be leaked. */
    public static final String MASK = "???";
    private static final Pattern ALIAS = Pattern.compile("\\bannon(\\d+)\\b");
    private static final Pattern NAME_TOKEN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    /** Separators custom chat plugins put between the sender and the message. */
    private static final char[] STRONG_SEPARATORS = {'»', '›', '➤'};
    /** Ambiguous in ordinary prose, so only trusted after a short prefix. */
    private static final char[] WEAK_SEPARATORS = {':', '>'};
    /** Single words that show up before a colon and are plainly not players. */
    private static final java.util.Set<String> NOT_NAMES = java.util.Set.of(
            "note", "warning", "error", "tip", "info", "hint", "http", "https", "www", "server", "console");
    /**
     * Words that commonly open a server announcement. Masking these would
     * make broadcasts unreadable for no privacy gain; a player actually
     * called one of them is rare enough to accept.
     */
    private static final java.util.Set<String> COMMON_WORDS = java.util.Set.of(
            "welcome", "thanks", "thank", "congrats", "congratulations", "please", "you", "your", "the", "this",
            "that", "click", "join", "joined", "left", "type", "use", "now", "new", "sale", "shop", "store",
            "vote", "discord", "hello", "hey", "event", "season", "staff", "admin", "owner", "players", "player",
            "team", "game", "match", "round", "win", "won", "lost", "next", "open", "closed", "starting", "started",
            "queue", "party", "friend", "friends", "rank", "ranked", "unranked", "duel", "duels", "map", "maps",
            "and", "for", "with", "from", "all", "get", "got", "has", "have", "was", "are", "not", "but", "out",
            "buy", "free", "help", "news", "update", "updated", "reset", "live", "soon", "here", "there", "what",
            "when", "who", "why", "how", "yes", "yeah", "nope", "lol", "lmao", "bruh", "good", "nice", "wow");
    /** Phrases a server only ever says about a player, used as evidence the preceding word is a name. */
    private static final String[] PLAYER_VERBS = {
            "left", "joined", "is hosting", "is now", "has joined", "has left",
            "wins", "won", "died", "was killed", "killed", "quit", "disconnected",
            "is online", "is offline", "logged in", "logged out"};
    private static final Object LOCK = new Object();

    /** Cached replacement list; see {@link #replacements()}. */
    private static volatile List<Map.Entry<String, String>> cache = List.of();
    private static volatile boolean dirty = true;
    private static volatile boolean cachedOwn, cachedOthers, cachedAddress;
    private static int ticks;
    /** real name -> alias, insertion ordered so numbering is stable. */
    private static final Map<String, String> aliasByName = new LinkedHashMap<>();
    private static final Map<String, String> nameByAlias = new LinkedHashMap<>();
    private static int next = 1;

    private Names() {}

    public static void resetConnection() {
        synchronized (LOCK) {
            aliasByName.clear();
            nameByAlias.clear();
            next = 1;
        }
        Addresses.invalidate();
        dirty = true;
    }

    private static boolean active() {
        PrivacyConfig c = PrivacyFix.config();
        return c.enabled && (c.hideOwnName || c.hideOtherNames || c.hideServerAddress);
    }

    private static boolean isSelf(String name) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getUser() != null && name.equals(mc.getUser().getName());
    }

    /** The alias for a real name, or the name itself when it should not be hidden. */
    public static String alias(String name) {
        if (name == null || name.isEmpty()) return name;
        PrivacyConfig c = PrivacyFix.config();
        if (!c.enabled) return name;
        if (isSelf(name)) return c.hideOwnName ? OWN_ALIAS : name;
        if (!c.hideOtherNames) return name;
        synchronized (LOCK) {
            String existing = aliasByName.get(name);
            if (existing != null) return existing;
            String made = OWN_ALIAS + next++;
            aliasByName.put(name, made);
            nameByAlias.put(made, name);
            dirty = true;
            return made;
        }
    }

    /** Reverses {@link #alias}: "annon3" -> the real name, "annon" -> your name. */
    public static String realName(String alias) {
        if (alias == null) return null;
        if (alias.equals(OWN_ALIAS)) {
            Minecraft mc = Minecraft.getInstance();
            return mc.getUser() != null ? mc.getUser().getName() : alias;
        }
        synchronized (LOCK) {
            String real = nameByAlias.get(alias);
            return real != null ? real : alias;
        }
    }

    /**
     * Rewrites any alias in outgoing chat or command text back to the real
     * name, so "/msg anon3 hi" reaches anon3's owner. Unknown aliases are
     * left alone.
     */
    public static String resolveForSend(String text) {
        PrivacyConfig c = PrivacyFix.config();
        if (!c.enabled || text == null || text.isEmpty()) return text;
        String out = text;
        if (c.hideOwnName) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getUser() != null) {
                out = out.replaceAll("\\b" + Pattern.quote(OWN_ALIAS) + "\\b(?!\\d)",
                        Matcher.quoteReplacement(mc.getUser().getName()));
            }
        }
        if (!c.hideOtherNames) return out;
        Matcher m = ALIAS.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String found = m.group();
            String real;
            synchronized (LOCK) { real = nameByAlias.get(found); }
            m.appendReplacement(sb, Matcher.quoteReplacement(real != null ? real : found));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Learns every player in the client's player list (usually the tab list). */
    public static void learnOnlinePlayers() {
        if (!active()) return;
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        for (PlayerInfo info : conn.getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name != null && !isSelf(name)) alias(name);
        }
    }

    /**
     * Learns the sender of a server-formatted chat line such as
     * "[VIP] Steve » hello". Networks that format chat themselves send it as
     * a system message, so no profile is attached and the sender is often not
     * in the player list either; the only thing left is where the name sits
     * in the line. Only the text before the first separator is considered,
     * and only the last token of it, so rank prefixes are skipped.
     */
    public static void learnFromChatLine(String flat) {
        PrivacyConfig c = PrivacyFix.config();
        if (!c.enabled || !c.hideOtherNames || flat == null) return;
        if (flat.startsWith("[PrivacyFix]")) return; // never learn from our own output

        int cut = -1;
        boolean weak = false;
        int limit = Math.min(flat.length(), 64);
        outer:
        for (int i = 0; i < limit; i++) {
            char ch = flat.charAt(i);
            for (char sep : STRONG_SEPARATORS) {
                if (ch == sep) { cut = i; break outer; }
            }
            for (char sep : WEAK_SEPARATORS) {
                if (ch == sep) { cut = i; weak = true; break outer; }
            }
        }
        if (cut <= 0) return;
        // A weak separator has to look like chat: "Name: text", never "12:34" or "Note:this".
        if (weak && (cut + 1 >= flat.length() || flat.charAt(cut + 1) != ' ')) return;

        String head = flat.substring(0, cut).trim();
        if (head.isEmpty()) return;
        String[] parts = head.split("\\s+");
        // A real chat prefix is a name, optionally behind a rank or icon. Anything
        // longer is a sentence that happens to contain a colon.
        if (parts.length > (weak ? 2 : 3)) return;

        String candidate = parts[parts.length - 1];
        if (!NAME_TOKEN.matcher(candidate).matches()) return;
        if (candidate.startsWith(OWN_ALIAS)) return; // already an alias
        if (NOT_NAMES.contains(candidate.toLowerCase(java.util.Locale.ROOT))) return;
        alias(candidate);
    }

    /**
     * The alias for a player we have a profile for. Used by the tab list and
     * nametags, where the client knows exactly who the entry belongs to, so
     * no text matching is involved and a server-set nickname cannot leak.
     * Returns {@code fallback} when this player should not be hidden.
     */
    public static Component aliasOf(com.mojang.authlib.GameProfile profile, Component fallback) {
        PrivacyConfig c = PrivacyFix.config();
        if (!c.enabled || profile == null || profile.name() == null) return fallback;
        boolean self = isSelf(profile.name());
        if (self ? !c.hideOwnName : !c.hideOtherNames) return fallback;
        return Component.literal(alias(profile.name()));
    }

    /**
     * Last line of defence for chat: if the line still opens with something
     * that looks like a player name and is not one of our aliases, blank it
     * out rather than let it through. Only the leading name is touched, so
     * the message itself stays readable.
     */
    public static Component maskLeadingName(Component c) {
        PrivacyConfig cfg = PrivacyFix.config();
        if (!cfg.enabled || !cfg.hideOtherNames) return c;
        String flat = c.getString();
        if (flat.isEmpty() || flat.startsWith("[PrivacyFix]")) return c;
        String token = leadingNameToken(flat);
        if (token == null) return c;
        return rebuild(c, List.of(Map.entry(token, MASK)));
    }

    /**
     * The player name at the start of a chat line, or null.
     *
     * A word is only treated as a name when the line gives evidence for it:
     * either a chat separator follows it ("Steve > hi"), or it is followed by
     * something only ever said about a player ("Steve left the party"). Without
     * that, ordinary server text would have its first long word masked, which
     * makes announcements unreadable for no privacy gain.
     */
    private static String leadingNameToken(String flat) {
        int limit = Math.min(flat.length(), 48);
        int i = 0, examined = 0;
        while (i < limit && examined < 5) {
            while (i < limit && !nameChar(flat.charAt(i))) i++;
            int start = i;
            while (i < limit && nameChar(flat.charAt(i))) i++;
            if (i == start) break;
            String token = flat.substring(start, i);
            examined++;

            // a rank tag sits inside [ ] or < >: not a name, keep looking
            boolean bracketed = start > 0 && (flat.charAt(start - 1) == '[' || flat.charAt(start - 1) == '<')
                    && i < flat.length() && (flat.charAt(i) == ']' || flat.charAt(i) == '>');
            if (bracketed) continue;
            // too short or too long to be a name (counts, times, "gg")
            if (!NAME_TOKEN.matcher(token).matches()) continue;
            // already ours, or something the normal replacement will handle
            if (token.equals(OWN_ALIAS) || ALIAS.matcher(token).matches()) return null;
            synchronized (LOCK) {
                if (aliasByName.containsKey(token)) return null;
            }
            if (COMMON_WORDS.contains(token.toLowerCase(java.util.Locale.ROOT))) continue;
            return nameEvidence(flat, i) ? token : null;
        }
        return null;
    }

    /** True when what follows the word could only follow a player's name. */
    private static boolean nameEvidence(String flat, int after) {
        int j = after;
        while (j < flat.length() && flat.charAt(j) == ' ') j++;
        if (j < flat.length()) {
            char ch = flat.charAt(j);
            for (char sep : STRONG_SEPARATORS) if (ch == sep) return true;
            for (char sep : WEAK_SEPARATORS) if (ch == sep) return true;
        }
        String rest = flat.substring(Math.min(j, flat.length())).toLowerCase(java.util.Locale.ROOT);
        for (String verb : PLAYER_VERBS) {
            if (rest.startsWith(verb)) return true;
        }
        return false;
    }

    /**
     * Names that must be replaced in rendered text, longest first so
     * substrings do not clash.
     *
     * Every string the game draws asks for this list, so it is cached and
     * rebuilt only when it can have changed: a newly learned name, a
     * different server, one of the three switches flipped, or the periodic
     * re-read in {@link #tick()}. Building it walks the player list and the
     * saved server list, which on a full server is thousands of entries and
     * a file read - far too much to repeat per string, per frame.
     */
    private static List<Map.Entry<String, String>> replacements() {
        PrivacyConfig c = PrivacyFix.config();
        List<Map.Entry<String, String>> local = cache;
        if (!dirty && cachedOwn == c.hideOwnName && cachedOthers == c.hideOtherNames
                && cachedAddress == c.hideServerAddress) {
            return local;
        }
        return rebuildReplacements(c);
    }

    private static synchronized List<Map.Entry<String, String>> rebuildReplacements(PrivacyConfig c) {
        List<Map.Entry<String, String>> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (c.hideOwnName && mc.getUser() != null) {
            out.add(Map.entry(mc.getUser().getName(), OWN_ALIAS));
        }
        if (c.hideOtherNames) {
            synchronized (LOCK) {
                for (Map.Entry<String, String> e : aliasByName.entrySet()) {
                    out.add(Map.entry(e.getKey(), e.getValue()));
                }
            }
        }
        if (c.hideServerAddress) out.addAll(Addresses.replacements());
        out.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        cachedOwn = c.hideOwnName;
        cachedOthers = c.hideOtherNames;
        cachedAddress = c.hideServerAddress;
        cache = List.copyOf(out);
        dirty = false;
        return cache;
    }

    /**
     * Once per client tick. The player list is re-read once a second, which
     * is soon enough for someone joining; a name that appears in chat is
     * learned by the line itself and shows up aliased immediately. The saved
     * server list is re-read every five seconds, so editing it in the
     * multiplayer screen takes effect without a restart.
     */
    public static void tick() {
        if (!active()) return;
        int t = ++ticks;
        if (t % 20 == 0) learnOnlinePlayers();
        if (t % 100 == 0 && PrivacyFix.config().hideServerAddress) {
            Addresses.invalidate();
            dirty = true;
        }
    }

    /** Forces the next lookup to rebuild: a new alias, a new server, changed settings. */
    public static void invalidate() {
        dirty = true;
    }

    public static String sanitize(String text) {
        if (!active() || text == null || text.isEmpty()) return text;
        return replace(text, replacements());
    }

    /**
     * Rebuilds a component with every known player name replaced by its
     * alias, keeping styles, siblings and translation arguments intact.
     */
    public static Component sanitize(Component c) {
        if (!active() || c == null) return c;
        return rebuild(c, replacements());
    }

    /**
     * Chat goes through here: the sender is learned from the shape of the
     * line first (for servers that format chat themselves), so the name is
     * already aliased in the very message that introduced it.
     */
    public static Component sanitizeChat(Component c) {
        if (!active() || c == null) return c;
        learnFromChatLine(c.getString());
        return maskLeadingName(rebuild(c, replacements()));
    }

    private static Component rebuild(Component c, List<Map.Entry<String, String>> reps) {
        ComponentContents contents = c.getContents();
        MutableComponent out;
        if (contents instanceof PlainTextContents plain) {
            out = Component.literal(replace(plain.text(), reps));
        } else if (contents instanceof TranslatableContents t) {
            Object[] args = t.getArgs();
            // "X joined the game" can arrive before the player-info packet
            // that would have taught us the name, so mint the alias here.
            boolean joinLeave = t.getKey().startsWith("multiplayer.player.");
            Object[] newArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (joinLeave) {
                    String plain = a instanceof Component ac2 ? ac2.getString() : a instanceof String s2 ? s2 : null;
                    if (plain != null && !plain.isEmpty()) {
                        newArgs[i] = Component.literal(alias(plain));
                        continue;
                    }
                }
                newArgs[i] = a instanceof Component ac ? rebuild(ac, reps)
                        : a instanceof String s ? replace(s, reps) : a;
            }
            out = MutableComponent.create(new TranslatableContents(t.getKey(), t.getFallback(), newArgs));
        } else {
            out = MutableComponent.create(contents);
        }
        out.setStyle(c.getStyle());
        for (Component sibling : c.getSiblings()) out.append(rebuild(sibling, reps));
        return out;
    }

    /** True when the character can be part of a Minecraft name, so a match there is not a whole name. */
    private static boolean nameChar(char c) {
        return c == '_' || Character.isLetterOrDigit(c);
    }

    /**
     * Replaces whole names only. "Bleary" inside "Bleary__" is left alone,
     * which is what produced names like "cloudzzanon37_" before.
     */
    private static String replace(String s, List<Map.Entry<String, String>> reps) {
        if (s == null || s.isEmpty()) return s;
        String out = s;
        for (Map.Entry<String, String> e : reps) {
            String name = e.getKey(), alias = e.getValue();
            int from = 0;
            while (true) {
                int i = out.indexOf(name, from);
                if (i < 0) break;
                int end = i + name.length();
                boolean leftOk = i == 0 || !nameChar(out.charAt(i - 1));
                boolean rightOk = end >= out.length() || !nameChar(out.charAt(end));
                if (leftOk && rightOk) {
                    out = out.substring(0, i) + alias + out.substring(end);
                    from = i + alias.length();
                } else {
                    from = i + 1;
                }
            }
        }
        return out;
    }
}
