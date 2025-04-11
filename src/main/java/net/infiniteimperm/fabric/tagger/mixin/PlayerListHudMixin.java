package net.infiniteimperm.fabric.tagger.mixin;

import net.infiniteimperm.fabric.tagger.TagStorage;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
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
        Optional<String> tagOpt = TagStorage.getPlayerTag(playerUuid);

        if (tagOpt.isPresent()) {
            String tag = tagOpt.get();
            MutableText prefix = Text.literal("[" + tag + "] ")
                                     .formatted(Formatting.GOLD);
            
            String profileName = entry.getProfile().getName();
            MutableText baseName = Text.literal(profileName);

            MutableText modifiedName = prefix.append(baseName);

            cir.setReturnValue(modifiedName);
            cir.cancel();
        }
    }
} 