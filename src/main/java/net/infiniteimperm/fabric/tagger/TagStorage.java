package net.infiniteimperm.fabric.tagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

public class TagStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(TaggerMod.MOD_ID);
    private static final Path TAG_FILE = CONFIG_DIR.resolve("tags.json");
    private static Map<UUID, PlayerData> playerData = new HashMap<>();

    // Valid tags for tab completion
    public static final String[] VALID_TAGS = {"Runner", "Competent", "Skilled", "Creature"};
    
    // Color mapping for predefined tags
    public static final Map<String, String> DEFAULT_COLORS = new HashMap<>();
    static {
        DEFAULT_COLORS.put("skilled", "aqua"); // Cyan
        DEFAULT_COLORS.put("competent", "dark_purple"); // Purple
        DEFAULT_COLORS.put("runner", "dark_red"); // Dark Red
        DEFAULT_COLORS.put("creature", "red"); // Red
        DEFAULT_COLORS.put("private", "blue"); // Blue
        DEFAULT_COLORS.put("default", "gold"); // Gold for other tags
        
        // Add case variations just to be safe
        DEFAULT_COLORS.put("Skilled", "aqua");
        DEFAULT_COLORS.put("Competent", "dark_purple");
        DEFAULT_COLORS.put("Runner", "dark_red");
        DEFAULT_COLORS.put("Creature", "red");
    }
    
    // Map to convert between color names and color codes (two-way)
    public static final Map<String, String> COLOR_NAME_TO_CODE = new HashMap<>();
    public static final Map<String, String> COLOR_CODE_TO_NAME = new HashMap<>();
    static {
        COLOR_NAME_TO_CODE.put("black", "§0");
        COLOR_NAME_TO_CODE.put("dark_blue", "§1");
        COLOR_NAME_TO_CODE.put("dark_green", "§2");
        COLOR_NAME_TO_CODE.put("dark_aqua", "§3");
        COLOR_NAME_TO_CODE.put("dark_red", "§4");
        COLOR_NAME_TO_CODE.put("dark_purple", "§5");
        COLOR_NAME_TO_CODE.put("gold", "§6");
        COLOR_NAME_TO_CODE.put("gray", "§7");
        COLOR_NAME_TO_CODE.put("dark_gray", "§8");
        COLOR_NAME_TO_CODE.put("blue", "§9");
        COLOR_NAME_TO_CODE.put("green", "§a");
        COLOR_NAME_TO_CODE.put("aqua", "§b");
        COLOR_NAME_TO_CODE.put("red", "§c");
        COLOR_NAME_TO_CODE.put("light_purple", "§d");
        COLOR_NAME_TO_CODE.put("yellow", "§e");
        COLOR_NAME_TO_CODE.put("white", "§f");
        
        // Create reverse mapping
        for (Map.Entry<String, String> entry : COLOR_NAME_TO_CODE.entrySet()) {
            COLOR_CODE_TO_NAME.put(entry.getValue(), entry.getKey());
        }
    }

    // Class to store player data
    public static class PlayerData {
        private String tag;
        private String color; // Now stored as name (green, red, etc)
        private CrystalStats crystalStats;
        private boolean hasStats;
        private long lastChecked;
        private boolean privateStats;

        public PlayerData(String tag, String color) {
            this.tag = (tag == null || tag.isEmpty()) ? "T" : tag;
            this.color = convertToColorName(color); // Ensure stored as name
            this.crystalStats = null;
            this.hasStats = false;
            this.lastChecked = Instant.now().getEpochSecond();
            this.privateStats = false;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public String getColor() {
            return color;
        }
        
        public String getColorCode() {
            return COLOR_NAME_TO_CODE.getOrDefault(color, COLOR_NAME_TO_CODE.get("gold"));
        }
        
        public Formatting getFormatting() {
            return getColorFormatting(color);
        }

        public void setColor(String color) {
            this.color = convertToColorName(color);
        }

        public CrystalStats getCrystalStats() {
            return crystalStats;
        }

        public void setCrystalStats(CrystalStats crystalStats) {
            if (crystalStats != null) {
                this.crystalStats = crystalStats;
                this.hasStats = true;
            }
        }
        
        public boolean hasStats() {
            return hasStats;
        }
        
        public void setHasStats(boolean hasStats) {
            this.hasStats = hasStats;
        }

        public long getLastChecked() {
            return lastChecked;
        }

        public void setLastChecked(long lastChecked) {
            this.lastChecked = lastChecked;
        }
        
        public boolean isPrivateStats() {
            return privateStats;
        }
        
        public void setPrivateStats(boolean privateStats) {
            this.privateStats = privateStats;
        }
        
        public void updateLastChecked() {
            this.lastChecked = Instant.now().getEpochSecond();
        }
        
        // Should we display this tag in-game?
        public boolean shouldDisplayTag() {
            return !tag.equalsIgnoreCase("T") && !tag.equalsIgnoreCase("Private");
        }
    }

    // Class to store Crystal 1v1 stats
    public static class CrystalStats {
        private int wins;
        private double winPercent;
        private int currentWinStreak;
        private int bestWinStreak;

        public CrystalStats() {
            this.wins = 0;
            this.winPercent = 0;
            this.currentWinStreak = 0;
            this.bestWinStreak = 0;
        }

        public CrystalStats(int wins, double winPercent, int currentWinStreak, int bestWinStreak) {
            this.wins = wins;
            this.winPercent = winPercent;
            this.currentWinStreak = currentWinStreak;
            this.bestWinStreak = bestWinStreak;
        }

        // Getters and setters
        public int getWins() {
            return wins;
        }

        public void setWins(int wins) {
            this.wins = wins;
        }

        public double getWinPercent() {
            return winPercent;
        }

        public void setWinPercent(double winPercent) {
            this.winPercent = winPercent;
        }

        public int getCurrentWinStreak() {
            return currentWinStreak;
        }

        public void setCurrentWinStreak(int currentWinStreak) {
            this.currentWinStreak = currentWinStreak;
        }

        public int getBestWinStreak() {
            return bestWinStreak;
        }

        public void setBestWinStreak(int bestWinStreak) {
            this.bestWinStreak = bestWinStreak;
        }
        
        // Check if all values are zero/empty
        public boolean isEmpty() {
            return wins == 0 && winPercent == 0 && currentWinStreak == 0 && bestWinStreak == 0;
        }
    }

    public static void loadTags() {
        try {
            Files.createDirectories(CONFIG_DIR); // Ensure directory exists
            if (Files.exists(TAG_FILE)) {
                Reader reader = Files.newBufferedReader(TAG_FILE);
                Type type = new TypeToken<HashMap<UUID, PlayerData>>() {}.getType();
                playerData = GSON.fromJson(reader, type);
                reader.close();
                if (playerData == null) { // Handle case where file is empty or invalid
                    playerData = new HashMap<>();
                }
                TaggerMod.LOGGER.info("Loaded {} player data entries.", playerData.size());
            } else {
                playerData = new HashMap<>();
                TaggerMod.LOGGER.info("Tag file not found, creating new map.");
                saveTags(); // Create the file if it doesn't exist
            }
        } catch (IOException e) {
            TaggerMod.LOGGER.error("Failed to load player data", e);
            playerData = new HashMap<>(); // Use empty map on error
        }
    }

    public static void saveTags() {
        try {
            Files.createDirectories(CONFIG_DIR); // Ensure directory exists again just in case
            Writer writer = Files.newBufferedWriter(TAG_FILE);
            GSON.toJson(playerData, writer);
            writer.close();
        } catch (IOException e) {
            TaggerMod.LOGGER.error("Failed to save player data", e);
        }
    }

    public static void setPlayerTag(UUID playerUuid, String tag, String colorOrCode) {
        // Convert color code to name if needed
        String colorName = convertToColorName(colorOrCode);
        
        // If colorName is null, assign a default color based on the tag
        if (colorName == null) {
            colorName = getDefaultColorForTag(tag);
        }
        
        // Check if player already exists in our data
        if (playerData.containsKey(playerUuid)) {
            PlayerData data = playerData.get(playerUuid);
            data.setTag(tag);
            data.setColor(colorName);
        } else {
            playerData.put(playerUuid, new PlayerData(tag, colorName));
        }
        saveTags();
    }
    
    public static void markPlayerStatsPrivate(UUID playerUuid) {
        PlayerData data = playerData.getOrDefault(playerUuid, 
                new PlayerData("Private", DEFAULT_COLORS.get("private")));
        data.setPrivateStats(true);
        data.setTag("Private");
        data.setColor(DEFAULT_COLORS.get("private"));
        data.updateLastChecked();
        data.setHasStats(false);
        playerData.put(playerUuid, data);
        saveTags();
    }

    public static void updatePlayerStats(UUID playerUuid, CrystalStats stats) {
        // Check if the player exists, create if not
        if (!playerData.containsKey(playerUuid)) {
            playerData.put(playerUuid, new PlayerData("", "white"));
        }
        
        // Don't store stats if null or they're all zeros
        if (stats == null || stats.isEmpty()) {
            PlayerData data = playerData.get(playerUuid);
            data.setCrystalStats(null);
            data.setHasStats(false);
            data.updateLastChecked();
            data.setPrivateStats(false);
            saveTags();
            return;
        }
        
        // Store the stats if they're valid
        PlayerData data = playerData.get(playerUuid);
        data.setCrystalStats(stats);
        data.setHasStats(true);
        data.updateLastChecked();
        data.setPrivateStats(false);
        saveTags();
    }

    public static void removePlayerTag(UUID playerUuid) {
        if (playerData.containsKey(playerUuid)) {
            playerData.remove(playerUuid);
            saveTags();
        }
    }

    public static Optional<PlayerData> getPlayerData(UUID playerUuid) {
        return Optional.ofNullable(playerData.get(playerUuid));
    }
    
    public static Optional<String> getPlayerTag(UUID playerUuid) {
        return getPlayerData(playerUuid).map(PlayerData::getTag);
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
    
    public static boolean isValidTagForCompletion(String tag) {
        for (String validTag : VALID_TAGS) {
            if (validTag.equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }
    
    // Any tag is valid now, we just have special ones for tab completion
    public static boolean isValidTag(String tag) {
        return tag != null && !tag.isEmpty();
    }
    
    /**
     * Get the default color code for a tag
     * @param tag The tag to get the color code for
     * @return The color code for the tag
     */
    public static String getDefaultColorForTag(String tag) {
        switch (tag) {
            case "Runner": return "§4"; // dark red
            case "Competent": return "§9"; // blue (dark_purple)
            case "Skilled": return "§b"; // cyan (aqua) 
            case "Creature": return "§c"; // red
            case "T": return "§6"; // gold
            default: return "§f"; // white
        }
    }
    
    public static CrystalStats createCrystalStats(int wins, double winPercent, int currentWinStreak, int bestWinStreak) {
        return new CrystalStats(wins, winPercent, currentWinStreak, bestWinStreak);
    }
    
    /**
     * Convert a color value to a color name
     * Handles both color codes (§a) and color names (green)
     */
    public static String convertToColorName(String colorValue) {
        if (colorValue == null) {
            return null; // Return null so default color logic can run
        }
        
        // If it's already a color name, return it
        if (COLOR_NAME_TO_CODE.containsKey(colorValue.toLowerCase())) {
            return colorValue.toLowerCase();
        }
        
        // If it's a color code, convert to name
        if (COLOR_CODE_TO_NAME.containsKey(colorValue)) {
            return COLOR_CODE_TO_NAME.get(colorValue);
        }
        
        // Default
        return DEFAULT_COLORS.get("default");
    }
    
    /**
     * Get the color code for a given color name
     */
    public static String getColorCode(String colorName) {
        if (colorName == null) {
            return COLOR_NAME_TO_CODE.get(DEFAULT_COLORS.get("default"));
        }
        
        return COLOR_NAME_TO_CODE.getOrDefault(colorName.toLowerCase(), 
                COLOR_NAME_TO_CODE.get(DEFAULT_COLORS.get("default")));
    }

    /**
     * Get the Minecraft Formatting enum value for a color name
     */
    public static Formatting getColorFormatting(String colorName) {
        if (colorName == null) {
            return Formatting.GOLD; // Default
        }
        
        switch (colorName.toLowerCase()) {
            case "black": return Formatting.BLACK;
            case "dark_blue": return Formatting.DARK_BLUE;
            case "dark_green": return Formatting.DARK_GREEN;
            case "dark_aqua": return Formatting.DARK_AQUA;
            case "dark_red": return Formatting.DARK_RED;
            case "dark_purple": return Formatting.DARK_PURPLE;
            case "gold": return Formatting.GOLD;
            case "gray": return Formatting.GRAY;
            case "dark_gray": return Formatting.DARK_GRAY;
            case "blue": return Formatting.BLUE;
            case "green": return Formatting.GREEN;
            case "aqua": return Formatting.AQUA;
            case "red": return Formatting.RED;
            case "light_purple": return Formatting.LIGHT_PURPLE;
            case "yellow": return Formatting.YELLOW;
            case "white": return Formatting.WHITE;
            default: return Formatting.GOLD;
        }
    }
} 