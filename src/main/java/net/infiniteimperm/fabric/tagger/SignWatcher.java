package net.infiniteimperm.fabric.tagger;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.ArrayList;

public class SignWatcher {

    private static final double PROXIMITY_DISTANCE = 4.5;
    private static final double PROXIMITY_DISTANCE_SQ = PROXIMITY_DISTANCE * PROXIMITY_DISTANCE; // Use squared distance for efficiency
    private static final MinecraftClient client = MinecraftClient.getInstance();

    private static BlockPos watchedSignPos = null;
    private static boolean isWatching = false;
    private static boolean lilBitchMode = false;
    private static Set<UUID> recentlyCheckedPlayers = new HashSet<>(); // Track players already checked in this session

    /**
     * Toggles watching a sign based on player interaction.
     * Called from the UseBlockCallback event.
     */
    public static boolean handleInteractBlock(PlayerEntity player, World world, BlockHitResult hitResult) {
        if (hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            BlockPos pos = hitResult.getBlockPos();
            if (world.getBlockEntity(pos) instanceof SignBlockEntity) {
                // Clicked on a sign
                if (isWatching && pos.equals(watchedSignPos)) {
                    // Stop watching if clicking the same sign again
                    stopWatching("Stopped watching sign.");
                } else {
                    // Start watching the new sign
                    watchedSignPos = pos;
                    isWatching = true;
                    recentlyCheckedPlayers.clear(); // Clear checked players for the new session
                    player.sendMessage(Text.literal("§aStarted watching sign at " + pos.toShortString()), false);
                    TaggerMod.LOGGER.info("[SignWatcher] Started watching sign at {}", pos);
                    
                    if (lilBitchMode) {
                        // Find the best player in the lobby and display their stats
                        displayBestPlayerInLobby();
                    }
                }
                return true; // Indicate we handled the interaction
            }
        }
        return false; // Didn't handle interaction (not a sign)
    }

    /**
     * Toggles LilBitch mode on/off
     */
    public static void toggleLilBitchMode() {
        lilBitchMode = !lilBitchMode;
        if (client.player != null) {
            String statusMessage = lilBitchMode ? 
                "§dLilBitch mode §aENABLED" : 
                "§dLilBitch mode §cDISABLED";
            client.player.sendMessage(Text.literal(statusMessage), false);
        }
        TaggerMod.LOGGER.info("[SignWatcher] LilBitch mode toggled to: {}", lilBitchMode);
    }
    
    /**
     * Returns the current state of LilBitch mode
     */
    public static boolean isLilBitchModeEnabled() {
        return lilBitchMode;
    }

    /**
     * Stops watching the current sign.
     */
    private static void stopWatching(String reason) {
        if (isWatching) {
            isWatching = false;
            watchedSignPos = null;
            recentlyCheckedPlayers.clear();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c" + reason), false);
            }
            TaggerMod.LOGGER.info("[SignWatcher] Stopped watching sign. Reason: {}", reason);
        }
    }

    /**
     * Called when a block is broken. Stops watching if the watched sign is broken.
     */
    public static void handleBlockBreak(World world, BlockPos pos) {
        if (isWatching && pos.equals(watchedSignPos)) {
            stopWatching("Watched sign was broken.");
        }
    }

    /**
     * Called every client tick to check for nearby players if watching a sign.
     */
    public static void tick() {
        if (!isWatching || watchedSignPos == null || client.world == null || client.player == null) {
            return;
        }

        World world = client.world;
        ClientPlayerEntity self = client.player;
        Vec3d signCenter = Vec3d.ofCenter(watchedSignPos);

        // Get detection radius from config if in LilBitch mode
        double checkDistance = lilBitchMode ? 
            LilBitchConfig.getInstance().getDetectionRadius() : PROXIMITY_DISTANCE;
        double checkDistanceSq = checkDistance * checkDistance;
        
        // Create a bounding box around the sign
        Box checkArea = new Box(watchedSignPos).expand(checkDistance);

        // Get players within the bounding box (more efficient than checking all world players)
        List<PlayerEntity> nearbyPlayers = world.getEntitiesByClass(PlayerEntity.class, checkArea, entity -> 
            entity != self && // Exclude self
            entity.getPos().squaredDistanceTo(signCenter) <= checkDistanceSq // Precise distance check
        );

        for (PlayerEntity nearbyPlayer : nearbyPlayers) {
            UUID playerUuid = nearbyPlayer.getUuid();

            // Check if we've already processed this player in this watch session
            if (recentlyCheckedPlayers.contains(playerUuid)) {
                continue;
            }

            String playerName = nearbyPlayer.getName().getString();

            // Check the player's tag
            Optional<TagStorage.PlayerData> playerDataOpt = TagStorage.getPlayerData(playerUuid);
            
            if (lilBitchMode) {
                // In LilBitch mode we need to do additional checks
                boolean isDangerous = false;
                String reason = "";
                
                LilBitchConfig config = LilBitchConfig.getInstance();
                
                if (playerDataOpt.isEmpty()) {
                    if (config.isDodgeUntaggedPlayers()) {
                        isDangerous = true;
                        reason = "untracked player";
                    }
                } else {
                    TagStorage.PlayerData playerData = playerDataOpt.get();
                    String tag = playerData.getTag().toLowerCase();
                    
                    // Check for specific tags to dodge
                    if ((config.isDodgeRunnerTag() && tag.equals("runner")) || 
                        (config.isDodgeCreatureTag() && tag.equals("creature")) ||
                        (config.isDodgeCompetentTag() && tag.equals("competent")) ||
                        (config.isDodgeSkilledTag() && tag.equals("skilled"))) {
                        isDangerous = true;
                        reason = "tagged as " + tag;
                    } else if (config.isDodgePrivateStats() && playerData.isPrivateStats()) {
                        isDangerous = true;
                        reason = "private stats";
                    } else if (playerData.hasStats() && playerData.getCrystalStats() != null) {
                        TagStorage.CrystalStats stats = playerData.getCrystalStats();
                        
                        // Log the actual win percent value for debugging
                        TaggerMod.LOGGER.info("[SignWatcher] Player {} has win percent: {}, wins: {}, best streak: {}", 
                            playerName, 
                            stats.getWinPercent(),
                            stats.getWins(),
                            stats.getBestWinStreak());
                        
                        // Check if win percent is in decimal form (0.6 = 60%) or already percentage (60.0 = 60%)
                        double storedWinPercent = stats.getWinPercent();
                        boolean isAlreadyPercentage = storedWinPercent > 1.0 && storedWinPercent <= 100.0;
                        double winPercent = isAlreadyPercentage ? storedWinPercent / 100.0 : storedWinPercent;
                        
                        TaggerMod.LOGGER.info("[SignWatcher] Actual win percent for {}: {}", playerName, winPercent);
                        
                        // Check high winrate
                        if (config.isDodgeHighWinrate()) {
                            double threshold = config.getHighWinrateThreshold();
                            int minWins = config.getHighWinrateMinWins();
                            
                            if ((isAlreadyPercentage && storedWinPercent > threshold || 
                                !isAlreadyPercentage && storedWinPercent > threshold/100.0) && 
                                stats.getWins() >= minWins) {
                                isDangerous = true;
                                reason = String.format("%.1f%% win rate with %d wins", winPercent * 100, stats.getWins());
                            }
                        }
                        
                        // Check perfect winrate
                        if (config.isDodgePerfectWinrate() && !isDangerous) {
                            int maxWins = config.getPerfectWinrateMaxWins();
                            
                            if (stats.getWins() < maxWins && 
                                (isAlreadyPercentage && storedWinPercent >= 100.0 || 
                                !isAlreadyPercentage && storedWinPercent >= 1.0)) {
                                isDangerous = true;
                                reason = "100% win rate with " + stats.getWins() + " wins";
                            }
                        }
                        
                        // Check winstreaks
                        if (config.isDodgeHighWinstreak() && !isDangerous) {
                            int streakThreshold = config.getHighWinstreakThreshold();
                            
                            if (stats.getBestWinStreak() > streakThreshold) {
                                isDangerous = true;
                                reason = stats.getBestWinStreak() + " best winstreak";
                            }
                        }
                    }
                }
                
                if (isDangerous) {
                    TaggerMod.LOGGER.info("[SignWatcher] LilBitch mode detected dangerous player {} ({}). Executing /leave and /autosc.", 
                            playerName, reason);
                    
                    // Send chat notification about why we're leaving
                    if (client.player != null) {
                        client.player.sendMessage(
                            Text.literal("§c[LilBitch] Leaving due to " + playerName + " (" + reason + ")"), 
                            false
                        );
                    }
                    
                    // Execute /leave and then /autosc
                    if (client.getNetworkHandler() != null) {
                        client.getNetworkHandler().sendChatCommand("leave");
                        
                        // Schedule autosc to run after a short delay, but only if DEBUG_MODE is on
                        if (TaggerMod.DEBUG_MODE) {
                            Thread scheduledTask = new Thread(() -> {
                                try {
                                    Thread.sleep(500); // Wait half a second
                                    if (client.getNetworkHandler() != null) {
                                        client.execute(() -> {
                                            client.getNetworkHandler().sendChatCommand("autosc");
                                        });
                                    }
                                } catch (InterruptedException e) {
                                    // Handle interruption
                                }
                            });
                            scheduledTask.start();
                        }
                    }
                    
                    // Mark player as checked and stop watching
                    recentlyCheckedPlayers.add(playerUuid);
                    stopWatching("Left game due to dangerous player: " + playerName);
                    break; // Exit loop after finding one dangerous player and leaving
                }
            } else {
                // Normal mode - only check for Runner/Creature tags
                String tag = playerDataOpt.map(TagStorage.PlayerData::getTag).orElse("").toLowerCase();
                
                if (tag.equals("runner") || tag.equals("creature")) {
                    // If tagged as Runner or Creature, execute /leave
                    TaggerMod.LOGGER.info("[SignWatcher] Detected tagged player {} (tag: '{}') near sign. Executing /leave.", playerName, tag);
                    if (client.getNetworkHandler() != null) {
                        client.getNetworkHandler().sendChatCommand("leave");
                    }
                    // Mark as checked so we don't spam /leave if they linger
                    recentlyCheckedPlayers.add(playerUuid);
                    break; // Exit loop after finding one tagged player and leaving
                }
            }
            
            // Mark player as checked regardless
            recentlyCheckedPlayers.add(playerUuid);
            TaggerMod.LOGGER.info("[SignWatcher] Detected player {} near sign. Ignoring.", playerName);
        }
    }
    
    /**
     * Calculates a threat score for a player based on their stats
     * Formula: (wins/50)*(1/(win%))^2
     */
    private static double calculatePlayerScore(TagStorage.PlayerData playerData) {
        if (!playerData.hasStats() || playerData.getCrystalStats() == null) {
            return 0.0;
        }
        
        LilBitchConfig config = LilBitchConfig.getInstance();
        if (!config.isEnableScoreCalculation()) {
            return 0.0; // Score calculation disabled
        }
        
        TagStorage.CrystalStats stats = playerData.getCrystalStats();
        int wins = stats.getWins();
        double storedWinPercent = stats.getWinPercent(); // This could be in decimal form or already percentage
        
        // Normalize win percent to decimal form (0.55 = 55%)
        boolean isAlreadyPercentage = storedWinPercent > 1.0 && storedWinPercent <= 100.0;
        double winPercent = isAlreadyPercentage ? storedWinPercent / 100.0 : storedWinPercent;
        
        // Log values to help with debugging
        TaggerMod.LOGGER.info("[SignWatcher] Calculating score for player with wins: {}, stored win percent: {}, normalized win percent: {}", 
            wins, storedWinPercent, winPercent);
        
        // Special case for 0% win rate - give them a low score based only on wins
        if (winPercent <= 0.0) {
            double score = wins / 100.0; // Simple score based just on wins
            TaggerMod.LOGGER.info("[SignWatcher] Player has 0% win rate. Calculated simple score: {}", score);
            return score;
        }
        
        // Avoid very low percentages that could cause extreme results
        if (winPercent < 0.01) { // Use 1% as minimum to avoid extreme values
            winPercent = 0.01;
        }
        
        // Apply formula with configurable parameters: (wins/divisor)*(1/(win%))^exponent
        double score = (wins / config.getScoreDivisor()) * 
                      Math.pow(1.0 / winPercent, config.getScoreExponent());
        
        // Log the calculated score
        TaggerMod.LOGGER.info("[SignWatcher] Calculated score: {}", score);
        
        return score;
    }
    
    /**
     * Display the best player in the lobby based on the formula
     */
    private static void displayBestPlayerInLobby() {
        if (client.getNetworkHandler() == null || client.player == null) {
            return;
        }
        
        // Create a list to store players and their scores
        List<PlayerScoreEntry> playerScores = new ArrayList<>();
        
        // Loop through all players in the tab list
        client.getNetworkHandler().getPlayerList().forEach(entry -> {
            String playerName = entry.getProfile().getName();
            UUID playerUuid = entry.getProfile().getId();
            
            // Skip ourselves
            if (playerName.equals(client.player.getName().getString())) {
                return;
            }
            
            // Get player data
            Optional<TagStorage.PlayerData> playerDataOpt = TagStorage.getPlayerData(playerUuid);
            if (playerDataOpt.isPresent()) {
                TagStorage.PlayerData playerData = playerDataOpt.get();
                
                if (playerData.hasStats() && playerData.getCrystalStats() != null) {
                    double score = calculatePlayerScore(playerData);
                    playerScores.add(new PlayerScoreEntry(playerName, playerData, score));
                }
            }
        });
        
        // Sort players by score (highest first)
        playerScores.sort(Comparator.comparing(PlayerScoreEntry::getScore).reversed());
        
        // Display the best player's stats
        if (!playerScores.isEmpty()) {
            PlayerScoreEntry bestPlayer = playerScores.get(0);
            TagStorage.CrystalStats stats = bestPlayer.playerData.getCrystalStats();
            
            // Normalize win percentage for display
            double storedWinPercent = stats.getWinPercent();
            boolean isAlreadyPercentage = storedWinPercent > 1.0 && storedWinPercent <= 100.0;
            double displayWinPercent = isAlreadyPercentage ? storedWinPercent : storedWinPercent * 100;
            
            String message = String.format(
                "§d[LilBitch] Best player: §f%s §d(Score: §f%.2f§d) §7- §fWins: %d§7, §fWin Rate: %.1f%%§7, §fWS: %d§7, §fBWS: %d", 
                bestPlayer.playerName,
                bestPlayer.score,
                stats.getWins(),
                displayWinPercent, // Display the correct percentage
                stats.getCurrentWinStreak(),
                stats.getBestWinStreak()
            );
            
            client.player.sendMessage(Text.literal(message), false);
        } else {
            client.player.sendMessage(Text.literal("§d[LilBitch] No players with stats found in lobby"), false);
        }
    }
    
    /**
     * Helper class to store player name, data and score for sorting
     */
    private static class PlayerScoreEntry {
        String playerName;
        TagStorage.PlayerData playerData;
        double score;
        
        PlayerScoreEntry(String playerName, TagStorage.PlayerData playerData, double score) {
            this.playerName = playerName;
            this.playerData = playerData;
            this.score = score;
        }
        
        double getScore() {
            return score;
        }
    }
} 