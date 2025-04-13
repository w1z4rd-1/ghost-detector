package net.infiniteimperm.fabric.tagger.mixin;

import net.infiniteimperm.fabric.tagger.TagStorage;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
import java.util.Optional;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    @Inject(
        method = "getPlayerName",
        at = @At("HEAD"),
        cancellable = true
    )
    private void getPlayerNameMixin(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        UUID playerUuid = entry.getProfile().getId();
        Optional<TagStorage.PlayerData> playerDataOpt = TagStorage.getPlayerData(playerUuid);

        if (playerDataOpt.isPresent()) {
            TagStorage.PlayerData playerData = playerDataOpt.get();
            String tag = playerData.getTag();
            String profileName = entry.getProfile().getName();
            
            // Get Formatting for this player
            Formatting formatting = playerData.getFormatting();
            
            if (playerData.shouldDisplayTag()) {
                // Show the tag + name with formatting
                MutableText prefix = Text.literal("[" + tag + "] ");
                prefix = prefix.formatted(formatting);
                
                MutableText baseName = Text.literal(profileName);
                MutableText modifiedName = prefix.append(baseName);
                
                cir.setReturnValue(modifiedName);
                cir.cancel();
            } else {
                // Just apply the color without showing the tag
                MutableText coloredName = Text.literal(profileName);
                coloredName = coloredName.formatted(formatting);
                
                cir.setReturnValue(coloredName);
                cir.cancel();
            }
        }
    }
} 