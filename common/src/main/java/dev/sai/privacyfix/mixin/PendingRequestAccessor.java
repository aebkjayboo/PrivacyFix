package dev.sai.privacyfix.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.net.URL;
import java.util.UUID;

/** record PendingRequest(UUID id, URL url, String hash) inside the pack prompt screen. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl$PackConfirmScreen$PendingRequest")
public interface PendingRequestAccessor {
    @Accessor("id")
    UUID privacyfix$id();

    @Accessor("url")
    URL privacyfix$url();

    @Accessor("hash")
    String privacyfix$hash();
}
