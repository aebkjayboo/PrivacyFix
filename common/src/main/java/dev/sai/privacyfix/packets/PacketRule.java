package dev.sai.privacyfix.packets;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One user-defined rule for an outbound packet type, stored in the config as JSON.
 *
 * <pre>
 *   packet   = catalog name, e.g. "ServerboundSwingPacket"
 *   action   = PASS | DROP | LOG | MODIFY   (what happens to the packet itself)
 *   set      = field index -> value text    (used by MODIFY and by the copy)
 *   thenCopy = also send one extra copy of the packet with `set` applied
 * </pre>
 *
 * Fields are addressed by index, not name: on 1.21.11 field names are
 * obfuscated at runtime, indices are stable across versions.
 */
public final class PacketRule {
    public enum Action { PASS, DROP, LOG, MODIFY }

    public boolean enabled = true;
    public String packet = "ServerboundSwingPacket";
    public Action action = Action.LOG;
    public Map<String, String> set = new LinkedHashMap<>();
    public boolean thenCopy = false;

    public PacketRule() {}

    public PacketRule copy() {
        PacketRule r = new PacketRule();
        r.enabled = enabled;
        r.packet = packet;
        r.action = action;
        r.set = new LinkedHashMap<>(set);
        r.thenCopy = thenCopy;
        return r;
    }

    public boolean touchesFields() {
        return action == Action.MODIFY || thenCopy;
    }
}
