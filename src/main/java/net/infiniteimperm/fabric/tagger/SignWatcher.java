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

public class SignWatcher {

    private static final double PROXIMITY_DISTANCE = 4.5;
    private static final double PROXIMITY_DISTANCE_SQ = PROXIMITY_DISTANCE * PROXIMITY_DISTANCE; // Use squared distance for efficiency
    private static final MinecraftClient client = MinecraftClient.getInstance();

    private static BlockPos watchedSignPos = null;
    private static boolean isWatching = false;
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
                }
                return true; // Indicate we handled the interaction
            }
        }
        return false; // Didn't handle interaction (not a sign)
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

        // Create a bounding box around the sign
        Box checkArea = new Box(watchedSignPos).expand(PROXIMITY_DISTANCE);

        // Get players within the bounding box (more efficient than checking all world players)
        List<PlayerEntity> nearbyPlayers = world.getEntitiesByClass(PlayerEntity.class, checkArea, entity -> 
            entity != self && // Exclude self
            entity.getPos().squaredDistanceTo(signCenter) <= PROXIMITY_DISTANCE_SQ // Precise distance check
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
            String tag = playerDataOpt.map(TagStorage.PlayerData::getTag).orElse("").toLowerCase();

            if (tag.equals("runner") || tag.equals("creature")) {
                // If tagged as Runner or Creature, execute /leave
                TaggerMod.LOGGER.info("[SignWatcher] Detected tagged player {} (tag: '{}') near sign. Executing /leave.", playerName, tag);
                if (client.getNetworkHandler() != null) {
                    client.getNetworkHandler().sendChatCommand("leave");
                }
                // Mark as checked so we don't spam /leave if they linger
                recentlyCheckedPlayers.add(playerUuid);
                // Stop watching after executing /leave ? Or just leave? For now, just leave.
                // stopWatching("Left game due to nearby Runner/Creature: " + playerName);
                break; // Exit loop after finding one tagged player and leaving
            } else {
                // Player is nearby but not tagged Runner/Creature - ignore them
                // Mark them as checked for this session so we don't re-evaluate them constantly
                recentlyCheckedPlayers.add(playerUuid);
                TaggerMod.LOGGER.info("[SignWatcher] Detected untagged player {} near sign. Ignoring.", playerName);
            }
        }
    }
} 