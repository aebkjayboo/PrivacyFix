package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.PacketFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft.getCurrentServer() is null until the play phase, but the brand,
 * client info, channel list and (often) the resource pack prompt all happen
 * during login/configuration. Every outbound connection (server list,
 * direct connect, quick play, transfers) starts here, so remember the
 * address at this point.
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void privacyfix$rememberTarget(Screen parent, Minecraft mc, ServerAddress address, ServerData serverData,
                                                  boolean quickPlay, TransferState transferState, CallbackInfo ci) {
        String typed = serverData != null && serverData.ip != null && !serverData.ip.isBlank() ? serverData.ip
                : address.getHost() + ":" + address.getPort();
        PacketFilter.setConnecting(typed);
    }
}
