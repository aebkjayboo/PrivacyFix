package dev.sai.privacyfix;

import dev.sai.privacyfix.compat.ChatCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Records what PrivacyFix did on the current connection. Everything that
 * happens during login/configuration is buffered (there is no chat yet) and
 * flushed as one summary line when the player actually joins the world.
 */
public final class Audit {
    private static final Object LOCK = new Object();
    private static final Set<String> droppedChannels = new LinkedHashSet<>();
    private static final Set<String> inboundChannels = new LinkedHashSet<>();
    private static String originalBrand = null;
    private static boolean brandRewritten = false;
    private static boolean clientInfoRewritten = false;
    private static boolean excepted = false;
    private static String server = "?";

    private Audit() {}

    public static void reset(String serverAddress, boolean serverExcepted) {
        synchronized (LOCK) {
            droppedChannels.clear();
            inboundChannels.clear();
            originalBrand = null;
            brandRewritten = false;
            clientInfoRewritten = false;
            excepted = serverExcepted;
            server = serverAddress;
        }
    }

    public static void brand(String original, String replacement) {
        synchronized (LOCK) {
            originalBrand = original;
            brandRewritten = !original.equals(replacement);
        }
        log("brand: \"{}\" -> \"{}\"", original, replacement);
    }

    public static void droppedChannel(String id) {
        synchronized (LOCK) { droppedChannels.add(id); }
        log("dropped outbound custom payload on channel {}", id);
    }

    public static void clientInfo(String what) {
        synchronized (LOCK) { clientInfoRewritten = true; }
        log("client information: {}", what);
    }

    /** Purely informational: which non-vanilla channels the server is talking on. */
    public static void inboundChannel(String id) {
        boolean first;
        synchronized (LOCK) { first = inboundChannels.add(id); }
        if (first) log("server sent custom payload on channel {}", id);
    }

    /** A probe was detected and neutralised. Shown in chat immediately. */
    public static void probe(String where, List<String> keys) {
        String msg = "probe via " + where + ": " + String.join(", ", keys);
        log("{}", msg);
        chat(prefix().append(Component.literal("blocked " + msg).withStyle(ChatFormatting.RED)));
    }

    private static final Path AUDIT_FILE = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("privacyfix-audit.log");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** Game log AND logs/privacyfix-audit.log (some launchers, e.g. Lunar, filter mod loggers out of latest.log). */
    public static void log(String fmt, Object... args) {
        PrivacyConfig c = PrivacyFix.config();
        if (c == null || !c.auditLog) return;
        PrivacyFix.LOGGER.info("[audit] " + fmt, args);
        String line = fmt;
        for (Object a : args) line = line.replaceFirst("\\{}", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
        synchronized (LOCK) {
            try {
                Files.createDirectories(AUDIT_FILE.getParent());
                Files.writeString(AUDIT_FILE, "[" + LocalTime.now().format(TIME) + "] [" + server + "] " + line + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
        }
    }

    /** Called from ClientPlayConnectionEvents.JOIN: one line in chat. */
    public static void flushToChat() {
        PrivacyConfig c = PrivacyFix.config();
        if (c == null || !c.auditChat) return;
        MutableComponent line = prefix();
        synchronized (LOCK) {
            if (!c.enabled) {
                line.append(Component.literal("disabled in config").withStyle(ChatFormatting.RED));
            } else if (excepted) {
                line.append(Component.literal("server exception, nothing rewritten for " + server)
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                line.append(Component.literal("brand ").withStyle(ChatFormatting.GRAY));
                if (brandRewritten) {
                    line.append(Component.literal(originalBrand + " -> " + c.brand).withStyle(ChatFormatting.GREEN));
                } else {
                    line.append(Component.literal(originalBrand == null ? "not sent?" : originalBrand)
                            .withStyle(ChatFormatting.GREEN));
                }
                line.append(Component.literal(" | dropped channels ").withStyle(ChatFormatting.GRAY));
                line.append(Component.literal(droppedChannels.isEmpty() ? "none"
                        : droppedChannels.size() + " (" + String.join(", ", droppedChannels) + ")")
                        .withStyle(ChatFormatting.GREEN));
                line.append(Component.literal(" | client info ").withStyle(ChatFormatting.GRAY));
                line.append(Component.literal(clientInfoRewritten ? "hardened" : "untouched")
                        .withStyle(ChatFormatting.GREEN));
                line.append(Component.literal(" | probe guard ").withStyle(ChatFormatting.GRAY));
                line.append(Component.literal(c.probeGuard ? "on" : "off")
                        .withStyle(c.probeGuard ? ChatFormatting.GREEN : ChatFormatting.RED));
                if (!inboundChannels.isEmpty()) {
                    line.append(Component.literal(" | server spoke on ").withStyle(ChatFormatting.GRAY));
                    line.append(Component.literal(String.join(", ", inboundChannels)).withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        chat(line);
    }

    public static MutableComponent prefix() {
        return Component.literal("[PrivacyFix] ").withStyle(ChatFormatting.AQUA);
    }

    public static void chat(Component msg) {
        PrivacyConfig c = PrivacyFix.config();
        if (c == null || !c.auditChat) return;
        // Chat can only be written from the render thread.
        Minecraft.getInstance().execute(() -> ChatCompat.send(msg));
    }
}
