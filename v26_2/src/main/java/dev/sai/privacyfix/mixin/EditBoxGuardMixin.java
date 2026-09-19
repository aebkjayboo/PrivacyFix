package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.names.Screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Text the player is typing is left unmasked: a server address box has to
 * show the address being edited, and the contents are the player's own
 * input rather than something the game is disclosing.
 */
@Mixin(EditBox.class)
public abstract class EditBoxGuardMixin {
    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
    private void privacyfix$enter(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screens.enterTextField();
    }

    @Inject(method = "extractWidgetRenderState", at = @At("RETURN"))
    private void privacyfix$leave(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screens.leaveTextField();
    }
}
