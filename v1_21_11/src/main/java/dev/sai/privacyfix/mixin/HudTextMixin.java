package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.names.Names;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 1.21.11: titles and the action bar arrive as Gui setters. */
@Mixin(Gui.class)
public abstract class HudTextMixin {
    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
    private Component privacyfix$title(Component title) {
        return Names.sanitize(title);
    }

    @ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true)
    private Component privacyfix$subtitle(Component subtitle) {
        return Names.sanitize(subtitle);
    }

    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true)
    private Component privacyfix$actionBar(Component message) {
        return Names.sanitize(message);
    }
}
