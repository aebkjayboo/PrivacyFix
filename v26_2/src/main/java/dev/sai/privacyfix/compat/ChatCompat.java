package dev.sai.privacyfix.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** 26.2: Gui no longer exposes getChat(); LocalPlayer.sendSystemMessage routes to it. Screen lives on Gui. Render thread only. */
public final class ChatCompat {
    private ChatCompat() {}

    public static void send(Component msg) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(msg);
    }

    public static Screen currentScreen() {
        return Minecraft.getInstance().gui.screen();
    }
}
