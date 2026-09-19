package dev.sai.privacyfix.mixin;

import dev.sai.privacyfix.Audit;
import dev.sai.privacyfix.PrivacyFix;
import dev.sai.privacyfix.ProbeGuard;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;

/**
 * Anvil half of the probe guard. The anvil pre-fills its name box with the
 * input item's hover name and sends whatever is in the box back as
 * ServerboundRenameItemPacket. A server can put a translatable custom name
 * on the item it hands you, so the pre-fill is sanitized the same way as
 * sign text. Only the pre-fill is touched: if the box no longer equals the
 * raw item name (player typed something) it is left alone.
 */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
    @ModifyArg(method = {"subInit", "slotChanged"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;setValue(Ljava/lang/String;)V"))
    private String privacyfix$guardAnvilName(String value) {
        if (!PrivacyFix.active(PrivacyFix.config().probeGuard) || value == null || value.isEmpty()) return value;
        AbstractContainerMenu menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        ItemStack stack = menu.getSlot(0).getItem();
        if (stack.isEmpty()) return value;
        Component name = stack.getHoverName();
        if (!value.equals(name.getString())) return value;
        Audit.log("anvil item name: {}", ProbeGuard.describe(name));
        List<String> found = new ArrayList<>();
        String clean = ProbeGuard.sanitize(name, found).getString();
        if (!found.isEmpty()) Audit.probe("anvil", found);
        return clean;
    }
}
