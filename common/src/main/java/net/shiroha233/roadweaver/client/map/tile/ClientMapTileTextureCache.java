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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;

/**
 * 瓦片纹理双层缓存。
 *
 * 性能优化：
 * 1. 增大缓存容量以支持全精度渲染（数百个 chunk 纹片）
 * 2. 异步预加载纹理，避免主线程阻塞
 * 3. 减少 lastModified 检查频率（缓存命中时不检查）
 */
public final class ClientMapTileTextureCache {
    private ClientMapTileTextureCache() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    // 全精度模式下视口内可能需要几百个 chunk 纹理，增大容量
    private static final int MAX_VIEWPORT_TEXTURES = 256;
    private static final int MAX_BACKGROUND_TEXTURES = 512;

    private static final LinkedHashMap<String, TextureEntry> VIEWPORT_CACHE = new LinkedHashMap<>(512, 0.75f, true);
    private static final LinkedHashMap<String, TextureEntry> BACKGROUND_CACHE = new LinkedHashMap<>(1024, 0.75f, true);

    // 异步加载线程池
    private static final ExecutorService LOAD_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "RW-TileLoader");
        t.setDaemon(true);
        return t;
    });

    // 正在加载的纹理（防止重复加载）
    private static final ConcurrentHashMap<String, CompletableFuture<ResourceLocation>> PENDING_LOADS = new ConcurrentHashMap<>();

    public static synchronized ResourceLocation getOrLoad(Minecraft mc, Path path, boolean inViewport) {
        if (mc == null || path == null || !Files.exists(path)) {
            return null;
        }
        String key = path.toAbsolutePath().normalize().toString();

        // 1. 检查视口缓存（缓存命中时不检查 lastModified，避免每帧磁盘 I/O）
        TextureEntry vpEntry = VIEWPORT_CACHE.get(key);
        if (vpEntry != null) {
            if (inViewport) return vpEntry.location;
            // 从视口移到后台
            VIEWPORT_CACHE.remove(key);
            BACKGROUND_CACHE.put(key, vpEntry);
            trimBackgroundCache(mc);
            return vpEntry.location;
        }

        // 2. 检查后台缓存
        TextureEntry bgEntry = BACKGROUND_CACHE.get(key);
        if (bgEntry != null) {
            if (inViewport) {
                BACKGROUND_CACHE.remove(key);
                VIEWPORT_CACHE.put(key, bgEntry);
                trimViewportCache(mc);
            }
            return bgEntry.location;
        }

        // 3. 检查是否有正在异步加载的
        CompletableFuture<ResourceLocation> pending = PENDING_LOADS.get(key);
        if (pending != null) {
            if (pending.isDone()) {
                PENDING_LOADS.remove(key);
                try {
                    ResourceLocation loc = pending.getNow(null);
                    if (loc != null) return loc;
                } catch (Exception ignored) {}
            }
            // 还在加载中，本帧跳过
            return null;
        }

        // 4. 同步加载（首次必须同步，否则该帧无纹理可渲染）
        long lastMod = lastModified(path);
        return loadTexture(mc, path, key, lastMod, inViewport);
    }

    public static synchronized ResourceLocation getOrLoad(Minecraft mc, Path path) {
        return getOrLoad(mc, path, false);
    }

    /**
     * 异步预加载纹理到后台缓存。不阻塞渲染线程。
     * 下次 getOrLoad 时可直接命中缓存。
     */
    public static void preloadAsync(Minecraft mc, Path path) {
        if (mc == null || path == null || !Files.exists(path)) return;
        String key = path.toAbsolutePath().normalize().toString();

        synchronized (ClientMapTileTextureCache.class) {
            if (VIEWPORT_CACHE.containsKey(key) || BACKGROUND_CACHE.containsKey(key) || PENDING_LOADS.containsKey(key)) {
                return; // 已缓存或正在加载
            }
        }

        CompletableFuture<ResourceLocation> future = CompletableFuture.supplyAsync(() -> {
            try (InputStream input = Files.newInputStream(path)) {
                NativeImage image = NativeImage.read(input);
                DynamicTexture texture = new DynamicTexture(image);
                ResourceLocation location = mc.getTextureManager().register(
                        "roadweaver_map_tile/" + Integer.toHexString(key.hashCode()), texture);
                TextureEntry entry = new TextureEntry(location, lastModified(path));

                synchronized (ClientMapTileTextureCache.class) {
                    BACKGROUND_CACHE.put(key, entry);
                    trimBackgroundCache(mc);
                }
                return location;
            } catch (IOException e) {
                LOGGER.warn("异步加载地图瓦片失败: {}", path, e);
                return null;
            }
        }, LOAD_EXECUTOR);

        PENDING_LOADS.put(key, future);
        future.whenComplete((loc, ex) -> PENDING_LOADS.remove(key));
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