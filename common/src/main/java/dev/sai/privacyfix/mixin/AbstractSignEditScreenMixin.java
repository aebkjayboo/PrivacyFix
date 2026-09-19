package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.Audit;
import dev.sai.privacyfix.PrivacyFix;
import dev.sai.privacyfix.ProbeGuard;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Sign-editor half of the probe guard. The screen's constructor copies the
 * sign's (server-supplied) text into {@code messages}, which is what gets
 * sent back in ServerboundSignUpdatePacket when the player clicks Done.
 * Rewrite both the strings and the rendered text right after that copy.
 */
@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin {
    @Shadow private SignText text;
    @Shadow @Final private String[] messages;

    @Inject(method = "<init>(Lnet/minecraft/world/level/block/entity/SignBlockEntity;ZZLnet/minecraft/network/chat/Component;)V",
            at = @At("TAIL"))
    private void privacyfix$guardSignText(SignBlockEntity sign, boolean isFrontText, boolean isFiltered, Component title, CallbackInfo ci) {
        if (!PrivacyFix.active(PrivacyFix.config().probeGuard) || this.text == null) return;
        List<String> found = new ArrayList<>();
        SignText clean = this.text;
        for (int i = 0; i < this.messages.length; i++) {
            Component original = clean.getMessage(i, isFiltered);
            if (!original.getString().isEmpty()) Audit.log("sign editor line {}: {}", i, ProbeGuard.describe(original));
            Component sanitized = ProbeGuard.sanitize(original, found);
            clean = clean.setMessage(i, sanitized);
            this.messages[i] = sanitized.getString();
        }
        if (!found.isEmpty()) {
            this.text = clean;
            Audit.probe("sign editor", found);
        }
    }
}
