package dev.sai.privacyfix.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.sai.privacyfix.PrivacyFix;
import dev.sai.privacyfix.gui.PrivacySettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds a "Privacy" button to Options, in the same grid as Skin Customization
 * / Video Settings / etc. Added as one more grid cell so it obeys the same
 * two-per-row, centre-if-alone layout as the rest and follows the screen
 * when it is resized.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @WrapOperation(
            method = "init",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToContents(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private LayoutElement privacyfix$addButton(HeaderAndFooterLayout layout, LayoutElement grid, Operation<LayoutElement> original,
                                               @Local GridLayout.RowHelper rows) {
        rows.addChild(Button.builder(Component.translatable("saisprivacyfix.options.button"),
                        b -> Minecraft.getInstance().setScreenAndShow(new PrivacySettingsScreen(this, PrivacyFix.config())))
                .width(150).build());
        return original.call(layout, grid);
    }
}
