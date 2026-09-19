package dev.sai.privacyfix.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/** 1.21.11: fabric-key-binding-api-v1. */
public final class KeyCompat {
    private KeyCompat() {}

    public static void register(KeyMapping mapping) {
        KeyBindingHelper.registerKeyBinding(mapping);
    }

    public static InputConstants.Key boundKey(KeyMapping mapping) {
        return KeyBindingHelper.getBoundKeyOf(mapping);
    }
}
