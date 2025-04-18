package net.infiniteimperm.fabric.tagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class LilBitchConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(TaggerMod.MOD_ID);
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("lilbitch_config.json");
    
    private static LilBitchConfig instance = null;
    
    // Detection settings
    private double detectionRadius = 5.0;
    
    // Player tag settings
    private boolean dodgeUntaggedPlayers = true;
    private boolean dodgePrivateStats = true;
    private boolean dodgeRunnerTag = true;
    private boolean dodgeCreatureTag = true;
    private boolean dodgeCompetentTag = true;
    private boolean dodgeSkilledTag = true;
    
    // Winrate settings
    private boolean dodgeHighWinrate = true;
    private double highWinrateThreshold = 68.0;
    private int highWinrateMinWins = 5;
    
    // Perfect winrate settings
    private boolean dodgePerfectWinrate = true;
    private int perfectWinrateMaxWins = 5;
    
    // Winstreak settings
    private boolean dodgeHighWinstreak = true;
    private int highWinstreakThreshold = 20;
    
    // Score calculation settings
    private boolean enableScoreCalculation = true;
    private double scoreDivisor = 50.0;
    private double scoreExponent = 2.0;
    
    // Default constructor with default values
    public LilBitchConfig() {
        // Default values are already set in field declarations
    }
    
    /**
     * Load configuration from file, or create default if not exists
     */
    public static void loadConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (Files.exists(CONFIG_FILE)) {
                Reader reader = Files.newBufferedReader(CONFIG_FILE);
                instance = GSON.fromJson(reader, LilBitchConfig.class);
                reader.close();
                if (instance == null) {
                    TaggerMod.LOGGER.warn("lilbitch_config.json was loaded as null, initializing with defaults");
                    instance = new LilBitchConfig();
                }
                TaggerMod.LOGGER.info("Loaded LilBitch config from file");
            } else {
                instance = new LilBitchConfig();
                TaggerMod.LOGGER.info("Created default LilBitch config");
                saveConfig(); // Create the file with defaults
            }
        } catch (Exception e) {
            TaggerMod.LOGGER.error("Failed to load LilBitch config", e);
            instance = new LilBitchConfig(); // Use defaults on error
        }
    }
    
    /**
     * Save configuration to file
     */
    public static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Writer writer = Files.newBufferedWriter(CONFIG_FILE);
            GSON.toJson(instance, writer);
            writer.close();
            TaggerMod.LOGGER.info("Saved LilBitch config to file");
        } catch (IOException e) {
            TaggerMod.LOGGER.error("Failed to save LilBitch config", e);
        }
    }
    
    /**
     * Get the singleton instance of the config
     */
    public static LilBitchConfig getInstance() {
        if (instance == null) {
            loadConfig();
        }
        return instance;
    }
    
    // Getters and setters for all configuration options
    
    public double getDetectionRadius() {
        return detectionRadius;
    }
    
    public void setDetectionRadius(double detectionRadius) {
        this.detectionRadius = detectionRadius;
        saveConfig();
    }
    
    public boolean isDodgeUntaggedPlayers() {
        return dodgeUntaggedPlayers;
    }
    
    public void setDodgeUntaggedPlayers(boolean dodgeUntaggedPlayers) {
        this.dodgeUntaggedPlayers = dodgeUntaggedPlayers;
        saveConfig();
    }
    
    public boolean isDodgePrivateStats() {
        return dodgePrivateStats;
    }
    
    public void setDodgePrivateStats(boolean dodgePrivateStats) {
        this.dodgePrivateStats = dodgePrivateStats;
        saveConfig();
    }
    
    public boolean isDodgeRunnerTag() {
        return dodgeRunnerTag;
    }
    
    public void setDodgeRunnerTag(boolean dodgeRunnerTag) {
        this.dodgeRunnerTag = dodgeRunnerTag;
        saveConfig();
    }
    
    public boolean isDodgeCreatureTag() {
        return dodgeCreatureTag;
    }
    
    public void setDodgeCreatureTag(boolean dodgeCreatureTag) {
        this.dodgeCreatureTag = dodgeCreatureTag;
        saveConfig();
    }
    
    public boolean isDodgeCompetentTag() {
        return dodgeCompetentTag;
    }
    
    public void setDodgeCompetentTag(boolean dodgeCompetentTag) {
        this.dodgeCompetentTag = dodgeCompetentTag;
        saveConfig();
    }
    
    public boolean isDodgeSkilledTag() {
        return dodgeSkilledTag;
    }
    
    public void setDodgeSkilledTag(boolean dodgeSkilledTag) {
        this.dodgeSkilledTag = dodgeSkilledTag;
        saveConfig();
    }
    
    public boolean isDodgeHighWinrate() {
        return dodgeHighWinrate;
    }
    
    public void setDodgeHighWinrate(boolean dodgeHighWinrate) {
        this.dodgeHighWinrate = dodgeHighWinrate;
        saveConfig();
    }
    
    public double getHighWinrateThreshold() {
        return highWinrateThreshold;
    }
    
    public void setHighWinrateThreshold(double highWinrateThreshold) {
        this.highWinrateThreshold = highWinrateThreshold;
        saveConfig();
    }
    
    public int getHighWinrateMinWins() {
        return highWinrateMinWins;
    }
    
    public void setHighWinrateMinWins(int highWinrateMinWins) {
        this.highWinrateMinWins = highWinrateMinWins;
        saveConfig();
    }
    
    public boolean isDodgePerfectWinrate() {
        return dodgePerfectWinrate;
    }
    
    public void setDodgePerfectWinrate(boolean dodgePerfectWinrate) {
        this.dodgePerfectWinrate = dodgePerfectWinrate;
        saveConfig();
    }
    
    public int getPerfectWinrateMaxWins() {
        return perfectWinrateMaxWins;
    }
    
    public void setPerfectWinrateMaxWins(int perfectWinrateMaxWins) {
        this.perfectWinrateMaxWins = perfectWinrateMaxWins;
        saveConfig();
    }
    
    public boolean isDodgeHighWinstreak() {
        return dodgeHighWinstreak;
    }
    
    public void setDodgeHighWinstreak(boolean dodgeHighWinstreak) {
        this.dodgeHighWinstreak = dodgeHighWinstreak;
        saveConfig();
    }
    
    public int getHighWinstreakThreshold() {
        return highWinstreakThreshold;
    }
    
    public void setHighWinstreakThreshold(int highWinstreakThreshold) {
        this.highWinstreakThreshold = highWinstreakThreshold;
        saveConfig();
    }
    
    public boolean isEnableScoreCalculation() {
        return enableScoreCalculation;
    }
    
    public void setEnableScoreCalculation(boolean enableScoreCalculation) {
        this.enableScoreCalculation = enableScoreCalculation;
        saveConfig();
    }
    
    public double getScoreDivisor() {
        return scoreDivisor;
    }
    
    public void setScoreDivisor(double scoreDivisor) {
        this.scoreDivisor = scoreDivisor;
        saveConfig();
    }
    
    public double getScoreExponent() {
        return scoreExponent;
    }
    
    public void setScoreExponent(double scoreExponent) {
        this.scoreExponent = scoreExponent;
        saveConfig();
    }
} 