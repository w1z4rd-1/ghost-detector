package net.infiniteimperm.fabric.tagger;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;

public class TaggerMod implements ClientModInitializer {
    public static final String MOD_ID = "insignia";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final boolean DEBUG_MODE = true; // Set to false to disable debug logging

    /**
     * Utility class to prevent running the stats command too frequently
     */
    public static class StatsCommandRateLimiter {
        private static final double MIN_COMMAND_INTERVAL_SECONDS = 1.2;
        private static long lastCommandTimeMs = 0;
        
        /**
         * Check if enough time has passed since the last stats command
         * @return true if a command can be run, false if it's too soon
         */
        public static boolean canRunCommand() {
            long currentTimeMs = System.currentTimeMillis();
            long elapsedMs = currentTimeMs - lastCommandTimeMs;
            boolean canRun = elapsedMs >= (MIN_COMMAND_INTERVAL_SECONDS * 1000);
            
            if (DEBUG_MODE && !canRun) {
                LOGGER.info("[RateLimiter] Too soon to run stats command. Wait {} more ms", 
                    (MIN_COMMAND_INTERVAL_SECONDS * 1000) - elapsedMs);
            }
            
            return canRun;
        }
        
        /**
         * Record that a command was run
         */
        public static void recordCommandRun() {
            lastCommandTimeMs = System.currentTimeMillis();
            if (DEBUG_MODE) {
                LOGGER.info("[RateLimiter] Stats command time recorded: {}", lastCommandTimeMs);
            }
        }
        
        /**
         * Helper method to run a stats command if allowed
         * @param playerName The player to check stats for
         * @return true if command was sent, false if rate limited
         */
        public static boolean runStatsCommand(String playerName) {
            if (!canRunCommand()) {
                return false;
            }
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.getNetworkHandler() != null) {
                client.getNetworkHandler().sendChatCommand("sc " + playerName);
                recordCommandRun();
                return true;
            }
            return false;
        }
    }

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

    // SuggestionProvider type is FabricClientCommandSource for tag suggestions
    private static final SuggestionProvider<FabricClientCommandSource> TAG_SUGGESTIONS = (context, builder) ->
            // Use CommandSource.suggestMatching helper
            CommandSource.suggestMatching(Arrays.asList(TagStorage.VALID_TAGS), builder);
            
    // SuggestionProvider for color codes
    private static final SuggestionProvider<FabricClientCommandSource> COLOR_SUGGESTIONS = (context, builder) -> {
        List<String> colorOptions = Arrays.asList(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", 
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", 
            "yellow", "white"
        );
        return CommandSource.suggestMatching(colorOptions, builder);
    };

    // Map color names to Minecraft color codes
    private static final Map<String, String> COLOR_MAP = new HashMap<>();
    static {
        COLOR_MAP.put("black", "§0");
        COLOR_MAP.put("dark_blue", "§1");
        COLOR_MAP.put("dark_green", "§2");
        COLOR_MAP.put("dark_aqua", "§3");
        COLOR_MAP.put("dark_red", "§4");
        COLOR_MAP.put("dark_purple", "§5");
        COLOR_MAP.put("gold", "§6");
        COLOR_MAP.put("gray", "§7");
        COLOR_MAP.put("dark_gray", "§8");
        COLOR_MAP.put("blue", "§9");
        COLOR_MAP.put("green", "§a");
        COLOR_MAP.put("aqua", "§b");
        COLOR_MAP.put("red", "§c");
        COLOR_MAP.put("light_purple", "§d");
        COLOR_MAP.put("yellow", "§e");
        COLOR_MAP.put("white", "§f");
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Insignia Client Mod initialized");
        TagStorage.loadTags(); // Load tags on initialization
        LilBitchConfig.loadConfig(); // Load LilBitch configuration

        // Register the tick event for various trackers
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            StatsReader.tick();
            QueueTracker.tick();
            GhostTotemDetector.tick(client);
            SignWatcher.tick(); // Add SignWatcher tick
            KitDetector.tick(); // Add KitDetector tick
            QueueDurabilityChecker.resetConfirmation(); // Reset confirmation timeout
            if (DEBUG_MODE) {
                AutoStatsChecker.tick();
            }
        });
        
        // Register event for processing chat messages
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            // Convert message to string and process through QueueTracker
            String messageStr = message.getString();
            
            QueueTracker.onChatMessage(messageStr);
            KitDetector.onChatMessage(messageStr); // Add kit detection
            
            // Handle private stats detection directly from chat
            if (messageStr.contains("Player's statistics are private")) {
                // If there's an active stats check, mark it as private
                StatsReader.handlePrivateStats();
            }
        });
        
        // Register event for intercepting commands before they're sent
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            // This intercepts incoming messages, but we need outgoing command interception
            // Check if the message should be filtered out
            String messageStr = message.getString();
            return !QueueTracker.shouldFilterMessage(messageStr);
        });

        // Register event for player block interaction (for SignWatcher)
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand == Hand.MAIN_HAND && SignWatcher.handleInteractBlock(player, world, hitResult)) {
                return ActionResult.SUCCESS; // Prevent default sign behavior if we handled it
            }
            return ActionResult.PASS; // Continue with default behavior
        });

        // Register event for block breaking (for SignWatcher)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            SignWatcher.handleBlockBreak(world, pos);
        });

        // Register HUD render callback for Totem Warning Overlay (in-game view)
        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
            // Check condition and render directly here, avoid calling overlay class method
            if (TotemWarningOverlay.shouldShowWarning()) {
                int screenWidth = MinecraftClient.getInstance().getWindow().getScaledWidth();
                int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
                drawContext.fill(0, 0, screenWidth, screenHeight, 0x40FF0000); // Draw overlay directly
            }
        });

        // Initialize QueueTracker
        QueueTracker.init();
                                            
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // Register the SC command
            dispatcher.register(ClientCommandManager.literal("sc")
                .then(ClientCommandManager.argument("player", StringArgumentType.string()).suggests(PLAYER_NAME_SUGGESTIONS)
                    .executes(context -> {
                        String playerName = StringArgumentType.getString(context, "player");
                        context.getSource().sendFeedback(Text.literal("Reading stats for " + playerName + "..."));
                        
                        // Record command in rate limiter since we're manually sending it
                        // (We're not rate-limiting manual commands, but we need to record them)
                        StatsCommandRateLimiter.recordCommandRun();
                        
                        // Call the StatsReader to read the player's stats
                        CompletableFuture<List<String>> future = StatsReader.readPlayerStats(playerName);
                        
                        // Handle the result when it completes
                        future.thenAccept(texts -> {
                            if (texts.isEmpty()) {
                                LOGGER.warn("No stats data was found for " + playerName);
                            } else {
                                LOGGER.info("Found " + texts.size() + " text entries from stats for " + playerName);
                                // Process the stats with our formatter
                                StatsFormatter.parseStats(texts, playerName);
                                
                                // Automatically tag the player as "T"
                                Optional<UUID> playerUuidOpt = TagStorage.getPlayerUuidByName(playerName);
                                if (playerUuidOpt.isPresent()) {
                                    UUID playerUuid = playerUuidOpt.get();
                                    // Only set tag if player doesn't already have one
                                    if (TagStorage.getPlayerTag(playerUuid).isEmpty()) {
                                        // Check if the player has stats or private stats
                                        Optional<TagStorage.PlayerData> playerDataOpt = TagStorage.getPlayerData(playerUuid);
                                        if (playerDataOpt.isPresent()) {
                                            TagStorage.PlayerData playerData = playerDataOpt.get();
                                            if (playerData.isPrivateStats()) {
                                                // Tag as Private for private stats
                                                TagStorage.setPlayerTag(playerUuid, "Private", "blue");
                                                LOGGER.info("Auto-tagged " + playerName + " as Private (private stats) after reading stats");
                                            } else if (playerData.hasStats()) {
                                                // Tag as T only if they have valid stats
                                                TagStorage.setPlayerTag(playerUuid, "T", null);
                                                LOGGER.info("Auto-tagged " + playerName + " as T after reading stats");
                                            }
                                        }
                                    }
                                }
                            }
                        });
                        
                        return 1;
                    }))
            );
            
            // Register the LB command alias
            dispatcher.register(ClientCommandManager.literal("lb")
                .executes(context -> {
                    MinecraftClient client = context.getSource().getClient();
                    if (client != null && client.getNetworkHandler() != null) {
                        client.getNetworkHandler().sendChatCommand("topstats mostwins Crystal1v1");
                        context.getSource().sendFeedback(Text.literal("Executed: /topstats mostwins Crystal1v1"));
                        return 1; // Command success
                    } else {
                        context.getSource().sendError(Text.literal("Could not send command."));
                        return 0; // Command failure
                    }
                })
            );
            
            // Register the AutoSC command only if debug mode is enabled
            if (DEBUG_MODE) {
                dispatcher.register(ClientCommandManager.literal("autosc")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal("Starting automatic stats check for all untagged players..."));
                        AutoStatsChecker.startAutoStatsCheck();
                        return 1;
                    })
                );
            }
            
            // Register the lilbitch command
            dispatcher.register(ClientCommandManager.literal("lilbitch")
                .executes(context -> {
                    SignWatcher.toggleLilBitchMode();
                    return 1;
                })
                .then(ClientCommandManager.literal("reload")
                    .executes(context -> {
                        LilBitchConfig.loadConfig();
                        context.getSource().sendFeedback(Text.literal("§aReloaded LilBitch configuration"));
                        return 1;
                    })
                )
            );
        
            // Updated tag command registration with color parameter
            dispatcher.register(ClientCommandManager.literal("tag")
                // Main command execution now with more flexible structure
                .then(ClientCommandManager.argument("player", StringArgumentType.string()).suggests(PLAYER_NAME_SUGGESTIONS)
                    .then(ClientCommandManager.argument("tag", StringArgumentType.string()).suggests(TAG_SUGGESTIONS)
                        .executes(context -> {
                            // Tag command without color specified (use default color)
                            String playerName = StringArgumentType.getString(context, "player");
                            String tag = StringArgumentType.getString(context, "tag");

                            if (!TagStorage.isValidTag(tag)) {
                                context.getSource().sendError(Text.literal("Invalid tag (cannot be empty): " + tag));
                                return 0;
                            }

                            // Use the new async method to get the UUID and set the tag
                            TagStorage.setPlayerTagByName(playerName, tag, null).thenAccept(success -> {
                                if (success) {
                                    context.getSource().sendFeedback(Text.literal("Tagged \"" + playerName + "\" as \"" + tag + "\" (using default color)"));
                                } else {
                                    context.getSource().sendError(Text.literal("Could not find player: " + playerName));
                                }
                            });
                            
                            // Return success immediately, the feedback will be sent when the lookup completes
                            return 1;
                        })
                        .then(ClientCommandManager.argument("color", StringArgumentType.word()).suggests(COLOR_SUGGESTIONS)
                            .executes(context -> {
                                // Tag command with color specified
                                String playerName = StringArgumentType.getString(context, "player");
                                String tag = StringArgumentType.getString(context, "tag");
                                String colorName = StringArgumentType.getString(context, "color");

                                // Convert color name to color code
                                String colorNameLower = colorName.toLowerCase();
                                
                                if (!TagStorage.COLOR_NAME_TO_CODE.containsKey(colorNameLower) && !colorName.startsWith("§")) {
                                    context.getSource().sendError(Text.literal("Unknown color: " + colorName));
                                    return 0;
                                }
                                
                                if (!TagStorage.isValidTag(tag)) {
                                     context.getSource().sendError(Text.literal("Invalid tag (cannot be empty): " + tag));
                                    return 0;
                                }

                                // Use the new async method to get the UUID and set the tag
                                TagStorage.setPlayerTagByName(playerName, tag, colorName).thenAccept(success -> {
                                    if (success) {
                                        context.getSource().sendFeedback(Text.literal("Tagged \"" + playerName + "\" as \"" + tag + "\" with color " + colorName));
                                    } else {
                                        context.getSource().sendError(Text.literal("Could not find player: " + playerName));
                                    }
                                });
                                
                                // Return success immediately, the feedback will be sent when the lookup completes
                                return 1;
                            })
                        )
                    )
                )
                // Help subcommand
                .then(ClientCommandManager.literal("help")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal(
                            "§6Insignia Mod Commands:\n" +
                            "§e/tag <playername> <tag> [color] §7- Assigns a tag with optional color\n" +
                            "§e/tag remove <playername> §7- Removes the tag from a player\n" +
                            "§e/tag help §7- Shows this help message\n" + 
                            "§e/sc <playername> §7- Shows Crystal 1v1 stats for a player\n" +
                            "§e/lilbitch §7- Toggles LilBitch mode for dodging dangerous players\n" +
                            "§e/lilbitch reload §7- Reloads LilBitch configuration from file"
                        ));
                        return 1;
                    }))
                // Remove subcommand
                 .then(ClientCommandManager.literal("remove")
                     .then(ClientCommandManager.argument("player", StringArgumentType.string()).suggests(PLAYER_NAME_SUGGESTIONS)
                         .executes(context -> {
                             String playerName = StringArgumentType.getString(context, "player");
                             
                             // Use the async method to remove the tag by setting it to null
                             TagStorage.setPlayerTagByName(playerName, null, null).thenAccept(success -> {
                                 if (success) {
                                     context.getSource().sendFeedback(Text.literal("Removed tag for " + playerName));
                                 } else {
                                     context.getSource().sendError(Text.literal("Player " + playerName + " not found"));
                                 }
                             });
                             
                             // Return success immediately, feedback will be sent via callback
                             return 1;
                         })))
                // Rename subcommand
                .then(ClientCommandManager.literal("rename")
                    .then(ClientCommandManager.argument("player", StringArgumentType.string()).suggests(PLAYER_NAME_SUGGESTIONS)
                        .then(ClientCommandManager.argument("tag", StringArgumentType.string()).suggests(TAG_SUGGESTIONS)
                            .executes(context -> {
                                String playerName = StringArgumentType.getString(context, "player");
                                String tag = StringArgumentType.getString(context, "tag");
                                
                                // Use the async method to set the tag by name
                                TagStorage.setPlayerTagByName(playerName, tag, null).thenAccept(success -> {
                                    if (success) {
                                        context.getSource().sendFeedback(Text.literal("Changed tag for " + playerName + " to " + tag));
                                    } else {
                                        context.getSource().sendError(Text.literal("Player " + playerName + " not found"));
                                    }
                                });
                                
                                // Return success immediately, feedback will be sent via callback
                                return 1;
                            }))))
                // Base command execution (shows help text if no arguments)
                .executes(context -> {
                     context.getSource().sendFeedback(Text.literal(
                            "§6Insignia Mod Commands:\n" +
                            "§e/tag <playername> <tag> [color] §7- Assigns a tag with optional color\n" +
                            "§e/tag remove <playername> §7- Removes the tag from a player\n" +
                            "§e/tag help §7- Shows this help message\n" + 
                            "§e/sc <playername> §7- Shows Crystal 1v1 stats for a player\n" +
                            "§e/lilbitch §7- Toggles LilBitch mode for dodging dangerous players\n" +
                            "§e/lilbitch reload §7- Reloads LilBitch configuration from file"
                        ));
                    return 1;
                })
            );
        });
    }
} 