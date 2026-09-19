package dev.sai.privacyfix.packets;

import dev.sai.privacyfix.Audit;
import dev.sai.privacyfix.PrivacyFix;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies the user's {@link PacketRule}s to an outbound packet.
 *
 * Records are rebuilt through their canonical constructor with the
 * overridden components; plain packet classes have their (final) fields
 * written in place via reflection. Every failure is logged and the packet
 * is sent unmodified: a bad rule must never take the connection down.
 */
public final class PacketRules {
    /** {@code packet == null} means drop. {@code extra} are additional packets to send after it. */
    public record Result(Packet<?> packet, List<Packet<?>> extra) {
        public static Result pass(Packet<?> p) { return new Result(p, Collections.emptyList()); }
    }

    private PacketRules() {}

    public static Result apply(Packet<?> packet, List<PacketRule> rules) {
        if (rules == null || rules.isEmpty()) return Result.pass(packet);
        PacketCatalog.Entry entry = PacketCatalog.byClass(packet.getClass());
        if (entry == null) return Result.pass(packet);

        Packet<?> current = packet;
        List<Packet<?>> extra = new ArrayList<>(0);
        for (PacketRule rule : rules) {
            if (!rule.enabled || !entry.name().equals(rule.packet)) continue;
            try {
                if (rule.thenCopy) {
                    Packet<?> copy = withOverrides(current, rule.set, true);
                    if (copy != null) extra.add(copy);
                }
                switch (rule.action) {
                    case PASS -> { }
                    case LOG -> Audit.log("rule: {} sent: {}", entry.name(), describe(current));
                    case DROP -> {
                        Audit.log("rule: dropped {}", entry.name());
                        return new Result(null, extra);
                    }
                    case MODIFY -> {
                        Packet<?> modified = withOverrides(current, rule.set, false);
                        if (modified != null) current = modified;
                    }
                }
            } catch (Throwable t) {
                PrivacyFix.LOGGER.warn("packet rule on {} failed, packet sent unmodified: {}", entry.name(), t.toString());
            }
        }
        return new Result(current, extra);
    }

    // ---- reflection ---------------------------------------------------

    /** Instance fields in declaration order, superclasses first (matches the catalog labels). */
    public static List<Field> fields(Class<?> type) {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class && c != Record.class; c = c.getSuperclass()) chain.add(0, c);
        List<Field> out = new ArrayList<>();
        for (Class<?> c : chain) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                out.add(f);
            }
        }
        return out;
    }

    /** True if a value of this type can be typed in by the user. */
    public static boolean editable(Class<?> t) {
        return t.isPrimitive() || t == String.class || t.isEnum() || t == UUID.class || t == Identifier.class
                || t == Component.class || Number.class.isAssignableFrom(t) || t == Boolean.class || t == Character.class;
    }

    public static String typeLabel(Class<?> t) {
        if (t.isEnum()) {
            StringBuilder sb = new StringBuilder("enum{");
            Object[] cs = t.getEnumConstants();
            for (int i = 0; i < cs.length && i < 8; i++) sb.append(i > 0 ? "," : "").append(((Enum<?>) cs[i]).name());
            if (cs.length > 8) sb.append(",...");
            return sb.append('}').toString();
        }
        return t.getSimpleName();
    }

    /** Parse user text into a value of the field's type. */
    public static Object parse(Class<?> t, String text) {
        String s = text.trim();
        if (t == int.class || t == Integer.class) return Integer.parseInt(s);
        if (t == long.class || t == Long.class) return Long.parseLong(s);
        if (t == short.class || t == Short.class) return Short.parseShort(s);
        if (t == byte.class || t == Byte.class) return Byte.parseByte(s);
        if (t == float.class || t == Float.class) return Float.parseFloat(s);
        if (t == double.class || t == Double.class) return Double.parseDouble(s);
        if (t == boolean.class || t == Boolean.class) return Boolean.parseBoolean(s);
        if (t == char.class || t == Character.class) return s.charAt(0);
        if (t == String.class) return text;
        if (t == UUID.class) return UUID.fromString(s);
        if (t == Identifier.class) return Identifier.parse(s);
        if (t == Component.class) return Component.literal(text);
        if (t.isEnum()) {
            Object[] cs = t.getEnumConstants();
            try {
                int i = Integer.parseInt(s);
                if (i >= 0 && i < cs.length) return cs[i];
            } catch (NumberFormatException ignored) { }
            for (Object c : cs) if (((Enum<?>) c).name().equalsIgnoreCase(s)) return c;
            throw new IllegalArgumentException("no enum value '" + s + "' (use a name or an index 0.." + (cs.length - 1) + ")");
        }
        throw new IllegalArgumentException("cannot edit values of type " + t.getSimpleName());
    }

    /**
     * Returns the packet with overrides applied. Records give a new instance;
     * plain classes are mutated in place (so {@code copy} is refused for them,
     * there is no way to clone an arbitrary packet).
     */
    public static Packet<?> withOverrides(Packet<?> packet, Map<String, String> set, boolean copy) throws Exception {
        if (set == null || set.isEmpty()) return copy ? rebuild(packet, Collections.emptyMap()) : packet;
        Class<?> type = packet.getClass();
        if (type.isRecord()) return rebuild(packet, set);
        if (copy) return rebuildPlain(packet, set);
        List<Field> fields = fields(type);
        for (Map.Entry<String, String> e : set.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            int idx = Integer.parseInt(e.getKey());
            if (idx < 0 || idx >= fields.size()) continue;
            Field f = fields.get(idx);
            f.setAccessible(true);
            f.set(packet, parse(f.getType(), e.getValue()));
        }
        return packet;
    }

    /**
     * Copies a packet that is not a record. Mutating the original in place is
     * no good here, because the original still has to be sent as it was, so a
     * fresh instance is built through the constructor whose parameters line up
     * with the packet's fields (which is how these classes are written).
     */
    private static Packet<?> rebuildPlain(Packet<?> packet, Map<String, String> set) throws Exception {
        Class<?> type = packet.getClass();
        List<Field> fields = fields(type);
        Class<?>[] types = new Class<?>[fields.size()];
        Object[] values = new Object[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            f.setAccessible(true);
            types[i] = f.getType();
            values[i] = f.get(packet);
            String text = set == null ? null : set.get(String.valueOf(i));
            if (text != null && !text.isEmpty()) values[i] = parse(types[i], text);
        }
        Constructor<?> ctor = null;
        for (Constructor<?> candidate : type.getDeclaredConstructors()) {
            if (java.util.Arrays.equals(candidate.getParameterTypes(), types)) {
                ctor = candidate;
                break;
            }
        }
        if (ctor == null) {
            Audit.log("cannot copy {}: no constructor matching its fields", type.getSimpleName());
            return null;
        }
        ctor.setAccessible(true);
        return (Packet<?>) ctor.newInstance(values);
    }

    private static Packet<?> rebuild(Packet<?> packet, Map<String, String> set) throws Exception {
        Class<?> type = packet.getClass();
        if (!type.isRecord()) return null;
        RecordComponent[] comps = type.getRecordComponents();
        Class<?>[] types = new Class<?>[comps.length];
        Object[] values = new Object[comps.length];
        for (int i = 0; i < comps.length; i++) {
            types[i] = comps[i].getType();
            comps[i].getAccessor().setAccessible(true);
            values[i] = comps[i].getAccessor().invoke(packet);
            String text = set.get(String.valueOf(i));
            if (text != null && !text.isEmpty()) values[i] = parse(types[i], text);
        }
        Constructor<?> ctor = type.getDeclaredConstructor(types);
        ctor.setAccessible(true);
        return (Packet<?>) ctor.newInstance(values);
    }

    public static String describe(Packet<?> packet) {
        StringBuilder sb = new StringBuilder();
        List<Field> fields = fields(packet.getClass());
        PacketCatalog.Entry entry = PacketCatalog.byClass(packet.getClass());
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            f.setAccessible(true);
            Object v;
            try { v = f.get(packet); } catch (Exception e) { v = "?"; }
            String label = entry != null && i < entry.fields().size() ? entry.fields().get(i) : "#" + i;
            if (i > 0) sb.append(", ");
            sb.append(label).append('=').append(v instanceof Object[] arr ? java.util.Arrays.toString(arr) : v);
        }
        return sb.toString();
    }
}
