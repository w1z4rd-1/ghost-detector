package net.infiniteimperm.fabric.tagger.mixin;

import net.infiniteimperm.fabric.tagger.GhostTotemDetector;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(
        method = "setHealth",
        at = @At("HEAD") // Inject at the beginning of the method
    )
    private void onSetHealth(float health, CallbackInfo ci) {
        // Pass the health update to our detector
        // It's important to cast 'this' to the correct type
        GhostTotemDetector.onPlayerHealthUpdate((ClientPlayerEntity) (Object) this, health);
    }
} 