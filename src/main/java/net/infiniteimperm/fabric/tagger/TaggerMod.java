package net.infiniteimperm.fabric.tagger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TaggerMod implements ClientModInitializer {
    public static final String MOD_ID = "tagger";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("Tagger Client Mod initialized");
        
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("tag")
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal("§6Tagger Mod §7- Use §e/tag help §7for more information"));
                    return 1;
                })
                .then(ClientCommandManager.literal("help")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal("§6Tagger Mod Commands:\n" +
                            "§e/tag §7- Shows this help message\n" +
                            "§e/tag help §7- Shows this help message"));
                        return 1;
                    })
                )
            );
        });
    }
} 