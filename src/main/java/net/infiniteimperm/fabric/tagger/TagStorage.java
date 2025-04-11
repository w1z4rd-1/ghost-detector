package net.infiniteimperm.fabric.tagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

public class TagStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(TaggerMod.MOD_ID);
    private static final Path TAG_FILE = CONFIG_DIR.resolve("tags.json");
    private static Map<UUID, String> playerTags = new HashMap<>();

    // Valid tags
    public static final String[] VALID_TAGS = {"Runner", "Competent", "Skilled", "T"};

    public static void loadTags() {
        try {
            Files.createDirectories(CONFIG_DIR); // Ensure directory exists
            if (Files.exists(TAG_FILE)) {
                Reader reader = Files.newBufferedReader(TAG_FILE);
                Type type = new TypeToken<HashMap<UUID, String>>() {}.getType();
                playerTags = GSON.fromJson(reader, type);
                reader.close();
                if (playerTags == null) { // Handle case where file is empty or invalid
                    playerTags = new HashMap<>();
                }
                TaggerMod.LOGGER.info("Loaded {} player tags.", playerTags.size());
            } else {
                playerTags = new HashMap<>();
                TaggerMod.LOGGER.info("Tag file not found, creating new map.");
                saveTags(); // Create the file if it doesn't exist
            }
        } catch (IOException e) {
            TaggerMod.LOGGER.error("Failed to load player tags", e);
            playerTags = new HashMap<>(); // Use empty map on error
        }
    }

    public static void saveTags() {
        try {
            Files.createDirectories(CONFIG_DIR); // Ensure directory exists again just in case
            Writer writer = Files.newBufferedWriter(TAG_FILE);
            GSON.toJson(playerTags, writer);
            writer.close();
            // TaggerMod.LOGGER.info("Saved {} player tags.", playerTags.size()); // Optional: Log save confirmation
        } catch (IOException e) {
            TaggerMod.LOGGER.error("Failed to save player tags", e);
        }
    }

    public static void setPlayerTag(UUID playerUuid, String tag) {
        if (isValidTag(tag)) {
            playerTags.put(playerUuid, tag);
            saveTags();
        } else {
             TaggerMod.LOGGER.warn("Attempted to set invalid tag '{}' for UUID {}", tag, playerUuid);
        }
    }

    public static void removePlayerTag(UUID playerUuid) {
        if (playerTags.containsKey(playerUuid)) {
            playerTags.remove(playerUuid);
            saveTags();
        }
    }

    public static Optional<String> getPlayerTag(UUID playerUuid) {
        return Optional.ofNullable(playerTags.get(playerUuid));
    }

    public static Optional<UUID> getPlayerUuidByName(String playerName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            return client.getNetworkHandler().getPlayerList()
                    .stream()
                    .filter(entry -> entry.getProfile().getName().equalsIgnoreCase(playerName))
                    .map(entry -> entry.getProfile().getId())
                    .findFirst();
        }
        return Optional.empty(); // Cannot find UUID if not connected or player not online
    }
    
     public static boolean isValidTag(String tag) {
        for (String validTag : VALID_TAGS) {
            if (validTag.equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }
} 