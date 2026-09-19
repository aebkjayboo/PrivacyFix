package dev.sai.privacyfix.packets;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The outbound packets a rule can target, with human field labels.
 *
 * Class references are resolved at compile time, so they survive the
 * obfuscated 1.21.11 runtime; the labels are ours because reflection would
 * only give intermediary names there. Field order is declaration order
 * (record component order for records), walking superclasses first.
 */
public final class PacketCatalog {
    /** Groups the catalog the way the dropdown lists it. */
    public enum Category {
        ACTIONS("Actions"), BLOCKS("Blocks & items"), MOVEMENT("Movement"), CHAT("Chat & commands"),
        INVENTORY("Inventory"), CONNECTION("Connection & heartbeat"), SETTINGS("Settings & identity"), MISC("Other");

        public final String label;

        Category(String label) { this.label = label; }
    }

    public record Entry(String name, Category category, Class<? extends Packet<?>> type, List<String> fields, String note) {}

    private static final Map<String, Entry> BY_NAME = new LinkedHashMap<>();
    private static final Map<Class<?>, Entry> BY_CLASS = new LinkedHashMap<>();

    private static void add(String name, Category category, Class<? extends Packet<?>> type, String note, String... fields) {
        Entry e = new Entry(name, category, type, List.of(fields), note);
        BY_NAME.put(name, e);
        BY_CLASS.put(type, e);
    }

    static {
        String move = "x, y, z, yRot, xRot, onGround, horizontalCollision, hasPos, hasRot";
        add("ServerboundSwingPacket", Category.ACTIONS, ServerboundSwingPacket.class, "arm swing", "hand");
        add("ServerboundSetCarriedItemPacket", Category.INVENTORY, ServerboundSetCarriedItemPacket.class, "hotbar slot change", "slot");
        add("ServerboundPlayerActionPacket", Category.ACTIONS, ServerboundPlayerActionPacket.class, "dig / drop / swap", "pos", "direction", "action", "sequence");
        add("ServerboundUseItemPacket", Category.ACTIONS, ServerboundUseItemPacket.class, "right click item", "hand", "sequence", "yRot", "xRot");
        add("ServerboundUseItemOnPacket", Category.BLOCKS, ServerboundUseItemOnPacket.class, "right click block", "blockHit", "hand", "sequence");
        add("ServerboundPlayerCommandPacket", Category.ACTIONS, ServerboundPlayerCommandPacket.class, "sneak / sprint / etc", "id", "action", "data");
        add("ServerboundPlayerAbilitiesPacket", Category.ACTIONS, ServerboundPlayerAbilitiesPacket.class, "flying toggle", "isFlying");
        add("ServerboundChatPacket", Category.CHAT, ServerboundChatPacket.class, "chat message (signed: editing may get you kicked on secure servers)", "message", "timeStamp", "salt", "signature", "lastSeenMessages");
        add("ServerboundChatCommandPacket", Category.CHAT, ServerboundChatCommandPacket.class, "unsigned command", "command");
        add("ServerboundChatCommandSignedPacket", Category.CHAT, ServerboundChatCommandSignedPacket.class, "signed command", "command", "timeStamp", "salt", "argumentSignatures", "lastSeenMessages");
        add("ServerboundClientCommandPacket", Category.CONNECTION, ServerboundClientCommandPacket.class, "respawn / stats request", "action");
        add("ServerboundKeepAlivePacket", Category.CONNECTION, ServerboundKeepAlivePacket.class, "keep-alive reply (drop = timeout kick)", "id");
        add("ServerboundPongPacket", Category.CONNECTION, ServerboundPongPacket.class, "ping reply", "id");
        add("ServerboundContainerClosePacket", Category.INVENTORY, ServerboundContainerClosePacket.class, "close inventory/container", "containerId");
        add("ServerboundPlayerInputPacket", Category.MOVEMENT, ServerboundPlayerInputPacket.class, "movement keys state", "input");
        add("ServerboundMovePlayerPacket$Pos", Category.MOVEMENT, ServerboundMovePlayerPacket.Pos.class, "position only", split(move));
        add("ServerboundMovePlayerPacket$PosRot", Category.MOVEMENT, ServerboundMovePlayerPacket.PosRot.class, "position + look", split(move));
        add("ServerboundMovePlayerPacket$Rot", Category.MOVEMENT, ServerboundMovePlayerPacket.Rot.class, "look only", split(move));
        add("ServerboundMovePlayerPacket$StatusOnly", Category.MOVEMENT, ServerboundMovePlayerPacket.StatusOnly.class, "on-ground only", split(move));
        add("ServerboundClientInformationPacket", Category.SETTINGS, ServerboundClientInformationPacket.class, "language / view distance / skin parts", "information");
        add("ServerboundCustomPayloadPacket", Category.SETTINGS, ServerboundCustomPayloadPacket.class, "plugin message (brand, mod channels)", "payload");
        add("ServerboundResourcePackPacket", Category.SETTINGS, ServerboundResourcePackPacket.class, "resource pack status", "id", "action");
        add("ServerboundSignUpdatePacket", Category.BLOCKS, ServerboundSignUpdatePacket.class, "sign text", "pos", "lines", "isFrontText");
        add("ServerboundRenameItemPacket", Category.INVENTORY, ServerboundRenameItemPacket.class, "anvil rename", "name");
        add("ServerboundClientTickEndPacket", Category.CONNECTION, ServerboundClientTickEndPacket.class, "end-of-tick marker (drop = broken movement)");
        add("ServerboundSelectBundleItemPacket", Category.INVENTORY, ServerboundSelectBundleItemPacket.class, "bundle selection", "slotId", "selectedItemIndex");
        add("ServerboundSetCreativeModeSlotPacket", Category.INVENTORY, ServerboundSetCreativeModeSlotPacket.class, "creative slot set", "slotNum", "itemStack");
        add("ServerboundPickItemFromBlockPacket", Category.BLOCKS, ServerboundPickItemFromBlockPacket.class, "pick block", "pos", "includeData");
        add("ServerboundPickItemFromEntityPacket", Category.ACTIONS, ServerboundPickItemFromEntityPacket.class, "pick entity", "id", "includeData");
        add("ServerboundPaddleBoatPacket", Category.MOVEMENT, ServerboundPaddleBoatPacket.class, "boat paddles", "left", "right");
        add("ServerboundMoveVehiclePacket", Category.MOVEMENT, ServerboundMoveVehiclePacket.class, "vehicle position", "position", "yRot", "xRot", "onGround");
        add("ServerboundCommandSuggestionPacket", Category.CHAT, ServerboundCommandSuggestionPacket.class, "tab-complete request (sent per keystroke while typing a /command)", "id", "command");
        add("ServerboundChatSessionUpdatePacket", Category.CHAT, ServerboundChatSessionUpdatePacket.class, "chat signing key upload", "chatSession");
        add("ServerboundChatAckPacket", Category.CHAT, ServerboundChatAckPacket.class, "chat ack", "offset");
        add("ServerboundAcceptTeleportationPacket", Category.MOVEMENT, ServerboundAcceptTeleportationPacket.class, "teleport confirm (drop = rubber-band)", "id");
        add("ServerboundSeenAdvancementsPacket", Category.MISC, ServerboundSeenAdvancementsPacket.class, "advancements tab", "action", "tab");
        add("ServerboundRecipeBookChangeSettingsPacket", Category.INVENTORY, ServerboundRecipeBookChangeSettingsPacket.class, "recipe book state", "bookType", "isOpen", "isFiltering");
        add("ServerboundEditBookPacket", Category.INVENTORY, ServerboundEditBookPacket.class, "book edit", "slot", "pages", "title");
        add("ServerboundPlaceRecipePacket", Category.INVENTORY, ServerboundPlaceRecipePacket.class, "recipe placement", "containerId", "recipe", "useMaxItems");
        add("ServerboundContainerButtonClickPacket", Category.INVENTORY, ServerboundContainerButtonClickPacket.class, "container button", "containerId", "buttonId");
        add("ServerboundContainerSlotStateChangedPacket", Category.INVENTORY, ServerboundContainerSlotStateChangedPacket.class, "crafter slot toggle", "slotId", "containerId", "newState");
        add("ServerboundTeleportToEntityPacket", Category.MOVEMENT, ServerboundTeleportToEntityPacket.class, "spectator teleport", "uuid");
        add("ServerboundPlayerLoadedPacket", Category.CONNECTION, ServerboundPlayerLoadedPacket.class, "player loaded marker");
        add("ServerboundBlockEntityTagQueryPacket", Category.BLOCKS, ServerboundBlockEntityTagQueryPacket.class, "F3+I block query", "transactionId", "pos");
        add("ServerboundEntityTagQueryPacket", Category.MISC, ServerboundEntityTagQueryPacket.class, "F3+I entity query", "transactionId", "entityId");
        add("ServerboundChangeGameModePacket", Category.MISC, ServerboundChangeGameModePacket.class, "F3+F4 game mode switch", "mode");
    }

    private static String[] split(String s) {
        return s.split(",\\s*");
    }

    private PacketCatalog() {}

    /** Every packet name, grouped and in category order, for the dropdown. */
    public static java.util.LinkedHashMap<Category, List<String>> byCategory() {
        java.util.LinkedHashMap<Category, List<String>> out = new java.util.LinkedHashMap<>();
        for (Category c : Category.values()) {
            List<String> names = new ArrayList<>();
            for (Entry e : BY_NAME.values()) if (e.category() == c) names.add(e.name());
            if (!names.isEmpty()) out.put(c, names);
        }
        return out;
    }

    public static List<String> names() {
        return new ArrayList<>(BY_NAME.keySet());
    }

    public static Entry byName(String name) {
        return BY_NAME.get(name);
    }

    public static Entry byClass(Class<?> type) {
        return BY_CLASS.get(type);
    }

    public static List<String> fieldLabels(String name) {
        Entry e = BY_NAME.get(name);
        return e == null ? Collections.emptyList() : e.fields();
    }
}
