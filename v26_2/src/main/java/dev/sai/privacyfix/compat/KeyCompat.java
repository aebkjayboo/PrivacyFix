package dev.sai.privacyfix.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/** 26.2: fabric-key-mapping-api-v1 (renamed module). */
public final class KeyCompat {
    private KeyCompat() {}

    public static void register(KeyMapping mapping) {
        KeyMappingHelper.registerKeyMapping(mapping);
    }

    public static InputConstants.Key boundKey(KeyMapping mapping) {
        return KeyMappingHelper.getBoundKeyOf(mapping);
    }
}
