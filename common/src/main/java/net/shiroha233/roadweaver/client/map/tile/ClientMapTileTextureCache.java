package net.shiroha233.roadweaver.client.map.tile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 瓦片纹理双层缓存。
 */
public final class ClientMapTileTextureCache {
    private ClientMapTileTextureCache() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final int MAX_VIEWPORT_TEXTURES = 32;
    private static final int MAX_BACKGROUND_TEXTURES = 64;

    private static final LinkedHashMap<String, TextureEntry> VIEWPORT_CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static final LinkedHashMap<String, TextureEntry> BACKGROUND_CACHE = new LinkedHashMap<>(128, 0.75f, true);

    public static synchronized ResourceLocation getOrLoad(Minecraft mc, Path path, boolean inViewport) {
        if (mc == null || path == null || !Files.exists(path)) {
            return null;
        }
        String key = path.toAbsolutePath().normalize().toString();
        long lastMod = lastModified(path);

        TextureEntry vpEntry = VIEWPORT_CACHE.get(key);
        if (vpEntry != null && vpEntry.lastModified == lastMod) {
            return vpEntry.location;
        }
        if (vpEntry != null) {
            release(mc, vpEntry.location);
            VIEWPORT_CACHE.remove(key);
        }

        TextureEntry bgEntry = BACKGROUND_CACHE.get(key);
        if (bgEntry != null && bgEntry.lastModified == lastMod) {
            if (inViewport) {
                BACKGROUND_CACHE.remove(key);
                VIEWPORT_CACHE.put(key, bgEntry);
                trimViewportCache(mc);
            }
            return bgEntry.location;
        }
        if (bgEntry != null) {
            release(mc, bgEntry.location);
            BACKGROUND_CACHE.remove(key);
        }

        return loadTexture(mc, path, key, lastMod, inViewport);
    }

    public static synchronized ResourceLocation getOrLoad(Minecraft mc, Path path) {
        return getOrLoad(mc, path, false);
    }

    public static synchronized void trimToViewport(Minecraft mc, Set<String> viewportKeys) {
        if (mc == null) return;
        Iterator<Map.Entry<String, TextureEntry>> it = BACKGROUND_CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TextureEntry> entry = it.next();
            if (!viewportKeys.contains(entry.getKey())) {
                release(mc, entry.getValue().location);
                it.remove();
            }
        }
    }

    public static synchronized void invalidate(Minecraft mc, Path path) {
        if (path == null) return;
        String key = path.toAbsolutePath().normalize().toString();
        TextureEntry vpEntry = VIEWPORT_CACHE.remove(key);
        if (vpEntry != null && mc != null) {
            release(mc, vpEntry.location);
        }
        TextureEntry bgEntry = BACKGROUND_CACHE.remove(key);
        if (bgEntry != null && mc != null) {
            release(mc, bgEntry.location);
        }
    }

    public static synchronized void clear(Minecraft mc) {
        if (mc != null) {
            for (TextureEntry entry : VIEWPORT_CACHE.values()) {
                release(mc, entry.location);
            }
            for (TextureEntry entry : BACKGROUND_CACHE.values()) {
                release(mc, entry.location);
            }
        }
        VIEWPORT_CACHE.clear();
        BACKGROUND_CACHE.clear();
    }

    private static ResourceLocation loadTexture(Minecraft mc, Path path, String key, long lastMod, boolean inViewport) {
        try (InputStream input = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(input);
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation location = mc.getTextureManager().register(
                    "roadweaver_map_tile/" + Integer.toHexString(key.hashCode()), texture);
            TextureEntry entry = new TextureEntry(location, lastMod);

            if (inViewport) {
                VIEWPORT_CACHE.put(key, entry);
                trimViewportCache(mc);
            } else {
                BACKGROUND_CACHE.put(key, entry);
                trimBackgroundCache(mc);
            }
            return location;
        } catch (IOException e) {
            LOGGER.warn("加载地图瓦片失败: {}", path, e);
            return null;
        }
    }

    private static void trimViewportCache(Minecraft mc) {
        while (VIEWPORT_CACHE.size() > MAX_VIEWPORT_TEXTURES) {
            Iterator<Map.Entry<String, TextureEntry>> it = VIEWPORT_CACHE.entrySet().iterator();
            if (it.hasNext()) {
                Map.Entry<String, TextureEntry> eldest = it.next();
                BACKGROUND_CACHE.put(eldest.getKey(), eldest.getValue());
                it.remove();
                trimBackgroundCache(mc);
            }
        }
    }

    private static void trimBackgroundCache(Minecraft mc) {
        while (BACKGROUND_CACHE.size() > MAX_BACKGROUND_TEXTURES) {
            Iterator<Map.Entry<String, TextureEntry>> it = BACKGROUND_CACHE.entrySet().iterator();
            if (it.hasNext()) {
                Map.Entry<String, TextureEntry> eldest = it.next();
                release(mc, eldest.getValue().location);
                it.remove();
            }
        }
    }

    private static void release(Minecraft mc, ResourceLocation location) {
        if (mc == null || location == null) return;
        mc.getTextureManager().release(location);
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private record TextureEntry(ResourceLocation location, long lastModified) {}
}