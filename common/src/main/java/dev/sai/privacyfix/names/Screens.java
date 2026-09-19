package dev.sai.privacyfix.names;

import dev.sai.privacyfix.PrivacyConfig;
import dev.sai.privacyfix.PrivacyFix;
import net.minecraft.network.chat.Component;

/**
 * Masking for text the game draws, wherever it is drawn.
 *
 * Hooking the two text-drawing calls covers every screen at once: the pause
 * menu, the social interactions list, the server list, F3, a mod's own HUD.
 * That is the only way to be sure a name or an address is not sitting in
 * some corner of a screenshot, which is the whole point of hiding them.
 *
 * Both entry points bail immediately when nothing is being hidden, and the
 * work after that is a substring check against a handful of strings.
 */
public final class Screens {
    private Screens() {}

    /**
     * Set while a text field is drawing itself. Masking there would show
     * "annon.gg" in a box the player is trying to type a real address into,
     * so the field is left alone; what it holds was never hidden anyway,
     * it is the player's own input.
     */
    private static boolean inTextField;

    public static void enterTextField() { inTextField = true; }

    public static void leaveTextField() { inTextField = false; }

    private static boolean active() {
        if (inTextField) return false;
        PrivacyConfig c = PrivacyFix.config();
        return c.enabled && (c.hideOwnName || c.hideServerAddress || c.hideOtherNames);
    }

    /**
     * One pass over the text. {@link Names#sanitize(String)} already carries
     * your own name, everyone else's and every known server address, so
     * masking each of them separately would walk the string three times and
     * rebuild the same list three times over.
     */
    public static String mask(String text) {
        if (!active() || text == null || text.isEmpty()) return text;
        return Names.sanitize(text);
    }

    public static Component mask(Component text) {
        if (!active() || text == null) return text;
        String flat = text.getString();
        String masked = mask(flat);
        if (masked.equals(flat)) return text;
        // rebuilding from the flattened string loses per-part styling, but
        // only for the lines that actually contain something to hide
        return Component.literal(masked).setStyle(text.getStyle());
    }
}
