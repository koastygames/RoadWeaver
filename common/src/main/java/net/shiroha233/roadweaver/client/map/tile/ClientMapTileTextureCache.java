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

/**
 * 本地 PNG 瓦片到动态纹理的缓存。
 */
public final class ClientMapTileTextureCache {
    private ClientMapTileTextureCache() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final int MAX_TEXTURES = 96;
    private static final LinkedHashMap<String, TextureEntry> CACHE = new LinkedHashMap<>(128, 0.75f, true);

    public static synchronized ResourceLocation getOrLoad(Minecraft mc, Path path) {
        if (mc == null || path == null || !Files.exists(path)) {
            return null;
        }
        String key = path.toAbsolutePath().normalize().toString();
        long lastModified = lastModified(path);
        TextureEntry existing = CACHE.get(key);
        if (existing != null && existing.lastModified == lastModified) {
            return existing.location;
        }
        if (existing != null) {
            release(mc, existing.location);
            CACHE.remove(key);
        }
        try (InputStream input = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(input);
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation location = mc.getTextureManager().register("roadweaver_map_tile/" + Integer.toHexString(key.hashCode()), texture);
            CACHE.put(key, new TextureEntry(location, lastModified));
            trimToLimit(mc);
            return location;
        } catch (IOException e) {
            LOGGER.warn("加载地图瓦片失败: {}", path, e);
            return null;
        }
    }

    public static synchronized void clear(Minecraft mc) {
        if (mc != null) {
            for (TextureEntry entry : CACHE.values()) {
                release(mc, entry.location);
            }
        }
        CACHE.clear();
    }

    private static void trimToLimit(Minecraft mc) {
        Iterator<Map.Entry<String, TextureEntry>> iterator = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_TEXTURES && iterator.hasNext()) {
            Map.Entry<String, TextureEntry> eldest = iterator.next();
            release(mc, eldest.getValue().location);
            iterator.remove();
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