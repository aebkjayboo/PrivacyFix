package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.Audit;
import dev.sai.privacyfix.Panic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server dialogs (1.21.6+) are the newest trap screen; refuse them too while force mode is locked. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class DialogLockMixin {
    @Inject(method = "handleShowDialog", at = @At("HEAD"), cancellable = true)
    private void privacyfix$lockDialog(ClientboundShowDialogPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread() && Panic.locked()) {
            Audit.log("force lock: refused server dialog");
            ci.cancel();
        }
    }
}
