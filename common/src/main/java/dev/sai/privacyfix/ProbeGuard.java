package dev.sai.privacyfix;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Neutralises translation-key probes.
 *
 * A server cannot ask a Fabric client for its mod list, but it can hand the
 * client a translatable component and get the translated text back through
 * any UI that echoes text to the server: the sign editor and the anvil name
 * box. If the key belongs to a mod, the text that comes back proves the mod
 * is installed. {@link #sanitize} rebuilds a component the way an unmodified
 * client would render it: vanilla keys are translated (from vanilla's own
 * en_us.json, never from mod resources), every other key comes back as the
 * raw key (or its declared fallback), which is what vanilla does for a key
 * it does not know.
 */
public final class ProbeGuard {
    private static volatile Map<String, String> vanilla;
    // Same shape vanilla's Language.decomposeTemplate accepts: %s, %1$s, %%.
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([sd%])");

    private ProbeGuard() {}

    /** Vanilla's own en_us.json, read straight out of the game jar's resources. */
    private static Map<String, String> vanilla() {
        Map<String, String> v = vanilla;
        if (v == null) {
            synchronized (ProbeGuard.class) {
                v = vanilla;
                if (v == null) {
                    Map<String, String> m = new HashMap<>();
                    try (InputStream in = Language.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
                        if (in != null) Language.loadFromJson(in, m::put);
                    } catch (Exception e) {
                        PrivacyFix.LOGGER.error("Could not read vanilla en_us.json; probe guard will treat every key as non-vanilla", e);
                    }
                    PrivacyFix.LOGGER.info("Probe guard loaded {} vanilla translation keys", m.size());
                    vanilla = v = m;
                }
            }
        }
        return v;
    }

    public static boolean isVanillaKey(String key) {
        return vanilla().containsKey(key);
    }

    /**
     * @param found every non-vanilla key encountered is appended here so the
     *              caller can report the probe.
     */
    public static Component sanitize(Component c, List<String> found) {
        ComponentContents contents = c.getContents();
        MutableComponent out;
        if (contents instanceof TranslatableContents t) {
            out = Component.literal(renderTranslatable(t, found));
        } else if (contents instanceof KeybindContents k && !isVanillaKey(k.getName())) {
            // {"keybind":"key.somemod.x"} renders as the bound key ("F4") only if a
            // mod registered that keybind; vanilla shows the raw id. Vanilla ids
            // (key.attack, ...) are left to resolve normally, exactly like vanilla.
            found.add("keybind:" + k.getName());
            out = Component.literal(k.getName());
        } else {
            out = MutableComponent.create(contents);
        }
        out.setStyle(c.getStyle());
        for (Component sibling : c.getSiblings()) {
            out.append(sanitize(sibling, found));
        }
        return out;
    }

    /** Human-readable structure of a component, for the audit log: what the server actually sent. */
    public static String describe(Component c) {
        StringBuilder sb = new StringBuilder();
        describe(c, sb);
        return sb.toString();
    }

    private static void describe(Component c, StringBuilder sb) {
        ComponentContents contents = c.getContents();
        if (contents instanceof TranslatableContents t) {
            sb.append("translate(").append(t.getKey());
            if (t.getFallback() != null) sb.append(" fallback=\"").append(t.getFallback()).append('"');
            if (t.getArgs().length > 0) {
                sb.append(" args=[");
                for (Object a : t.getArgs()) {
                    if (a instanceof Component ac) describe(ac, sb); else sb.append(a);
                    sb.append(',');
                }
                sb.append(']');
            }
            sb.append(')');
        } else if (contents instanceof KeybindContents k) {
            sb.append("keybind(").append(k.getName()).append(')');
        } else {
            String plain = MutableComponent.create(contents).getString();
            sb.append(plain.isEmpty() ? contents.getClass().getSimpleName() : '"' + plain + '"');
        }
        for (Component sibling : c.getSiblings()) {
            sb.append(" + ");
            describe(sibling, sb);
        }
    }

    private static String renderTranslatable(TranslatableContents t, List<String> found) {
        String key = t.getKey();
        String template;
        if (isVanillaKey(key)) {
            PrivacyConfig cfg = PrivacyFix.config();
            // If we told the server we are en_us, answer in en_us; otherwise
            // answer in the real language like any vanilla client would.
            template = cfg.normalizeLanguage ? vanilla().get(key) : Language.getInstance().getOrDefault(key);
        } else {
            found.add(key);
            template = t.getFallback() != null ? t.getFallback() : key;
        }
        return format(template, t.getArgs(), found);
    }

    private static String format(String template, Object[] args, List<String> found) {
        if (args == null || args.length == 0 || template.indexOf('%') < 0) return template;
        StringBuilder sb = new StringBuilder();
        Matcher m = FORMAT.matcher(template);
        int last = 0, next = 0;
        while (m.find()) {
            sb.append(template, last, m.start());
            last = m.end();
            if ("%".equals(m.group(2))) {
                sb.append('%');
                continue;
            }
            int idx = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
            if (idx >= 0 && idx < args.length) {
                Object a = args[idx];
                sb.append(a instanceof Component ac ? sanitize(ac, found).getString() : String.valueOf(a));
            } else {
                sb.append(m.group());
            }
        }
        sb.append(template, last, template.length());
        return sb.toString();
    }
}
