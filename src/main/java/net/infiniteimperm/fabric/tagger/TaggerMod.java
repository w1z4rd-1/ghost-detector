package net.infiniteimperm.fabric.tagger;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.infiniteimperm.fabric.tagger.TagStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public class TaggerMod implements ClientModInitializer {
    public static final String MOD_ID = "tagger";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    // SuggestionProvider type is FabricClientCommandSource
    private static final SuggestionProvider<FabricClientCommandSource> PLAYER_NAME_SUGGESTIONS = (context, builder) -> {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            // Use CommandSource.suggestMatching helper
            return CommandSource.suggestMatching(client.getNetworkHandler().getPlayerList().stream()
                .map(PlayerListEntry::getProfile)
                .map(com.mojang.authlib.GameProfile::getName), builder);
        }
        // Use CommandSource.suggestMatching helper
        return CommandSource.suggestMatching(Arrays.asList(), builder);
    };

    // SuggestionProvider type is FabricClientCommandSource
    private static final SuggestionProvider<FabricClientCommandSource> TAG_SUGGESTIONS = (context, builder) ->
            // Use CommandSource.suggestMatching helper
            CommandSource.suggestMatching(Arrays.asList(TagStorage.VALID_TAGS), builder);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Tagger Client Mod initialized");
        TagStorage.loadTags(); // Load tags on initialization

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("tag")
                // Main command execution (now assigns tags)
                .then(ClientCommandManager.argument("player", StringArgumentType.string()).suggests(PLAYER_NAME_SUGGESTIONS)
                    .then(ClientCommandManager.argument("tag", StringArgumentType.string()).suggests(TAG_SUGGESTIONS)
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "player");
                            String tag = StringArgumentType.getString(context, "tag");

                            if (!TagStorage.isValidTag(tag)) {
                                context.getSource().sendError(Text.literal("Invalid tag: " + tag + ". Valid tags are: " + String.join(", ", TagStorage.VALID_TAGS)));
                                return 0;
                            }

                            Optional<UUID> playerUuidOpt = TagStorage.getPlayerUuidByName(playerName);
                            if (playerUuidOpt.isEmpty()) {
                                context.getSource().sendError(Text.literal("Player not found: " + playerName));
                                return 0;
                            }

                            TagStorage.setPlayerTag(playerUuidOpt.get(), tag);
                            context.getSource().sendFeedback(Text.literal("Tagged " + playerName + " as " + tag));
                            return 1;
                        })))
                // Help subcommand
                .then(ClientCommandManager.literal("help")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal(
                            "§6Tagger Mod Commands:\n" +
                            "§e/tag <playername> <tag> §7- Assigns a tag (Runner, Competent, Skilled, T)\n" +
                            "§e/tag remove <playername> §7- Removes the tag from a player\n" +
                            "§e/tag help §7- Shows this help message"
                        ));
                        return 1;
                    }))
                // Remove subcommand
                 .then(ClientCommandManager.literal("remove")
                     .then(ClientCommandManager.argument("player", StringArgumentType.string()).suggests(PLAYER_NAME_SUGGESTIONS)
                         .executes(context -> {
                             String playerName = StringArgumentType.getString(context, "player");
                             Optional<UUID> playerUuidOpt = TagStorage.getPlayerUuidByName(playerName);

                             if (playerUuidOpt.isEmpty()) {
                                 context.getSource().sendError(Text.literal("Player not found: " + playerName));
                                 return 0;
                             }

                             UUID playerUuid = playerUuidOpt.get();
                             if (TagStorage.getPlayerTag(playerUuid).isPresent()) {
                                 TagStorage.removePlayerTag(playerUuid);
                                 context.getSource().sendFeedback(Text.literal("Removed tag from " + playerName));
                                 return 1;
                             } else {
                                 context.getSource().sendError(Text.literal(playerName + " does not have a tag."));
                                 return 0;
                             }
                         })))
                // Base command execution (shows help text if no arguments)
                .executes(context -> {
                     context.getSource().sendFeedback(Text.literal(
                            "§6Tagger Mod Commands:\n" +
                            "§e/tag <playername> <tag> §7- Assigns a tag (Runner, Competent, Skilled, T)\n" +
                            "§e/tag remove <playername> §7- Removes the tag from a player\n" +
                            "§e/tag help §7- Shows this help message"
                        ));
                    return 1;
                })
            );
        });
    }
} 