package dev.sai.privacyfix;

import com.mojang.blaze3d.platform.InputConstants;
import dev.sai.privacyfix.compat.ChatCompat;
import dev.sai.privacyfix.compat.KeyCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Anti-trap tools.
 *
 * Force close: hold Esc and press the "force" key (default F, rebindable in
 * Controls) while a server keeps re-opening a sign / book / container /
 * dialog on you. The current screen is closed, every server-initiated
 * screen is refused for {@link PrivacyConfig#forceLockSeconds}, and the pause
 * menu opens once Esc is released so you can hit Disconnect.
 *
 * Portal chat: vanilla closes every screen that is not "allowed in portal"
 * while you stand in a nether portal, and replaces it with the level-loading
 * screen on every dimension switch. Both are what makes a portal trap
 * unplayable. The chat screen is marked allowed, and its text survives the
 * dimension switch.
 */
public final class Panic {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(PrivacyFix.MOD_ID, "privacyfix"));
    public static final KeyMapping FORCE_KEY = new KeyMapping("key.saisprivacyfix.force", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY);

    private static volatile long lockUntil = 0;
    private static boolean chordDown = false;
    private static boolean pausePending = false;
    /** Chat text to restore after a dimension switch while in a portal loop. */
    private static volatile String savedChat = null;

    private Panic() {}

    public static void register() {
        KeyCompat.register(FORCE_KEY);
    }

    /** True while server-initiated screens must be refused. */
    public static boolean locked() {
        return System.currentTimeMillis() < lockUntil;
    }

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        PrivacyConfig c = PrivacyFix.config();
        if (mc.getWindow() == null) return;
        boolean esc = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_ESCAPE);
        InputConstants.Key bound = KeyCompat.boundKey(FORCE_KEY);
        boolean force = bound.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(mc.getWindow(), bound.getValue());

        if (c.enabled && c.forceClose && esc && force) {
            if (!chordDown) activate(mc, c);
            chordDown = true;
        } else {
            chordDown = false;
        }
        if (pausePending && !esc && mc.player != null) {
            pausePending = false;
            mc.setScreenAndShow(new PauseScreen(true));
        }

        // Portal chat: put the chat screen back once the dimension switch is through.
        String text = savedChat;
        if (text != null && mc.player != null) {
            Screen s = ChatCompat.currentScreen();
            if (s == null || s instanceof LevelLoadingScreen) {
                savedChat = null;
                mc.setScreenAndShow(new ChatScreen(text, false));
            }
        }
    }

    private static void activate(Minecraft mc, PrivacyConfig c) {
        lockUntil = System.currentTimeMillis() + Math.max(1, c.forceLockSeconds) * 1000L;
        Screen s = ChatCompat.currentScreen();
        if (s instanceof AbstractContainerScreen && mc.player != null) {
            mc.player.closeContainer();
        } else if (s != null) {
            mc.setScreenAndShow(null);
        }
        pausePending = mc.player != null;
        Audit.log("force close: {} closed, server screens refused for {}s", s == null ? "nothing" : s.getClass().getSimpleName(), c.forceLockSeconds);
        Audit.chat(Audit.prefix().append(Component.literal("force: server screens blocked for " + c.forceLockSeconds
                + "s. Esc -> Disconnect if you want out.").withStyle(ChatFormatting.GOLD)));
    }

    /** True if the screen is one a server can open on you. */
    public static boolean isServerScreen(Screen s) {
        return s instanceof AbstractSignEditScreen || s instanceof BookEditScreen || s instanceof BookViewScreen
                || s instanceof AbstractContainerScreen || s instanceof DialogScreen;
    }

    // ---- portal chat --------------------------------------------------

    /** Called from handleRespawn on the main thread, before the level is swapped. */
    public static void beforeDimensionSwitch(String chatText) {
        if (PrivacyFix.config().enabled && PrivacyFix.config().portalChat) savedChat = chatText;
    }

    public static void resetConnection() {
        savedChat = null;
        pausePending = false;
        lockUntil = 0;
    }
}
