package net.infiniteimperm.fabric.tagger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles automatic stats checking for multiple players
 */
public class AutoStatsChecker {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final long DELAY_BETWEEN_PLAYERS_MS = 2000; // 2 seconds between each player check
    private static long lastCheckTime = 0;
    private static boolean isRunning = false;
    private static Queue<String> pendingPlayers = new LinkedList<>();
    private static boolean waitingForStats = false;
    
    /**
     * Start the automatic stats checking process for all untagged players
     */
    public static void startAutoStatsCheck() {
        if (isRunning) {
            TaggerMod.LOGGER.info("Auto stats check already running, skipping request");
            return;
        }
        
        // Get all players from tab list
        List<String> untaggedPlayers = getUntaggedPlayers();
        
        if (untaggedPlayers.isEmpty()) {
            TaggerMod.LOGGER.info("No untagged players found, nothing to check");
            return;
        }
        
        TaggerMod.LOGGER.info("Found {} untagged players to check", untaggedPlayers.size());
        pendingPlayers.clear();
        pendingPlayers.addAll(untaggedPlayers);
        
        isRunning = true;
        lastCheckTime = 0; // Start immediately
        waitingForStats = false;
    }
    
    /**
     * Process the queue of players to check stats for
     */
    public static void tick() {
        if (!isRunning || client.player == null) return;
        
        // If we're waiting for stats, check if StatsReader has a task running
        if (waitingForStats) {
            // If there's no task running, we can proceed to the next player
            if (StatsReader.isIdle()) {
                // Close any open screen (the stats GUI) before continuing
                if (client.currentScreen != null) {
                    client.setScreen(null);
                    TaggerMod.LOGGER.info("Closed stats GUI before proceeding to next player");
                }
                
                waitingForStats = false;
                lastCheckTime = System.currentTimeMillis();
            }
            return;
        }
        
        // Check if we should process the next player
        if (System.currentTimeMillis() - lastCheckTime < DELAY_BETWEEN_PLAYERS_MS) {
            return;
        }
        
        // Process the next player if available
        if (!pendingPlayers.isEmpty()) {
            // Ensure any previous GUI is closed before requesting new stats
            if (client.currentScreen != null) {
                client.setScreen(null);
                TaggerMod.LOGGER.info("Closing any open screen before requesting stats");
            }
            
            String playerName = pendingPlayers.poll();
            TaggerMod.LOGGER.info("Checking stats for: {}", playerName);
            
            // Send feedback to player
            String remainingMsg = pendingPlayers.isEmpty() ? 
                    "This is the last player." : 
                    pendingPlayers.size() + " players remaining.";
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§7Auto-checking stats for §f" + playerName + "§7. " + remainingMsg),
                false
            );
            
            // Request the player's stats
            CompletableFuture<List<String>> future = StatsReader.readPlayerStats(playerName);
            
            // Handle the result
            future.thenAccept(texts -> {
                if (texts.isEmpty()) {
                    TaggerMod.LOGGER.warn("No stats data was found for " + playerName);
                } else {
                    TaggerMod.LOGGER.info("Found " + texts.size() + " text entries from stats for " + playerName);
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
                                    TaggerMod.LOGGER.info("Auto-tagged " + playerName + " as Private (private stats) after reading stats");
                                } else if (playerData.hasStats()) {
                                    // Tag as T only if they have valid stats
                                    TagStorage.setPlayerTag(playerUuid, "T", null);
                                    TaggerMod.LOGGER.info("Auto-tagged " + playerName + " as T after reading stats");
                                }
                            }
                        }
                    }
                }
            });
            
            waitingForStats = true;
        } else {
            // All done
            isRunning = false;
            if (client.player != null) {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("§aCompleted auto stats check for all players."),
                    false
                );
            }
        }
    }
    
    /**
     * Get a list of player names who don't have tags yet
     */
    private static List<String> getUntaggedPlayers() {
        List<String> untaggedPlayers = new ArrayList<>();
        
        if (client.getNetworkHandler() == null) {
            return untaggedPlayers;
        }
        
        // Check each player in the tab list
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            String playerName = entry.getProfile().getName();
            
            // Skip ourselves
            if (client.player != null && playerName.equals(client.player.getName().getString())) {
                continue;
            }
            
            // Check if player already has a tag
            UUID playerUuid = entry.getProfile().getId();
            Optional<String> existingTag = TagStorage.getPlayerTag(playerUuid);
            
            if (existingTag.isEmpty()) {
                untaggedPlayers.add(playerName);
            }
        }
        
        return untaggedPlayers;
    }
} 