package net.infiniteimperm.fabric.tagger.mixin;

import net.infiniteimperm.fabric.tagger.TagStorage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    // Commenting out this mixin to avoid interfering with other mods that modify player nametags
    /*
    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void tagger$modifyDisplayName(CallbackInfoReturnable<Text> cir) {
        // Cast 'this' to PlayerEntity to access its methods
        PlayerEntity playerEntity = (PlayerEntity) (Object) this;
        UUID playerUuid = playerEntity.getUuid();
        
        // Get player data from TagStorage
        Optional<TagStorage.PlayerData> playerDataOpt = TagStorage.getPlayerData(playerUuid);

        if (playerDataOpt.isPresent()) {
            TagStorage.PlayerData playerData = playerDataOpt.get();
            String tag = playerData.getTag();
            
            // Get the player's formatting
            Formatting formatting = playerData.getFormatting();
            
            // Get the player's base name
            Text baseName = playerEntity.getName();

            // Only add prefix if the tag should be displayed
            if (playerData.shouldDisplayTag()) {
                // Create the prefix [TAG] with the correct color
                MutableText prefix = Text.literal("[" + tag + "] ");
                prefix = prefix.formatted(formatting);

                // Combine prefix and base name
                MutableText modifiedName = prefix.append(baseName);

                // Set the modified name and cancel the original method
                cir.setReturnValue(modifiedName);
                cir.cancel();
            } else {
                // Just color the name for T and Private tags
                MutableText coloredName = Text.literal(playerEntity.getName().getString());
                
                // Always apply the correct color to hidden tags
                if (tag.equalsIgnoreCase("T")) {
                    coloredName = coloredName.formatted(Formatting.GOLD);
                } else if (tag.equalsIgnoreCase("Private")) {
                    coloredName = coloredName.formatted(Formatting.BLUE);
                } else {
                    coloredName = coloredName.formatted(formatting);
                }
                
                cir.setReturnValue(coloredName);
                cir.cancel();
            }
        }
        // If no tag, do nothing, let original method run.
    }
    */
} 