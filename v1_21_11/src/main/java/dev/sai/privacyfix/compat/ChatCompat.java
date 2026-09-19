package dev.sai.privacyfix.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.21.11: ChatComponent.addMessage(Component); Minecraft.screen is a public field. Render thread only. */
public final class ChatCompat {
    private ChatCompat() {}

    public static void send(Component msg) {
        Minecraft.getInstance().gui.getChat().addMessage(msg);
    }

    public static Screen currentScreen() {
        return Minecraft.getInstance().screen;
    }
}
