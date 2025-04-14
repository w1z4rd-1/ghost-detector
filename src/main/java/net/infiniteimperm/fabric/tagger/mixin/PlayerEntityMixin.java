package net.infiniteimperm.fabric.tagger.mixin;

import net.infiniteimperm.fabric.tagger.TagStorage;
import net.infiniteimperm.fabric.tagger.TaggerMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import java.util.Optional;
import java.util.UUID;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    // Use ModifyReturnValue to prepend the tag to the name
    @ModifyReturnValue(method = "getDisplayName", at = @At("RETURN"))
    private Text tagger$modifyDisplayName(Text original) {
        // Cast 'this' to PlayerEntity to access its methods
        PlayerEntity playerEntity = (PlayerEntity) (Object) this;
        UUID playerUuid = playerEntity.getUuid();
        
        // Get player data from TagStorage
        Optional<TagStorage.PlayerData> playerDataOpt = TagStorage.getPlayerData(playerUuid);

        if (playerDataOpt.isPresent()) {
            TagStorage.PlayerData playerData = playerDataOpt.get();
            String tag = playerData.getTag();
            
            // Get the player's formatting (color)
            Formatting formatting = playerData.getFormatting();
            
            // Only add prefix if the tag should be displayed
            if (playerData.shouldDisplayTag()) {
                // Create the prefix [TAG] with the correct color
                MutableText prefix = Text.literal("[" + tag + "] ");
                prefix = prefix.formatted(formatting);

                // Prepend the prefix to the original Text (which might already be modified)
                return prefix.append(original);
            } else {
                // For hidden tags (T, Private), we might just want to color the original name
                // However, applying color directly might conflict with other mods.
                // Let's just return the original for hidden tags to be safe for now.
                // We apply color in the PlayerListHudMixin separately.
                // Alternatively, we could try to apply color *only* if no other color is present,
                // but that's more complex. Let's stick to the safer approach.
                // TaggerMod.LOGGER.info("Applying hidden tag format for: " + playerEntity.getName().getString());
                // return original.copy().formatted(formatting); // This might override other mod's colors
                return original; // Safest option: don't modify display name for hidden tags here
            }
        } else {
             // If no tag data, return the original unmodified text
            return original;
        }
    }
} 