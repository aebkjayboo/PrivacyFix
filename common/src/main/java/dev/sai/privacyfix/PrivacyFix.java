package dev.sai.privacyfix;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;


public final class PrivacyFix implements ClientModInitializer {
    public static final String MOD_ID = "saisprivacyfix";
    public static final Logger LOGGER = LoggerFactory.getLogger("PrivacyFix");

    private static volatile PrivacyConfig config;

    /** Never null; loads on first use because some hooks (telemetry) fire before onInitializeClient. */
    public static PrivacyConfig config() {
        PrivacyConfig c = config;
        if (c == null) {
            synchronized (PrivacyFix.class) {
                c = config;
                if (c == null) config = c = PrivacyConfig.load();
            }
        }
        return c;
    }

    /** True when a server-facing feature should act: master switch on, feature on, current server not excepted. */
    public static boolean active(boolean feature) {
        PrivacyConfig c = config();
        return c.enabled && feature && !PacketFilter.isExceptedServer(c);
    }

    public static void reload() {
        config = PrivacyConfig.load();
        dev.sai.privacyfix.names.Names.invalidate();
    }

    public static boolean sodiumPresent() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium");
    }

    /** Mod version for the settings header: "1.0.0", without the build's game-version suffix. */
    public static String version() {
        String v = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
        int plus = v.indexOf('+');
        return plus > 0 ? v.substring(0, plus) : v;
    }

    @Override
    public void onInitializeClient() {
        PrivacyConfig c = config();
        LOGGER.info("Sai's PrivacyFix loaded (enabled={}, brand={}, blockModChannels={}, probeGuard={}, exceptions={})",
                c.enabled, c.brand, c.blockModChannels, c.probeGuard, c.serverExceptions);

        Panic.register();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Panic.tick(mc);
            dev.sai.privacyfix.names.Names.tick();
            dev.sai.privacyfix.names.NameHarvest.tick();
        });

        // Login handler created = a fresh outbound connection. The brand and
        // client-information packets go out right after this, so reset here.
        ClientLoginConnectionEvents.INIT.register((handler, client) -> {
            String addr = PacketFilter.currentServerAddress();
            PacketFilter.resetConnection();
            PackSkip.resetConnection();
            Panic.resetConnection();
            dev.sai.privacyfix.names.Names.resetConnection();
            dev.sai.privacyfix.names.NameHarvest.resetConnection();
            Audit.reset(addr, !addr.isEmpty() && config().isServerExcepted(addr));
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Audit.flushToChat();
            PackSkip.flushNotice();
            dev.sai.privacyfix.names.Names.learnOnlinePlayers();
            dev.sai.privacyfix.names.NameHarvest.scheduleOnJoin();
        });
        // Forget the remembered address so singleplayer afterwards is not mistaken for that server.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PacketFilter.clearConnecting());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("privacyfix")
                        .executes(ctx -> status(ctx.getSource()))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(ctx -> status(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reload").executes(ctx -> {
                            reload();
                            feedback(ctx, "config reloaded");
                            return 1;
                        }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("except")
                                .executes(ctx -> except(ctx, PacketFilter.currentServerAddress(), true))
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("server", StringArgumentType.greedyString())
                                        .executes(ctx -> except(ctx, StringArgumentType.getString(ctx, "server"), true))))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("unexcept")
                                .executes(ctx -> except(ctx, PacketFilter.currentServerAddress(), false))
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("server", StringArgumentType.greedyString())
                                        .executes(ctx -> except(ctx, StringArgumentType.getString(ctx, "server"), false))))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("packdownload")
                                .executes(ctx -> PackSkip.download()))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("packskip")
                                .executes(ctx -> {
                                    String s = PrivacyConfig.canonicalServer(PacketFilter.currentServerAddress());
                                    if (s.isEmpty()) { error(ctx, "not connected to a server"); return 0; }
                                    PrivacyConfig c2 = config();
                                    boolean now;
                                    if (c2.packSkipServers.remove(s)) now = false; else { c2.packSkipServers.add(s); now = true; }
                                    c2.save();
                                    feedback(ctx, "resource pack auto-skip for " + s + " is now " + (now ? "on" : "off") + " (applies on next join)");
                                    return 1;
                                }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("viewdistance")
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("chunks", IntegerArgumentType.integer(0, 64))
                                        .executes(ctx -> {
                                            PrivacyConfig c2 = config();
                                            c2.viewDistanceCap = IntegerArgumentType.getInteger(ctx, "chunks");
                                            c2.save();
                                            feedback(ctx, "view distance cap " + (c2.viewDistanceCap > 0 ? c2.viewDistanceCap + " (applies on next join)" : "off"));
                                            return 1;
                                        })))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle")
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("feature", StringArgumentType.word())
                                        .executes(ctx -> toggle(ctx, StringArgumentType.getString(ctx, "feature")))))
        ));
    }

    // ---------------------------------------------------------------

    private static int status(FabricClientCommandSource src) {
        PrivacyConfig c = config();
        String addr = PacketFilter.currentServerAddress();
        src.sendFeedback(Audit.prefix().append(Component.literal("status" + (addr.isEmpty() ? "" : " on " + addr))
                .withStyle(ChatFormatting.WHITE)));
        src.sendFeedback(flag("enabled", c.enabled));
        src.sendFeedback(flag("brand", c.spoofBrand).append(Component.literal(" (" + c.brand + ")").withStyle(ChatFormatting.GRAY)));
        src.sendFeedback(flag("channels", c.blockModChannels).append(Component.literal(
                c.allowedChannels.isEmpty() ? "" : " allowed: " + String.join(", ", c.allowedChannels)).withStyle(ChatFormatting.GRAY)));
        src.sendFeedback(flag("probe", c.probeGuard));
        src.sendFeedback(flag("listing", c.hideFromServerListings));
        src.sendFeedback(flag("language", c.normalizeLanguage));
        src.sendFeedback(flag("telemetry", c.blockTelemetry));
        src.sendFeedback(flag("cookies", c.blockCookies));
        src.sendFeedback(flag("packheaders", c.stripPackDownloadHeaders));
        src.sendFeedback(flag("transfers", c.confirmTransfers));
        src.sendFeedback(flag("realms", c.blockRealms));
        src.sendFeedback(flag("dedupe", c.dedupeClientInfo));
        src.sendFeedback(flag("force", c.forceClose).append(Component.literal(" (Esc + force key, " + c.forceLockSeconds + "s lock)").withStyle(ChatFormatting.GRAY)));
        src.sendFeedback(flag("portalchat", c.portalChat));
        src.sendFeedback(flag("packskip", c.packSkip).append(Component.literal(
                c.packSkipServers.isEmpty() ? "" : " remembered: " + String.join(", ", c.packSkipServers)).withStyle(ChatFormatting.GRAY)));
        src.sendFeedback(Component.literal("  viewdistance cap: " + (c.viewDistanceCap > 0 ? c.viewDistanceCap : "off")).withStyle(ChatFormatting.GRAY));
        src.sendFeedback(flag("chat", c.auditChat));
        src.sendFeedback(flag("log", c.auditLog));
        src.sendFeedback(Component.literal("  exceptions: " + (c.serverExceptions.isEmpty() ? "none" : String.join(", ", c.serverExceptions)))
                .withStyle(ChatFormatting.GRAY));
        if (!addr.isEmpty() && c.isServerExcepted(addr)) {
            src.sendFeedback(Component.literal("  this server is excepted: nothing is being rewritten").withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static MutableComponent flag(String name, boolean on) {
        return Component.literal("  " + name + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(on ? "on" : "off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static int except(CommandContext<FabricClientCommandSource> ctx, String server, boolean add) {
        String s = PrivacyConfig.canonicalServer(server);
        if (s.isEmpty()) {
            error(ctx, "not connected to a server; give an address: /privacyfix except play.example.net");
            return 0;
        }
        PrivacyConfig c = config();
        if (add) {
            if (!c.serverExceptions.contains(s)) c.serverExceptions.add(s);
            feedback(ctx, s + " added to exceptions (takes effect on next join)");
        } else {
            c.serverExceptions.remove(s);
            feedback(ctx, s + " removed from exceptions (takes effect on next join)");
        }
        c.save();
        return 1;
    }

    /** Every keyword {@link #toggle} accepts, in one place so the help text cannot drift. */
    private static final String TOGGLES = "all brand channels probe listing language telemetry cookies "
            + "packheaders transfers realms dedupe packs force portalchat signing "
            + "hidename hideothers hideaddress chat log";

    private static int toggle(CommandContext<FabricClientCommandSource> ctx, String feature) {
        PrivacyConfig c = config();
        boolean now;
        switch (feature.toLowerCase(Locale.ROOT)) {
            case "enabled", "all" -> now = c.enabled = !c.enabled;
            case "brand" -> now = c.spoofBrand = !c.spoofBrand;
            case "channels" -> now = c.blockModChannels = !c.blockModChannels;
            case "probe" -> now = c.probeGuard = !c.probeGuard;
            case "listing" -> now = c.hideFromServerListings = !c.hideFromServerListings;
            case "language" -> now = c.normalizeLanguage = !c.normalizeLanguage;
            case "telemetry" -> now = c.blockTelemetry = !c.blockTelemetry;
            case "cookies" -> now = c.blockCookies = !c.blockCookies;
            case "packheaders" -> now = c.stripPackDownloadHeaders = !c.stripPackDownloadHeaders;
            case "transfers" -> now = c.confirmTransfers = !c.confirmTransfers;
            case "realms" -> now = c.blockRealms = !c.blockRealms;
            case "dedupe" -> now = c.dedupeClientInfo = !c.dedupeClientInfo;
            case "packs", "packskipbutton" -> now = c.packSkip = !c.packSkip;
            case "force" -> now = c.forceClose = !c.forceClose;
            case "portalchat" -> now = c.portalChat = !c.portalChat;
            case "signing" -> now = c.blockChatSigning = !c.blockChatSigning;
            case "hidename" -> now = c.hideOwnName = !c.hideOwnName;
            case "hideothers" -> now = c.hideOtherNames = !c.hideOtherNames;
            case "hideaddress" -> now = c.hideServerAddress = !c.hideServerAddress;
            case "chat" -> now = c.auditChat = !c.auditChat;
            case "log" -> now = c.auditLog = !c.auditLog;
            default -> {
                error(ctx, "unknown feature; one of: " + TOGGLES);
                return 0;
            }
        }
        c.save();
        dev.sai.privacyfix.names.Names.invalidate();
        feedback(ctx, feature + " is now " + (now ? "on" : "off") + " (brand/channels/listing/language apply on next join)");
        return 1;
    }

    private static void feedback(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(Audit.prefix().append(Component.literal(msg).withStyle(ChatFormatting.WHITE)));
    }

    private static void error(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendError(Audit.prefix().append(Component.literal(msg).withStyle(ChatFormatting.RED)));
    }
}
