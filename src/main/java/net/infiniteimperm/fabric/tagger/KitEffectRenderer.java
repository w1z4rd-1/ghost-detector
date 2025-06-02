package net.infiniteimperm.fabric.tagger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ConcurrentHashMap;

public class KitEffectRenderer {
    
    private static final ConcurrentHashMap<String, EffectData> activeEffects = new ConcurrentHashMap<>();
    
    /**
     * Strike a player with lightning for loading a kit
     */
    public static void addEffect(PlayerEntity player) {
        String playerName = player.getName().getString();
        TaggerMod.LOGGER.info("[KitEffectRenderer] Striking {} with lightning for loading a kit!", playerName);
        
        // Strike the player with lightning once
        strikeLightning(player);
        
        // Add cylinder particle effect that starts after lightning
        EffectData effect = new EffectData(player, System.currentTimeMillis());
        activeEffects.put(playerName, effect);
    }
    
    /**
     * Strike a player with dramatic lightning
     */
    private static void strikeLightning(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }
        
        Vec3d playerPos = player.getPos();
        
        // Create a lightning bolt entity at the player's position
        LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, client.world);
        lightning.refreshPositionAfterTeleport(playerPos.x, playerPos.y, playerPos.z);
        
        // Add the lightning to the world (correct method)
        client.world.addEntity(lightning);
        
        // Play dramatic lightning sounds
        client.world.playSound(client.player, playerPos.x, playerPos.y, playerPos.z, 
            SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 1.0f, 1.0f);
        client.world.playSound(client.player, playerPos.x, playerPos.y, playerPos.z, 
            SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.WEATHER, 0.8f, 1.2f);
        
        TaggerMod.LOGGER.info("[KitEffectRenderer] Lightning strike spawned at {}, {}, {}", 
            playerPos.x, playerPos.y, playerPos.z);
    }
    
    /**
     * Create cylinder particle effects around the player
     */
    private static void spawnCylinderEffect(PlayerEntity player, float progress) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        
        Vec3d playerPos = player.getPos();
        float time = System.currentTimeMillis() / 1000.0f;
        
        // Create vertical cylinder pillars around the player
        int pillarCount = 8; // Number of pillars forming the cylinder
        for (int pillar = 0; pillar < pillarCount; pillar++) {
            float pillarAngle = pillar * 2.0f * (float)Math.PI / pillarCount;
            float pillarRadius = 3.0f; // Cylinder radius
            float pillarX = (float)playerPos.x + pillarRadius * MathHelper.cos(pillarAngle);
            float pillarZ = (float)playerPos.z + pillarRadius * MathHelper.sin(pillarAngle);
            
            // Create vertical pillars going up
            for (int height = 0; height < 12; height++) { // 12 blocks tall cylinder
                float y = (float)playerPos.y + height + MathHelper.sin(time * 2.0f + height * 0.1f) * 0.2f;
                
                client.world.addParticle(ParticleTypes.END_ROD, pillarX, y, pillarZ, 0, 0.05, 0);
                client.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pillarX, y, pillarZ, 0, 0.02, 0);
            }
        }
        
        // Add some inner particles for more density
        int innerPillars = 4;
        for (int pillar = 0; pillar < innerPillars; pillar++) {
            float pillarAngle = pillar * 2.0f * (float)Math.PI / innerPillars + time * 1.5f;
            float pillarRadius = 1.5f; // Inner cylinder radius
            float pillarX = (float)playerPos.x + pillarRadius * MathHelper.cos(pillarAngle);
            float pillarZ = (float)playerPos.z + pillarRadius * MathHelper.sin(pillarAngle);
            
            // Shorter inner pillars
            for (int height = 0; height < 8; height++) {
                float y = (float)playerPos.y + height + MathHelper.sin(time * 3.0f + height * 0.2f) * 0.3f;
                
                client.world.addParticle(ParticleTypes.FIREWORK, pillarX, y, pillarZ, 0, 0.08, 0);
            }
        }
    }
    
    /**
     * Update cylinder particle effects
     */
    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // Update existing cylinder effects
        activeEffects.entrySet().removeIf(entry -> {
            EffectData effect = entry.getValue();
            PlayerEntity player = effect.player;
            
            // Effect lasts 3 seconds
            if (currentTime - effect.startTime > 3000) {
                return true; // Remove expired effect
            }
            
            // Check if player is still valid and alive
            if (player == null || !player.isAlive()) {
                return true; // Remove invalid effect
            }
            
            // Calculate effect progress (0.0 to 1.0)
            float progress = Math.min(1.0f, (currentTime - effect.startTime) / 3000.0f);
            
            // Spawn cylinder particle effects
            spawnCylinderEffect(player, progress);
            
            return false; // Keep the effect
        });
    }
    
    /**
     * Data class to hold effect information
     */
    private static class EffectData {
        final PlayerEntity player;
        final long startTime;
        
        EffectData(PlayerEntity player, long startTime) {
            this.player = player;
            this.startTime = startTime;
        }
    }
} 