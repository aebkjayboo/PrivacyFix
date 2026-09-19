package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.names.Screens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every string the game draws passes through here, so a hidden name or
 * address cannot survive in some screen we did not think of.
 */
@Mixin(GuiGraphics.class)
public abstract class TextDrawMixin {
    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
            at = @At("HEAD"), argsOnly = true)
    private String privacyfix$maskString(String text) {
        return Screens.mask(text);
    }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
            at = @At("HEAD"), argsOnly = true)
    private Component privacyfix$maskComponent(Component text) {
        return Screens.mask(text);
    }
}
