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
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class QueueTracker {
    private static final Pattern QUEUE_JOIN_PATTERN = Pattern.compile("\\[Queue\\] ([a-zA-Z0-9_]+) has joined the queue");
    private static final long TWO_WEEKS_IN_SECONDS = 14 * 24 * 60 * 60; // 2 weeks in seconds
    private static String currentClientName = "";
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final long STATS_CHECK_DELAY_SECONDS = 15;

    // Map to store pending stats checks
    private static Map<String, CompletableFuture<Void>> pendingStatsChecks = new ConcurrentHashMap<>();
    
    // Add patterns for messages to filter out
    private static final String LEAVE_MESSAGE = "You can use \"/leave\" to leave the game.";
    private static final String STATS_COUNTDOWN_MESSAGE = "You have 10 seconds to leave before statistics are counted.";
    
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
            
            // Immediately show stats for this player or schedule a check
            showPlayerStats(playerName);
        }
    }
    
    /**
     * Check if a message should be filtered out (not displayed)
     * @param message The message to check
     * @return true if the message should be filtered (hidden), false otherwise
     */
    public static boolean shouldFilterMessage(String message) {
        // Check if the message matches any of our filter patterns
        return message.equals(LEAVE_MESSAGE) || message.equals(STATS_COUNTDOWN_MESSAGE);
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
     * Show stats for a player who joined the queue or schedule a check if no stats exist.
     */
    private static void showPlayerStats(String playerName) {
        TaggerMod.LOGGER.info("[QueueTracker] Checking stats for player: {}", playerName);
        
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        
        // Cancel any existing pending check for this player
        if (pendingStatsChecks.containsKey(playerName)) {
            pendingStatsChecks.get(playerName).cancel(true);
            TaggerMod.LOGGER.info("[QueueTracker] Canceled previous pending stats check for {}", playerName);
        }

        // Check if we already have data for this player
        Optional<UUID> playerUuidOpt = TagStorage.getPlayerUuidByName(playerName); // Use sync version first
        if (playerUuidOpt.isPresent()) {
            UUID playerUuid = playerUuidOpt.get();
            Optional<TagStorage.PlayerData> playerDataOpt = TagStorage.getPlayerData(playerUuid);
            
            if (playerDataOpt.isPresent()) {
                TagStorage.PlayerData playerData = playerDataOpt.get();
                if (playerData.hasStats() || playerData.isPrivateStats()) {
                    // Show the stats we already have (including private)
                    displayPlayerStats(player, playerName, playerData);
                    return; // Don't schedule a check if we have data
                } else {
                    // We have a player entry, but no stats yet.
                    TaggerMod.LOGGER.info("[QueueTracker] No stats found for {}, scheduling /sc check in {} seconds.", playerName, STATS_CHECK_DELAY_SECONDS);
                    scheduleStatsCheck(playerName);
                }
            } else {
                // Player UUID known, but no PlayerData entry (shouldn't happen often, but handle it)
                 TaggerMod.LOGGER.info("[QueueTracker] No PlayerData found for {}, scheduling /sc check in {} seconds.", playerName, STATS_CHECK_DELAY_SECONDS);
                 scheduleStatsCheck(playerName);
            }
        } else {
            // Player not in our data cache/tab list yet.
            // Use async lookup to try Mojang API, then schedule check if needed.
            TagStorage.getPlayerUuidByNameAsync(playerName).thenAccept(asyncUuidOpt -> {
                if (asyncUuidOpt.isPresent()) {
                    UUID playerUuid = asyncUuidOpt.get();
                    Optional<TagStorage.PlayerData> playerDataOpt = TagStorage.getPlayerData(playerUuid);
                    if (playerDataOpt.isPresent() && (playerDataOpt.get().hasStats() || playerDataOpt.get().isPrivateStats())) {
                         // Found data async, display it (on main thread)
                         MinecraftClient.getInstance().execute(() -> displayPlayerStats(player, playerName, playerDataOpt.get()));
                    } else {
                        // No stats even after async lookup, schedule check
                        TaggerMod.LOGGER.info("[QueueTracker] No stats found for {} after async lookup, scheduling /sc check in {} seconds.", playerName, STATS_CHECK_DELAY_SECONDS);
                        scheduleStatsCheck(playerName);
                    }
                } else {
                     // Player not found even with Mojang API, schedule anyway?
                     // Let's assume they might log in or stats command handles it.
                     TaggerMod.LOGGER.warn("[QueueTracker] Could not find UUID for {}, scheduling /sc check anyway in {} seconds.", playerName, STATS_CHECK_DELAY_SECONDS);
                     scheduleStatsCheck(playerName);
                }
            });
        }
    }

    private static void scheduleStatsCheck(String playerName) {
        // Schedule /sc command after delay
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            if (client.player != null && client.getNetworkHandler() != null) {
                TaggerMod.LOGGER.info("[QueueTracker] Executing delayed /sc check for {}", playerName);
                client.getNetworkHandler().sendChatCommand("sc " + playerName);
            }
            pendingStatsChecks.remove(playerName); // Remove from pending list
        }, CompletableFuture.delayedExecutor(STATS_CHECK_DELAY_SECONDS, TimeUnit.SECONDS));
        
        pendingStatsChecks.put(playerName, future);
    }
    
    /**
     * Display stats for a specific player
     */
    private static void displayPlayerStats(ClientPlayerEntity player, String playerName, TagStorage.PlayerData playerData) {
        // Format player name with tag and color (consistent for all outcomes)
        String displayName = playerName;
        if (playerData.getTag() != null && !playerData.getTag().isEmpty()) {
            String tag = playerData.getTag();
            String formatting = playerData.getColorCode();
            displayName = formatting + "[" + tag + "] §r" + playerName;
        }

        if (playerData.hasStats()) {
            TagStorage.CrystalStats stats = playerData.getCrystalStats();
            
            // Get the colors for each stat (same as in StatsFormatter)
            String winColor = StatsFormatter.getWinColor(stats.getWins());
            String winPercentColor = StatsFormatter.getWinLossColor(stats.getWinPercent());
            String currentWinStreakColor = StatsFormatter.getWinStreakColor(stats.getCurrentWinStreak());
            String bestWinStreakColor = StatsFormatter.getWinStreakColor(stats.getBestWinStreak());
            
            // Format the message in the same way as the /sc command
            String formattedMessage = String.format(
                "%s, W: %s%d\n§rW%%: %s%.1f%%, §rCWS: %s%d, §rBWS: %s%d",
                displayName, winColor, stats.getWins(),
                winPercentColor, stats.getWinPercent(),
                currentWinStreakColor, stats.getCurrentWinStreak(),
                bestWinStreakColor, stats.getBestWinStreak()
            );
            
            // Check if stats are older than 2 weeks
            long lastChecked = playerData.getLastChecked();
            long now = Instant.now().getEpochSecond();
            if (now - lastChecked > TWO_WEEKS_IN_SECONDS) {
                // Calculate days ago
                long daysAgo = (now - lastChecked) / (24 * 60 * 60);
                formattedMessage += String.format(" §7(%d days ago)", daysAgo);
            }
            
            player.sendMessage(Text.literal(formattedMessage), false);
        } else if (playerData.isPrivateStats()) {
            // Display private stats message using the formatted name
            player.sendMessage(Text.literal(displayName + ", §9Private statistics"), false);
        } else {
            // This case should ideally not be reached here anymore due to scheduling,
            // but if it is, log it. Don't send a message.
            TaggerMod.LOGGER.warn("[QueueTracker] displayPlayerStats called for {} with no stats/private flag set.", playerName);
            // player.sendMessage(Text.literal(displayName + ", No stats available"), false); // Removed this message
        }
    }
    
    /**
     * Format a stats value with color - *Removed as it's no longer used here*
     *
    private static String formatStatsValue(String label, Object value, String colorCode) {
        return label + ": " + colorCode + value + "§r";
    }*/
} 