package dev.sai.privacyfix.packets;

import dev.sai.privacyfix.Audit;
import dev.sai.privacyfix.PrivacyFix;
import net.minecraft.network.protocol.Packet;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the node graph against an outbound packet.
 *
 * Execution starts at every WHEN node whose packet matches, then walks the
 * wires depth-first in the order they were connected. A visited set keeps a
 * graph that loops back on itself from hanging the connection, and anything
 * that throws is logged and skipped so a broken graph can never break play.
 */
public final class PacketGraph {
    private PacketGraph() {}

    public static PacketRules.Result run(Packet<?> packet, List<PacketNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return PacketRules.Result.pass(packet);
        PacketCatalog.Entry entry = PacketCatalog.byClass(packet.getClass());
        if (entry == null) return PacketRules.Result.pass(packet);

        Map<String, PacketNode> byId = new HashMap<>();
        for (PacketNode n : nodes) byId.put(n.id, n);

        State state = new State(packet);
        for (PacketNode n : nodes) {
            if (n.kind != PacketNode.Kind.WHEN || !entry.name().equals(n.packet)) continue;
            walk(n, byId, state, new HashSet<>());
            if (state.dropped) break;
        }
        return new PacketRules.Result(state.dropped ? null : state.packet, state.extra);
    }

    private static final class State {
        Packet<?> packet;
        final List<Packet<?>> extra = new ArrayList<>(0);
        boolean dropped;

        State(Packet<?> packet) { this.packet = packet; }
    }

    private static void walk(PacketNode node, Map<String, PacketNode> byId, State state, Set<String> seen) {
        if (state.dropped || !seen.add(node.id)) return;
        try {
            switch (node.kind) {
                case WHEN -> { }
                case IF -> { if (!test(node, state.packet)) return; }
                case LOG -> Audit.log("graph: {} {}", state.packet.getClass().getSimpleName(), PacketRules.describe(state.packet));
                case DROP -> {
                    Audit.log("graph: dropped {}", state.packet.getClass().getSimpleName());
                    state.dropped = true;
                    return;
                }
                case SET -> {
                    Packet<?> modified = PacketRules.withOverrides(state.packet, node.set, false);
                    if (modified != null) state.packet = modified;
                }
                case COPY -> {
                    Packet<?> copy = PacketRules.withOverrides(state.packet, node.set, true);
                    if (copy != null) state.extra.add(copy);
                }
            }
        } catch (Throwable t) {
            PrivacyFix.LOGGER.warn("packet graph node {} ({}) failed, packet left alone: {}", node.id, node.kind, t.toString());
            return;
        }
        for (String id : node.next) {
            PacketNode child = byId.get(id);
            if (child != null) walk(child, byId, state, seen);
        }
    }

    /** Compares one field of the packet against the node's value. */
    private static boolean test(PacketNode node, Packet<?> packet) throws Exception {
        List<Field> fields = PacketRules.fields(packet.getClass());
        if (node.field < 0 || node.field >= fields.size()) return false;
        Field f = fields.get(node.field);
        f.setAccessible(true);
        Object actual = f.get(packet);
        Object expected = PacketRules.parse(f.getType(), node.value);
        return switch (node.compare) {
            case EQUALS -> String.valueOf(actual).equals(String.valueOf(expected));
            case NOT_EQUALS -> !String.valueOf(actual).equals(String.valueOf(expected));
            case GREATER -> compareNumbers(actual, expected) > 0;
            case LESS -> compareNumbers(actual, expected) < 0;
        };
    }

    private static int compareNumbers(Object a, Object b) {
        double x = a instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(a));
        double y = b instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(b));
        return Double.compare(x, y);
    }
}
