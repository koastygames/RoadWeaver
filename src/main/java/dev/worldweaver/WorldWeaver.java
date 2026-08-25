package dev.worldweaver;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(WorldWeaver.MOD_ID)
public final class WorldWeaver {
    public static final String MOD_ID = "worldweaver";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WorldWeaver() {
        LOGGER.info("WorldWeaver 1.0.0 enabled: compatibility-first terrain pipeline optimizations are active.");
        LOGGER.info("Height cache: {} (max {} entries per vanilla noise generator)",
                WorldWeaverSettings.HEIGHT_CACHE_ENABLED,
                WorldWeaverSettings.HEIGHT_CACHE_MAX_ENTRIES);
    }
}
