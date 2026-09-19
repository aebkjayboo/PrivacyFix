package dev.sai.privacyfix.names;

import dev.sai.privacyfix.Audit;
import dev.sai.privacyfix.PrivacyConfig;
import dev.sai.privacyfix.PrivacyFix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.regex.Pattern;

/**
 * Asks the server to complete "/msg " and keeps the answer.
 *
 * The client's player list only holds the players the server chose to send,
 * which on a network is usually just your own lobby; global chat comes from
 * everyone. The completion for a message command is the one place a server
 * reliably names every player you could talk to, so one request at join time
 * gives the alias table almost everything it needs before anybody speaks.
 *
 * The request is an ordinary tab-completion, identical to the one the client
 * sends the moment you type "/msg " yourself, and only the reply to our own
 * transaction id is read.
 */
public final class NameHarvest {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    /** Transaction ids are ours alone; well outside the range the chat screen uses. */
    private static final int BASE_ID = 0x5A100;
    private static final String[] COMMANDS = {"/msg ", "/tell ", "/w ", "/party invite ", "/friend add "};
    /** Ask again on a slow loop so players who arrive later are aliased too. */
    private static final int REFRESH_TICKS = 20 * 60;

    private static volatile int pending = -1;
    private static int attempt = 0;
    private static int ticksUntilNext = -1;
    private static int ticksUntilRefresh = -1;

    private NameHarvest() {}

    public static void resetConnection() {
        pending = -1;
        attempt = 0;
        ticksUntilNext = -1;
        ticksUntilRefresh = -1;
    }

    /** Called on join: schedule the first request a moment after the world loads. */
    public static void scheduleOnJoin() {
        PrivacyConfig c = PrivacyFix.config();
        if (!c.enabled || !c.hideOtherNames) return;
        attempt = 0;
        ticksUntilNext = 40; // ~2s, after the server has finished sending the join burst
        ticksUntilRefresh = REFRESH_TICKS;
    }

    /** Called every client tick. */
    public static void tick() {
        if (ticksUntilRefresh > 0 && --ticksUntilRefresh == 0) {
            // start another full pass; players come and go
            attempt = 0;
            ticksUntilNext = 1;
            ticksUntilRefresh = REFRESH_TICKS;
        }
        if (ticksUntilNext < 0) return;
        if (--ticksUntilNext > 0) return;
        ticksUntilNext = -1;
        request();
    }

    private static void request() {
        PrivacyConfig c = PrivacyFix.config();
        if (!c.enabled || !c.hideOtherNames) return;
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null || attempt >= COMMANDS.length) return;
        int id = BASE_ID + attempt;
        pending = id;
        conn.send(new ServerboundCommandSuggestionPacket(id, COMMANDS[attempt]));
        attempt++;
    }

    /**
     * @return true when this reply was ours and must not reach the chat screen.
     */
    public static boolean handleSuggestions(ClientboundCommandSuggestionsPacket packet) {
        if (packet.id() != pending) return false;
        pending = -1;
        int learned = 0;
        for (ClientboundCommandSuggestionsPacket.Entry entry : packet.suggestions()) {
            String text = entry.text();
            if (text == null || !NAME.matcher(text).matches()) continue;
            Names.alias(text);
            learned++;
        }
        if (learned > 0) {
            Audit.log("name harvest: learned {} name(s) from a \"{}\" completion", learned, COMMANDS[Math.max(0, attempt - 1)]);
        }
        // Keep going through the list: different servers name different
        // commands, and each one can know players the previous did not.
        if (attempt < COMMANDS.length) ticksUntilNext = 5;
        return true;
    }
}
