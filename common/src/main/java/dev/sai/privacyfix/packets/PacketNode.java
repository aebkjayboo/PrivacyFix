package dev.sai.privacyfix.packets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One box on the packet graph. A rule is a chain of nodes wired together:
 * a WHEN node matches an outbound packet, and whatever hangs off its output
 * runs in order.
 *
 * Stored in the config as plain JSON, so a graph can be hand-edited or
 * shared without the screen.
 */
public final class PacketNode {
    public enum Kind {
        /** Entry point: matches one packet type. */
        WHEN,
        /** Passes through only when a field compares true. */
        IF,
        /** Stops the packet leaving the client. */
        DROP,
        /** Writes a value into a field before the packet is sent. */
        SET,
        /** Sends one extra copy of the packet, with any SET values applied. */
        COPY,
        /** Writes the packet to the audit log. */
        LOG
    }

    public enum Compare { EQUALS, NOT_EQUALS, GREATER, LESS }

    /** Stable id, referenced by {@link #next}. */
    public String id = "";
    public Kind kind = Kind.WHEN;
    /** Position on the canvas, in graph units. */
    public int x = 0, y = 0;

    /** WHEN: the catalog packet name this graph reacts to. */
    public String packet = "ServerboundSwingPacket";

    /** IF: field index, comparison and the value to compare against. */
    public int field = 0;
    public Compare compare = Compare.EQUALS;
    public String value = "";

    /** SET and COPY: field index -> value text. */
    public Map<String, String> set = new LinkedHashMap<>();

    /** Ids of the nodes wired to this node's output, run in order. */
    public List<String> next = new ArrayList<>();

    public PacketNode() {}

    public PacketNode(String id, Kind kind, int x, int y) {
        this.id = id;
        this.kind = kind;
        this.x = x;
        this.y = y;
    }

    public PacketNode copy() {
        PacketNode n = new PacketNode(id, kind, x, y);
        n.packet = packet;
        n.field = field;
        n.compare = compare;
        n.value = value;
        n.set = new LinkedHashMap<>(set);
        n.next = new ArrayList<>(next);
        return n;
    }

    /** Short label drawn in the node's title bar. */
    public String title() {
        return switch (kind) {
            case WHEN -> "when";
            case IF -> "if";
            case DROP -> "drop";
            case SET -> "set";
            case COPY -> "copy";
            case LOG -> "log";
        };
    }

    /** Whether this kind can have anything wired after it. */
    public boolean hasOutput() {
        return kind != Kind.DROP;
    }
}
