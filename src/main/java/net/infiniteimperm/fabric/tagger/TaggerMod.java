package net.infiniteimperm.fabric.tagger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TaggerMod implements ClientModInitializer {
    public static final String MOD_ID = "ghost-detector";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final boolean DEBUG_MODE = false; // Debug logging enabled

    @Override
    public void onInitializeClient() {
        LOGGER.info("Ghost Detector Mod initialized");

        // Register the tick event for various trackers
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            QueueDurabilityChecker.resetConfirmation(); // Reset confirmation timeout
            GhostTotemDetector.tick(client);
            KitDetector.tick(); // Add KitDetector tick
        });
        
        // Register event for processing chat messages
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            // Convert message to string and process through detectors
            String messageStr = message.getString();
            
            KitDetector.onChatMessage(messageStr); // Add kit detection
            GhostTotemDetector.onChatMessage(messageStr); // Detect player death via chat
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
    }
} 