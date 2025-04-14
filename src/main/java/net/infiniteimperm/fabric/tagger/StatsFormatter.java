package net.infiniteimperm.fabric.tagger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatsFormatter {
    private static final Pattern WIN_PERCENT_PATTERN = Pattern.compile("- Win %: (\\d+\\.\\d+)");
    private static final Pattern WIN_STREAK_PATTERN = Pattern.compile("- Win Streak: (\\d+)");
    private static final Pattern HIGHEST_WIN_STREAK_PATTERN = Pattern.compile("- Highest Win Streak: (\\d+)");
    private static final Pattern WINS_PATTERN = Pattern.compile("wins: (\\d+)");
    private static final Pattern PRIVATE_STATS_PATTERN = Pattern.compile("Player's statistics are private");

    /**
     * Parse stats text and handle Crystal 1v1 data or private statistics
     */
    public static void parseStats(List<String> guiTexts, String playerName) {
        // Check if stats are private
        boolean isPrivate = isStatsPrivate(guiTexts);
        
        if (isPrivate) {
            handlePrivateStats(playerName);
            return;
        }
        
        // Parse Crystal 1v1 stats
        parseCrystalStats(guiTexts, playerName);
    }

    /**
     * Check if the player's stats are private
     */
    private static boolean isStatsPrivate(List<String> guiTexts) {
        if (guiTexts.size() == 1 && guiTexts.get(0).equals("Player's statistics are private")) {
            // This is our special marker for private stats detected from chat
            return true;
        }
        
        // Regular check for GUI-based private stats detection
        for (String line : guiTexts) {
            if (PRIVATE_STATS_PATTERN.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Handle a player with private stats
     */
    private static void handlePrivateStats(String playerName) {
        // Instead of immediately checking and sending a message, wait for the UUID resolution
        TagStorage.getPlayerUuidByNameAsync(playerName).thenAccept(playerUuidOpt -> {
            if (playerUuidOpt.isPresent()) {
                UUID playerUuid = playerUuidOpt.get();
                TagStorage.markPlayerStatsPrivate(playerUuid);
                MinecraftClient.getInstance().player.sendMessage(Text.literal("§9" + playerName + " has private statistics"), false);
            } else {
                MinecraftClient.getInstance().player.sendMessage(Text.literal("§cCould not find player: " + playerName), false);
            }
        });
    }

    /**
     * Parse Crystal 1v1 stats from the GUI text and send formatted results to chat
     */
    public static void parseCrystalStats(List<String> guiTexts, String playerName) {
        final boolean[] inCrystalStats = {false};
        final int[] wins = {0};
        final double[] winPercent = {0};
        final int[] currentWinStreak = {0};
        final int[] bestWinStreak = {0};

        if (TaggerMod.DEBUG_MODE) {
            TaggerMod.LOGGER.info("Starting to parse Crystal stats for " + playerName + ", found " + guiTexts.size() + " lines");
        }

        for (int i = 0; i < guiTexts.size(); i++) {
            String line = guiTexts.get(i);
            
            // Check if we're in the Crystal 1v1 section
            if (line.contains("Crystal 1v1")) {
                inCrystalStats[0] = true;
                if (TaggerMod.DEBUG_MODE) {
                    TaggerMod.LOGGER.info("Found Crystal 1v1 section at line " + i + ": " + line);
                }
                continue;
            }
            
            // Check if we've moved past Crystal 1v1 to another section
            if (inCrystalStats[0] && line.contains("-----------------") && i < guiTexts.size() - 1 
                    && !guiTexts.get(i+1).contains("Crystal 1v1")) {
                break;
            }
            
            if (inCrystalStats[0]) {
                // Parse win percentage
                Matcher winPercentMatcher = WIN_PERCENT_PATTERN.matcher(line);
                if (winPercentMatcher.find()) {
                    winPercent[0] = Double.parseDouble(winPercentMatcher.group(1));
                    if (TaggerMod.DEBUG_MODE) {
                        TaggerMod.LOGGER.info("Found win percent: " + winPercent[0]);
                    }
                }
                
                // Parse current win streak
                Matcher winStreakMatcher = WIN_STREAK_PATTERN.matcher(line);
                if (winStreakMatcher.find()) {
                    currentWinStreak[0] = Integer.parseInt(winStreakMatcher.group(1));
                    if (TaggerMod.DEBUG_MODE) {
                        TaggerMod.LOGGER.info("Found current win streak: " + currentWinStreak[0]);
                    }
                }
                
                // Parse best win streak
                Matcher highestWinStreakMatcher = HIGHEST_WIN_STREAK_PATTERN.matcher(line);
                if (highestWinStreakMatcher.find()) {
                    bestWinStreak[0] = Integer.parseInt(highestWinStreakMatcher.group(1));
                    if (TaggerMod.DEBUG_MODE) {
                        TaggerMod.LOGGER.info("Found best win streak: " + bestWinStreak[0]);
                    }
                }
                
                // Parse wins
                Matcher winsMatcher = WINS_PATTERN.matcher(line);
                if (winsMatcher.find()) {
                    wins[0] = Integer.parseInt(winsMatcher.group(1));
                    if (TaggerMod.DEBUG_MODE) {
                        TaggerMod.LOGGER.info("Found wins: " + wins[0]);
                    }
                }
            }
        }

        // Always update player stats entry regardless of whether we found Crystal 1v1 stats
        // Use async UUID lookup to prevent race conditions when player isn't in tab list
        TagStorage.getPlayerUuidByNameAsync(playerName).thenAccept(playerUuidOpt -> {
            if (playerUuidOpt.isPresent()) {
                UUID playerUuid = playerUuidOpt.get();
                
                if (inCrystalStats[0]) {
                    // Create stats object and store in TagStorage
                    if (wins[0] == 0 && winPercent[0] == 0 && currentWinStreak[0] == 0 && bestWinStreak[0] == 0) {
                        // No valid stats, send null to indicate no stats
                        if (TaggerMod.DEBUG_MODE) {
                            TaggerMod.LOGGER.warn("Found Crystal 1v1 section but no valid stats values were extracted for " + playerName);
                        }
                        TagStorage.updatePlayerStats(playerUuid, null);
                        MinecraftClient.getInstance().player.sendMessage(Text.literal("§cNo Crystal 1v1 stats found for " + playerName), false);
                    } else {
                        // Valid stats found
                        TagStorage.CrystalStats stats = TagStorage.createCrystalStats(wins[0], winPercent[0], currentWinStreak[0], bestWinStreak[0]);
                        TagStorage.updatePlayerStats(playerUuid, stats);
                        
                        // Show formatted stats in chat
                        sendFormattedStats(playerName, wins[0], winPercent[0], currentWinStreak[0], bestWinStreak[0]);
                    }
                } else {
                    // No Crystal 1v1 section found, update with null stats
                    if (TaggerMod.DEBUG_MODE) {
                        TaggerMod.LOGGER.info("No Crystal 1v1 section found for " + playerName);
                    }
                    TagStorage.updatePlayerStats(playerUuid, null);
                    MinecraftClient.getInstance().player.sendMessage(Text.literal("§cNo Crystal 1v1 stats found for " + playerName), false);
                }
            } else {
                // Still couldn't get UUID even after async lookup
                MinecraftClient.getInstance().player.sendMessage(Text.literal("§cCould not find player: " + playerName), false);
            }
        });
        
        // Don't need to show message here since it's handled in the async callback
    }

    /**
     * Send formatted stats to the player's chat
     */
    private static void sendFormattedStats(String playerName, int wins, double winPercent, int currentWinStreak, int bestWinStreak) {
        String winColor = getWinColor(wins);
        String winPercentColor = getWinLossColor(winPercent);
        String currentWinStreakColor = getWinStreakColor(currentWinStreak);
        String bestWinStreakColor = getWinStreakColor(bestWinStreak);
        
        // Add tag if player has one - using async to be consistent
        final String[] displayName = {playerName};
        
        TagStorage.getPlayerUuidByNameAsync(playerName).thenAccept(playerUuidOpt -> {
            if (playerUuidOpt.isPresent()) {
                UUID playerUuid = playerUuidOpt.get();
                Optional<TagStorage.PlayerData> playerDataOpt = TagStorage.getPlayerData(playerUuid);
                
                if (playerDataOpt.isPresent()) {
                    TagStorage.PlayerData playerData = playerDataOpt.get();
                    String tag = playerData.getTag();
                    
                    if (tag != null && !tag.isEmpty()) {
                        String colorCode = playerData.getColorCode();
                        displayName[0] = colorCode + "[" + tag + "] §r" + playerName;
                    }
                }
            }
            
            // Now we can format the message with the updated displayName
            String formattedMessage = String.format(
                "%s, W: %s%d\n§rW%%: %s%.1f%%, §rCWS: %s%d, §rBWS: %s%d",
                displayName[0], winColor, wins,
                winPercentColor, winPercent,
                currentWinStreakColor, currentWinStreak,
                bestWinStreakColor, bestWinStreak
            );
            
            MinecraftClient.getInstance().player.sendMessage(Text.literal(formattedMessage), false);
        });
    }

    /**
     * Get color code for wins count
     */
    public static String getWinColor(int wins) {
        if (wins >= 50000) return "§d";
        else if (wins >= 25000) return "§b";
        else if (wins >= 10000) return "§5";
        else if (wins >= 5000) return "§e";
        else if (wins >= 2000) return "§4";
        else if (wins >= 1000) return "§2";
        else if (wins >= 500) return "§3";
        else if (wins >= 250) return "§g";
        else if (wins >= 100) return "§f";
        else if (wins >= 50) return "§7";
        else return "";
    }

    /**
     * Get color code for win streak
     */
    public static String getWinStreakColor(int winStreak) {
        if (winStreak >= 1000) return "§5";
        else if (winStreak >= 750) return "§c";
        else if (winStreak >= 500) return "§d";
        else if (winStreak >= 250) return "§b§3";
        else if (winStreak >= 200) return "§5";
        else if (winStreak >= 100) return "§e";
        else if (winStreak >= 75) return "§4";
        else if (winStreak >= 50) return "§2";
        else if (winStreak >= 25) return "§b";
        else if (winStreak >= 10) return "§6";
        else if (winStreak >= 5) return "§f";
        else if (winStreak >= 2) return "§7";
        else if (winStreak >= 0) return "§8";
        else return "";
    }

    /**
     * Get color code for win/loss ratio
     */
    public static String getWinLossColor(double wlr) {
        if (wlr >= 99) return "§5";
        else if (wlr >= 95) return "§c";
        else if (wlr >= 92.5) return "§d";
        else if (wlr >= 90) return "§b§3";
        else if (wlr >= 87.5) return "§5";
        else if (wlr >= 85) return "§e";
        else if (wlr >= 82.5) return "§4";
        else if (wlr >= 80) return "§2";
        else if (wlr >= 75) return "§b";
        else if (wlr >= 70) return "§6";
        else if (wlr >= 50) return "§f";
        else if (wlr >= 25) return "§7";
        else if (wlr >= 0) return "§8";
        else return "";
    }
} 