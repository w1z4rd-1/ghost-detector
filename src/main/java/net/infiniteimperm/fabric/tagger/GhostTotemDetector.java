package net.infiniteimperm.fabric.tagger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.text.SimpleDateFormat;
import java.util.Date;

public class GhostTotemDetector {

    private static long totemEquipTimeNano = 0;
    private static int lastCheckedSlot = -1; // Track the last known selected slot
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
    private static float lastHealth = -1; // Track last known health
    private static boolean wasAlive = true; // Track whether player was alive last tick
    private static GameMode lastGameMode = null; // Track the last game mode
    
    // Variables to track previously held items
    private static boolean wasHoldingTotemLastTick = false;
    private static long gameTickCounter = 0; // Count game ticks for detailed timing info
    private static long totemEquipGameTick = 0; // The game tick when totem was equipped
    
    // For diagnostic purposes - track if we've detected a ghost totem recently
    private static long lastGhostTotemTime = 0;
    private static long ghostTotemHoldTime = 0;
    
    // Constants
    private static final double SECONDS_PER_TICK = 0.05; // 50ms per tick (20 ticks per second)

    // Called every client tick
    public static void tick(MinecraftClient client) {
        gameTickCounter++; // Increment our own tick counter for precise timing
        
        if (client.player == null || client.player.getInventory() == null) {
            // Reset if player or inventory is not available (e.g., title screen)
            if (totemEquipTimeNano > 0 || lastCheckedSlot != -1 || lastHealth != -1) {
                if (TaggerMod.DEBUG_MODE) {
                    TaggerMod.LOGGER.info("[GhostTotem] Resetting state (player/inventory null)");
                }
                totemEquipTimeNano = 0;
                lastCheckedSlot = -1;
                lastHealth = -1;
                wasAlive = true;
                wasHoldingTotemLastTick = false;
                lastGameMode = null;
            }
            return;
        }

        ClientPlayerEntity player = client.player;
        int currentSlot = player.getInventory().selectedSlot;
        
        // Get current game mode - note this uses interactionManager which is client-side
        GameMode currentGameMode = null;
        if (client.interactionManager != null) {
            currentGameMode = client.interactionManager.getCurrentGameMode();
        }
        
        // Multiple ways to detect death
        float currentHealth = player.getHealth();
        boolean isDead = player.isDead(); // Direct method to check if player is dead
        boolean isAlive = !isDead && currentHealth > 0;
        
        // Check for transition to spectator mode (common server behavior for death)
        boolean spectatorTransition = false;
        if (lastGameMode != null && currentGameMode == GameMode.SPECTATOR && lastGameMode != GameMode.SPECTATOR) {
            spectatorTransition = true;
            TaggerMod.LOGGER.info("[GhostTotem] Player transitioned to SPECTATOR mode (potential death)");
        }
        
        // If we were alive before but now we're not, or we entered spectator mode, we died
        if ((wasAlive && !isAlive) || spectatorTransition) {
            TaggerMod.LOGGER.info("[GhostTotem] Death detected! isDead: {}, currentHealth: {}, spectatorTransition: {}, totem active: {}", 
                              isDead, currentHealth, spectatorTransition, totemEquipTimeNano > 0);
            
            // Log state of Death Screen as additional info
            if (client.currentScreen != null) {
                TaggerMod.LOGGER.info("[GhostTotem] Current screen: {}", client.currentScreen.getClass().getSimpleName());
            } else {
                TaggerMod.LOGGER.info("[GhostTotem] No current screen");
            }
            
            // Call our death handler
            onPlayerDeath(player);
        }
        wasAlive = isAlive;
        lastHealth = currentHealth;
        lastGameMode = currentGameMode;
        
        // Check if player is currently holding a totem (in either hand)
        boolean isHoldingTotemNow = isPlayerHoldingTotem(player);
        
        // If totem state changed
        if (isHoldingTotemNow != wasHoldingTotemLastTick) {
            if (isHoldingTotemNow) {
                // Player just started holding a totem
                handleTotemEquipped(player);
            } else {
                // Player just stopped holding a totem
                handleTotemUnequipped(player);
            }
        }
        
        // Log special debug for totem slot changes even if slot didn't change
        if (currentSlot != lastCheckedSlot) {
            ItemStack currentItem = player.getInventory().main.get(currentSlot);
            if (TaggerMod.DEBUG_MODE) {
                TaggerMod.LOGGER.info("[GhostTotem] Slot changed from {} to {}. New item: {}", 
                                    lastCheckedSlot, currentSlot, currentItem.getItem());
            }
            lastCheckedSlot = currentSlot;
        }
        
        // Store the current totem state for next tick
        wasHoldingTotemLastTick = isHoldingTotemNow;
    }
    
    // Utility method to check if player is holding a totem in either hand
    private static boolean isPlayerHoldingTotem(ClientPlayerEntity player) {
        // Check main hand (current selected slot)
        ItemStack mainHandItem = player.getInventory().main.get(player.getInventory().selectedSlot);
        // Check off hand
        ItemStack offHandItem = player.getOffHandStack();
        
        return mainHandItem.getItem() == Items.TOTEM_OF_UNDYING || 
               offHandItem.getItem() == Items.TOTEM_OF_UNDYING;
    }
    
    // Called when a totem is equipped
    private static void handleTotemEquipped(ClientPlayerEntity player) {
        // Only start the timer if it wasn't already running
        if (totemEquipTimeNano == 0) {
            totemEquipTimeNano = System.nanoTime();
            totemEquipGameTick = gameTickCounter;
            
            // Determine where the totem is (main or off hand)
            ItemStack mainHandItem = player.getInventory().main.get(player.getInventory().selectedSlot);
            ItemStack offHandItem = player.getOffHandStack();
            
            boolean inMainHand = mainHandItem.getItem() == Items.TOTEM_OF_UNDYING;
            boolean inOffHand = offHandItem.getItem() == Items.TOTEM_OF_UNDYING;
            
            String location = inMainHand ? 
                "main hand (slot " + player.getInventory().selectedSlot + ")" : 
                (inOffHand ? "off hand" : "unknown");
            
            // Always log this regardless of DEBUG_MODE
            TaggerMod.LOGGER.info("[GhostTotem] Totem equipped in {}. System time: {}, Game tick: {}", 
                               location, TIME_FORMAT.format(new Date()), totemEquipGameTick);
        }
    }
    
    // Called when a totem is unequipped
    private static void handleTotemUnequipped(ClientPlayerEntity player) {
        // Only handle if we were tracking a totem
        if (totemEquipTimeNano > 0) {
            long unequipTimeNano = System.nanoTime();
            long heldDurationNanos = unequipTimeNano - totemEquipTimeNano;
            long heldDurationMillis = heldDurationNanos / 1_000_000;
            long ticksHeld = gameTickCounter - totemEquipGameTick;
            
            // Always log regardless of DEBUG_MODE
            TaggerMod.LOGGER.info("[GhostTotem] Totem unequipped. Was held for {} ms ({} game ticks).", 
                               heldDurationMillis, ticksHeld);
            
            totemEquipTimeNano = 0;
        }
    }

    // This handles player death with ghost totem detection
    private static void onPlayerDeath(ClientPlayerEntity player) {
        // Check if we were holding a totem when we died
        if (totemEquipTimeNano > 0) {
            long deathTimeNano = System.nanoTime();
            long durationNanos = deathTimeNano - totemEquipTimeNano;
            long durationMillis = durationNanos / 1_000_000; // Convert nanoseconds to milliseconds
            long deathGameTick = gameTickCounter;
            long ticksHeld = deathGameTick - totemEquipGameTick;

            // Determine if the totem was in main hand or off hand at time of death
            ItemStack mainHandItem = player.getInventory().main.get(player.getInventory().selectedSlot);
            ItemStack offHandItem = player.getOffHandStack();
            
            boolean inMainHand = mainHandItem.getItem() == Items.TOTEM_OF_UNDYING;
            boolean inOffHand = offHandItem.getItem() == Items.TOTEM_OF_UNDYING;
            
            String handType = inMainHand ? "Mainhand" : (inOffHand ? "Offhand" : "Unknown");

            // Save these values for diagnostics
            lastGhostTotemTime = System.currentTimeMillis();
            ghostTotemHoldTime = durationMillis;

            // Detailed game tick info for the consistent 48-49ms issue
            TaggerMod.LOGGER.info("[GhostTotem] ====== GHOST TOTEM DETECTED! ======");
            TaggerMod.LOGGER.info("[GhostTotem] Hand: {}", handType);
            TaggerMod.LOGGER.info("[GhostTotem] Totem equipped on game tick: {}", totemEquipGameTick);
            TaggerMod.LOGGER.info("[GhostTotem] Death occurred on game tick: {}", deathGameTick);
            TaggerMod.LOGGER.info("[GhostTotem] Ticks between equip and death: {}", ticksHeld);
            TaggerMod.LOGGER.info("[GhostTotem] Expected duration for {} ticks: {} ms", 
                               ticksHeld, (ticksHeld * 1000 * SECONDS_PER_TICK));
            TaggerMod.LOGGER.info("[GhostTotem] Actual measured duration: {} ms", durationMillis);
            TaggerMod.LOGGER.info("[GhostTotem] Difference from expected: {} ms", 
                              durationMillis - (ticksHeld * 1000 * SECONDS_PER_TICK));
            TaggerMod.LOGGER.info("[GhostTotem] ===============================");

            // Always log this critical event with precise timing info
            TaggerMod.LOGGER.info("[GhostTotem] Death with {} totem held! Equip time: {}, Death time: {}, Held for {} ms ({} game ticks)",
                             handType.toLowerCase(), TIME_FORMAT.format(new Date(System.currentTimeMillis() - durationMillis)), 
                             TIME_FORMAT.format(new Date()), durationMillis, ticksHeld);

            // Send the message to chat
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.getNetworkHandler() != null) {
                // Format the message to send to public chat with hand information
                String publicMessage = String.format("[INSIGNIA] <%s Ghost Detected> totem held for %dms (%d ticks)", 
                                                 handType, durationMillis, ticksHeld);
                
                // Send the message as a regular chat message (will be visible to other players)
                client.getNetworkHandler().sendChatMessage(publicMessage);
            }

            // Reset the timer immediately to prevent multiple messages for the same death event
            totemEquipTimeNano = 0;
        } else {
            // Log that a death was detected, but no totem was active
            TaggerMod.LOGGER.info("[GhostTotem] Death detected, but no totem was being held.");
        }
    }

    // This method is left for compatibility with the mixin, but we'll move the actual logic to onPlayerDeath
    // Deprecated in favor of the more reliable onPlayerDeath method
    public static void onPlayerHealthUpdate(ClientPlayerEntity player, float health) {
        // If health dropped to zero, call our unified death handler
        if (health <= 0) {
            TaggerMod.LOGGER.info("[GhostTotem] Death detected via health update method. Health: {}", health);
            onPlayerDeath(player);
        }
    }
    
    // These methods are for diagnostics if needed
    public static long getLastGhostTotemTime() {
        return lastGhostTotemTime;
    }
    
    public static long getGhostTotemHoldTime() {
        return ghostTotemHoldTime;
    }
} 