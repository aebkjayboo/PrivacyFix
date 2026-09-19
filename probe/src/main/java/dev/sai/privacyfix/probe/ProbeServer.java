package dev.sai.privacyfix.probe;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * TEST RIG ONLY. Server-side mod that does what a mod-detecting server does:
 * logs the brand / channel list / client info every client sends, and
 * offers /probe (sign editor) and /probeanvil (anvil) translation-key probes.
 * Install on a local Fabric server next to Fabric API. Never ship this.
 */
public final class ProbeServer implements DedicatedServerModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("PROBE");

    // One key that only exists if PrivacyFix is installed, one that only
    // exists in a hypothetical other mod, one vanilla key for comparison.
    private static final String[] KEYS = {
            "key.saisprivacyfix.probe_test",
            "key.somemod.does_not_exist",
            "menu.singleplayer"
    };

    /** Like a real detection server: probe every player automatically a few seconds after they join. */
    private record Pending(UUID player, int atTick, boolean anvil) {}
    private static final List<Pending> PENDING = new ArrayList<>();
    private static int tick = 0;
    /** Players being sign-spammed: uuid -> tick to stop at. */
    private static final java.util.Map<UUID, Integer> SIGN_TRAPS = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Integer> BOOK_TRAPS = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Integer> INTERVAL = new java.util.HashMap<>();
    /** Players in a portal trap: enclose them on every arrival. */
    private static final Set<UUID> PORTAL_TRAPS = new HashSet<>();
    private static final java.util.Map<UUID, Object> LAST_DIM = new java.util.HashMap<>();

    @Override
    public void onInitializeServer() {
        LOG.info("probe server mod active");
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            UUID id = handler.getPlayer().getUUID();
            synchronized (PENDING) {
                PENDING.add(new Pending(id, tick + 80, false));
                PENDING.add(new Pending(id, tick + 160, true));
            }
            LOG.info("{} joined; sign probe in 4s, anvil probe in 8s", handler.getPlayer().getName().getString());
            // What Fabric API on the server knows about this client: the channels
            // it announced via minecraft:register (empty for a vanilla-looking client).
            LOG.info("CHANNELS REGISTERED BY CLIENT = {}", ServerPlayNetworking.getSendable(handler.getPlayer()));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            List<Pending> due = new ArrayList<>();
            synchronized (PENDING) {
                PENDING.removeIf(pd -> { if (pd.atTick() <= tick) { due.add(pd); return true; } return false; });
            }
            for (Pending pd : due) {
                ServerPlayer p = server.getPlayerList().getPlayer(pd.player());
                if (p == null) continue;
                if (pd.anvil()) anvilProbe(p); else signProbe(p);
            }
            // portal trap: whenever a trapped player changes dimension, encase the arrival portal
            for (UUID id : PORTAL_TRAPS) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                if (tick % 100 == 0) {
                    BlockPos at = p.blockPosition();
                    LOG.info("portal trap: {} at {} feet={} head={} dim={}", p.getName().getString(), at,
                            p.level().getBlockState(at).getBlock(), p.level().getBlockState(at.above()).getBlock(), p.level().dimension());
                }
                Object dim = p.level().dimension();
                if (!dim.equals(LAST_DIM.put(id, dim)) && p.tickCount > 0) {
                    enclose(p);
                    LOG.info("portal trap: {} arrived in {}, enclosed", p.getName().getString(), dim);
                }
            }
            // sign/book traps: re-open every N ticks (default 10, /trapbook 1 = every tick)
            SIGN_TRAPS.entrySet().removeIf(e -> e.getValue() < tick);
            for (UUID id : SIGN_TRAPS.keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null && tick % INTERVAL.getOrDefault(id, 10) == 0) signProbe(p);
            }
            BOOK_TRAPS.entrySet().removeIf(e -> e.getValue() < tick);
            for (UUID id : BOOK_TRAPS.keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null && tick % INTERVAL.getOrDefault(id, 10) == 0) bookTrap(p);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            dispatcher.register(Commands.literal("trapsign").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                SIGN_TRAPS.put(p.getUUID(), tick + 20 * 60);
                LOG.info("sign trap started on {} for 60s", p.getName().getString());
                return 1;
            }));
            dispatcher.register(Commands.literal("trapbook")
                    .executes(ctx -> bookTrapCmd(ctx.getSource().getPlayerOrException(), 10))
                    .then(Commands.argument("everyTicks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 200))
                            .executes(ctx -> bookTrapCmd(ctx.getSource().getPlayerOrException(),
                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "everyTicks")))));
            dispatcher.register(Commands.literal("trapsign")
                    .then(Commands.argument("everyTicks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 200))
                            .executes(ctx -> {
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                INTERVAL.put(p.getUUID(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "everyTicks"));
                                SIGN_TRAPS.put(p.getUUID(), tick + 20 * 60);
                                return 1;
                            })));
            dispatcher.register(Commands.literal("trapportal").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                ServerLevel level = p.level();
                BlockPos b = p.blockPosition();
                BlockState obs = Blocks.OBSIDIAN.defaultBlockState();
                BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
                // frame + side walls first, portal blocks last (the portal shape check
                // removes portal blocks whose frame is not complete yet)
                for (int x = -1; x <= 2; x++) for (int y = -1; y <= 3; y++) {
                    boolean frame = x == -1 || x == 2 || y == -1 || y == 3;
                    if (frame) level.setBlock(b.offset(x, y, 0), obs, 3);
                    level.setBlock(b.offset(x, y, -1), obs, 3);
                    level.setBlock(b.offset(x, y, 1), obs, 3);
                }
                for (int x = 0; x <= 1; x++) for (int y = 0; y <= 2; y++) level.setBlock(b.offset(x, y, 0), portal, 3);
                PORTAL_TRAPS.add(p.getUUID());
                LAST_DIM.put(p.getUUID(), level.dimension());
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.teleportTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
                LOG.info("portal trap built around {}", p.getName().getString());
                return 1;
            }));
            dispatcher.register(Commands.literal("untrap").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                SIGN_TRAPS.remove(p.getUUID());
                BOOK_TRAPS.remove(p.getUUID());
                PORTAL_TRAPS.remove(p.getUUID());
                return 1;
            }));
            // Fake players that exist only as completions, never in the tab
            // list: exactly the shape of a network's cross-server chat.
            dispatcher.register(Commands.literal("msg")
                    .then(Commands.argument("target", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .suggests((ctx, b) -> {
                                for (String n : new String[]{"GhostPlayerOne", "FarAwayFriend", "LobbyTwoGuy"}) b.suggest(n);
                                return b.buildFuture();
                            })
                            .executes(ctx -> 1)));
            dispatcher.register(Commands.literal("addr").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                p.sendSystemMessage(Component.literal("Welcome to localhost:25599 - store at localhost for ranks"));
                return 1;
            }));
            dispatcher.register(Commands.literal("saychat").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                // server-formatted chat, the way a network sends it: a system
                // message with the name baked into the text
                p.sendSystemMessage(Component.literal("[VIP] GhostPlayerOne » hello from far away"));
                p.sendSystemMessage(Component.literal("UnknownDude » nobody told the client who I am"));
                return 1;
            }));
            dispatcher.register(Commands.literal("probe").executes(ctx -> signProbe(ctx.getSource().getPlayerOrException())));
            dispatcher.register(Commands.literal("probeanvil").executes(ctx -> anvilProbe(ctx.getSource().getPlayerOrException())));
        });
    }

    private static int bookTrapCmd(ServerPlayer p, int every) {
        INTERVAL.put(p.getUUID(), every);
        BOOK_TRAPS.put(p.getUUID(), tick + 20 * 60);
        LOG.info("book trap started on {} every {} tick(s) for 60s", p.getName().getString(), every);
        return 1;
    }

    /** Put a written book in the main hand and force it open, like a griefing server. */
    private static void bookTrap(ServerPlayer p) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new net.minecraft.world.item.component.WrittenBookContent(
                net.minecraft.server.network.Filterable.passThrough("trap"), "server", 0,
                List.of(net.minecraft.server.network.Filterable.passThrough(Component.literal("you are stuck reading this"))), true));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, book);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundOpenBookPacket(net.minecraft.world.InteractionHand.MAIN_HAND));
    }

    private static void enclose(ServerPlayer p) {
        ServerLevel level = p.level();
        // put the player inside the nearest portal block so the loop continues
        BlockPos here = p.blockPosition();
        BlockPos best = null;
        for (int x = -4; x <= 4; x++) for (int y = -2; y <= 3; y++) for (int z = -4; z <= 4; z++) {
            BlockPos q = here.offset(x, y, z);
            if (level.getBlockState(q).is(Blocks.NETHER_PORTAL) && level.getBlockState(q.above()).is(Blocks.NETHER_PORTAL)
                    && (best == null || q.distSqr(here) < best.distSqr(here))) best = q;
        }
        if (best != null) p.teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
        BlockPos b = best != null ? best : here;
        BlockState obs = Blocks.OBSIDIAN.defaultBlockState();
        for (int x = -2; x <= 2; x++) for (int y = -1; y <= 3; y++) for (int z = -2; z <= 2; z++) {
            BlockPos pos = b.offset(x, y, z);
            if (level.getBlockState(pos).isAir()) level.setBlock(pos, obs, 3);
        }
    }

    private static int signProbe(ServerPlayer p) {
        {
            {
                ServerLevel level = p.level();
                BlockPos pos = p.blockPosition().above(2);
                level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState(), 3);
                if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return 0;
                SignText text = sign.getFrontText();
                for (int i = 0; i < KEYS.length; i++) text = text.setMessage(i, Component.translatable(KEYS[i]));
                sign.setText(text, true);
                sign.setAllowedPlayerEditor(p.getUUID());
                // Push the sign text to the client BEFORE opening the editor, like
                // Paper's player.openSign() does; otherwise the editor opens empty.
                p.connection.send(new ClientboundBlockUpdatePacket(pos, level.getBlockState(pos)));
                p.connection.send(sign.getUpdatePacket());
                p.openTextEdit(sign, true);
                LOG.info("sign probe sent to {} with keys {}", p.getName().getString(), String.join(", ", KEYS));
                return 1;
            }
        }
    }

    private static int anvilProbe(ServerPlayer p) {
        {
            {
                ServerLevel level = p.level();
                BlockPos pos = p.blockPosition().above(2);
                level.setBlock(pos, Blocks.ANVIL.defaultBlockState(), 3);
                ItemStack bait = new ItemStack(Items.IRON_SWORD);
                bait.set(DataComponents.CUSTOM_NAME, Component.translatable(KEYS[0]));
                p.openMenu(new MenuProvider() {
                    @Override public Component getDisplayName() { return Component.literal("probe"); }
                    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
                        return new AnvilMenu(id, inv, ContainerLevelAccess.create(level, pos));
                    }
                });
                // Put the bait straight into the input slot: the client pre-fills the
                // name box from it and sends ServerboundRenameItemPacket on its own.
                if (p.containerMenu instanceof AnvilMenu menu) {
                    menu.getSlot(0).set(bait);
                    menu.broadcastChanges();
                }
                LOG.info("anvil probe sent to {} with item named {}", p.getName().getString(), KEYS[0]);
                return 1;
            }
        }
    }
}
