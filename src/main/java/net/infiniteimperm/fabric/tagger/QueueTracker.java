package net.infiniteimperm.fabric.tagger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.List;

public class QueueTracker {
    private static final Pattern QUEUE_JOIN_PATTERN = Pattern.compile("\\[Queue\\] ([a-zA-Z0-9_]+) has joined the queue");
    private static final long TWO_WEEKS_IN_SECONDS = 14 * 24 * 60 * 60; // 2 weeks in seconds
    private static String currentClientName = "";
    
    /**
     * Initialize the queue tracker
     */
    public static void init() {
        // Initialize current client name
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            currentClientName = player.getName().getString();
        }
    }
    
    /**
     * Process a chat message to detect queue joins
     */
    public static void onChatMessage(String message) {
        // Try to match queue join pattern
        Matcher queueMatcher = QUEUE_JOIN_PATTERN.matcher(message);
        if (queueMatcher.find()) {
            String playerName = queueMatcher.group(1);
            
            // Skip if it's ourselves
            if (playerName.equalsIgnoreCase(currentClientName)) {
                return;
            }
            
            // Immediately show stats for this player
            showPlayerStats(playerName);
        }
    }
    
    /**
     * Check for client name updates (call this every tick)
     */
    public static void tick() {
        // Update client name if needed
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && !player.getName().getString().equals(currentClientName)) {
            currentClientName = player.getName().getString();
        }
    }
    
    /**
     * Show stats for a player who joined the queue
     */
    private static void showPlayerStats(String playerName) {
        TaggerMod.LOGGER.info("Showing stats for player: {}", playerName);
        
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        
        // Check if we already have data for this player
        Optional<UUID> playerUuidOpt = TagStorage.getPlayerUuidByName(playerName);
        if (playerUuidOpt.isPresent()) {
            UUID playerUuid = playerUuidOpt.get();
            Optional<TagStorage.PlayerData> playerDataOpt = TagStorage.getPlayerData(playerUuid);
            
            if (playerDataOpt.isPresent()) {
                // Show the stats we already have from tags.json
                TagStorage.PlayerData playerData = playerDataOpt.get();
                displayPlayerStats(player, playerName, playerData);
            } else {
                // No stats in our data
                player.sendMessage(Text.literal("Opponent: " + playerName + " - No stats available"), false);
            }
        } else {
            // Player not in our data
            player.sendMessage(Text.literal("Opponent: " + playerName + " - No stats available"), false);
        }
    }
    
    /**
     * Display stats for a specific player
     */
    private static void displayPlayerStats(ClientPlayerEntity player, String playerName, TagStorage.PlayerData playerData) {
        // Format crystal stats if available
        StringBuilder statsSummary = new StringBuilder();
        
        if (playerData.hasStats()) {
            TagStorage.CrystalStats stats = playerData.getCrystalStats();
            statsSummary.append("Crystal 1v1: ");
            statsSummary.append(formatStatsValue("W", stats.getWins(), StatsFormatter.getWinColor(stats.getWins())));
            statsSummary.append(", ");
            statsSummary.append(formatStatsValue("W%", String.format("%.1f%%", stats.getWinPercent()), 
                    StatsFormatter.getWinLossColor(stats.getWinPercent())));
            statsSummary.append(", ");
            statsSummary.append(formatStatsValue("CWS", stats.getCurrentWinStreak(), 
                    StatsFormatter.getWinStreakColor(stats.getCurrentWinStreak())));
            statsSummary.append(", ");
            statsSummary.append(formatStatsValue("BWS", stats.getBestWinStreak(), 
                    StatsFormatter.getWinStreakColor(stats.getBestWinStreak())));
            
            // Check if stats are older than 2 weeks
            long lastChecked = playerData.getLastChecked();
            long now = Instant.now().getEpochSecond();
            if (now - lastChecked > TWO_WEEKS_IN_SECONDS) {
                // Calculate days ago
                long daysAgo = (now - lastChecked) / (24 * 60 * 60);
                statsSummary.append(" §7(").append(daysAgo).append(" days ago)");
            }
        } else if (playerData.isPrivateStats()) {
            statsSummary.append("§9Private statistics");
        } else {
            statsSummary.append("No stats available");
        }
        
        // Format player name with tag and color - always show tag in the stats display
        String tagPrefix = "";
        if (playerData.getTag() != null && !playerData.getTag().isEmpty()) {
            String tag = playerData.getTag();
            String formatting = playerData.getColorCode();
            tagPrefix = formatting + "[" + tag + "] §r";
        }
        
        // Send the formatted message
        player.sendMessage(Text.literal("Opponent: " + tagPrefix + playerName + " - " + statsSummary), false);
    }
    
    /**
     * Format a stats value with color
     */
    private static String formatStatsValue(String label, Object value, String colorCode) {
        return label + ": " + colorCode + value + "§r";
    }
} 