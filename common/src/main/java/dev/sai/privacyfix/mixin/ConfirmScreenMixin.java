package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.PackSkip;
import dev.sai.privacyfix.PrivacyFix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the X button to the server resource pack prompt. The prompt is
 * ClientCommonPacketListenerImpl$PackConfirmScreen, a package-private
 * subclass of ConfirmScreen that does not override addButtons, so the hook
 * lives here and checks the concrete class by name.
 */
@Mixin(ConfirmScreen.class)
public abstract class ConfirmScreenMixin {
    @Inject(method = "addButtons", at = @At("TAIL"))
    private void privacyfix$addSkipButton(LinearLayout layout, CallbackInfo ci) {
        if (!(((Object) this) instanceof PackConfirmScreenAccessor screen)) return;
        if (!PrivacyFix.active(PrivacyFix.config().packSkip)) return;
        layout.addChild(Button.builder(Component.literal("X"), b -> {
            List<PackSkip.Pending> reqs = new ArrayList<>();
            for (Object o : screen.privacyfix$requests()) {
                PendingRequestAccessor r = (PendingRequestAccessor) o;
                reqs.add(new PackSkip.Pending(r.privacyfix$id(), r.privacyfix$url(), r.privacyfix$hash()));
            }
            Minecraft.getInstance().setScreenAndShow(screen.privacyfix$parentScreen());
            PackSkip.skip(null, reqs, true);
        }).width(20).tooltip(Tooltip.create(Component.literal("PrivacyFix, thank Sai. If you want to download it then read chat"))).build());
    }
}
