package dev.sai.privacyfix.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The pack prompt is a package-private inner class; read its fields through an accessor. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl$PackConfirmScreen")
public interface PackConfirmScreenAccessor {
    @Accessor("requests")
    List<?> privacyfix$requests();

    @Accessor("parentScreen")
    Screen privacyfix$parentScreen();
}
