package net.infiniteimperm.fabric.tagger.mixin;

import net.infiniteimperm.fabric.tagger.TagStorage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void tagger$modifyDisplayName(CallbackInfoReturnable<Text> cir) {
        // Cast 'this' to PlayerEntity to access its methods
        PlayerEntity playerEntity = (PlayerEntity) (Object) this;
        UUID playerUuid = playerEntity.getUuid();

        Optional<String> tagOpt = TagStorage.getPlayerTag(playerUuid);

        if (tagOpt.isPresent()) {
            String tag = tagOpt.get();

            // Only add prefix if the tag is not "T"
            if (!"T".equalsIgnoreCase(tag)) {
                // Create the prefix [TAG]
                MutableText prefix = Text.literal("[" + tag + "] ")
                                         .formatted(Formatting.GOLD);

                // Get the player's base name (without risking recursion)
                Text baseName = playerEntity.getName(); // PlayerEntity.getName() gives the base profile name

                // Combine prefix and base name
                MutableText modifiedName = prefix.append(baseName);

                // Set the modified name and cancel the original method
                cir.setReturnValue(modifiedName);
                cir.cancel();
            }
        }
        // If no tag or tag is "T", do nothing, let original method run.
    }
} 