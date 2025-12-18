package net.shiroha233.roadweaver.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.architectury.platform.Platform
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * 配置读写服务
 *
 * 说明：
 * - INSTANCE 使用 volatile 等价语义（@Volatile），读取无锁
 * - load/save 使用 synchronized 保证写入互斥
 */
object ConfigService {
    private val LOGGER: Logger = LoggerFactory.getLogger("roadweaver")
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private const val BASE_DIR: String = "roadweaver"
    private const val FILE_NAME: String = "roadweaver.json"

    // 使用 @Volatile 确保多线程可见性，但读取不需要同步锁
    @Volatile
    private var INSTANCE: ModConfig = ModConfig()

    @JvmStatic
    @Synchronized
    fun load() {
        val cfgRoot: Path = Platform.getConfigFolder()
        val baseDir: Path = cfgRoot.resolve(BASE_DIR)
        val file: Path = baseDir.resolve(FILE_NAME)

        try {
            Files.createDirectories(baseDir)
        } catch (e: Exception) {
            LOGGER.warn("Failed to create config directory: {}", baseDir, e)
        }

        if (Files.exists(file)) {
            try {
                Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader: BufferedReader ->
                    val loaded: ModConfig? = GSON.fromJson(reader, ModConfig::class.java)
                    if (loaded != null) {
                        INSTANCE = loaded
                    }
                }
            } catch (e: Exception) {
                LOGGER.warn("Failed to read config, using defaults. File: {}", file, e)
            }
        } else {
            save()
        }

        // 确保默认值和新字段被填充
        try {
            INSTANCE.sanitize()
        } catch (t: Throwable) {
            LOGGER.warn("Failed to sanitize config; continuing with raw values.", t)
        }

        LOGGER.info(
            "Configuration loaded (radiusChunks={}, enabled={})",
            INSTANCE.predictRadiusChunks(),
            INSTANCE.villagePredictionEnabled()
        )
    }

    @JvmStatic
    @Synchronized
    fun save() {
        val cfgRoot: Path = Platform.getConfigFolder()
        val baseDir: Path = cfgRoot.resolve(BASE_DIR)
        val file: Path = baseDir.resolve(FILE_NAME)

        try {
            Files.createDirectories(baseDir)
        } catch (e: Exception) {
            LOGGER.warn("Failed to create config directory: {}", baseDir, e)
        }

        try {
            Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { writer: BufferedWriter ->
                GSON.toJson(INSTANCE, writer)
            }
        } catch (e: Exception) {
            LOGGER.warn("Failed to write config file: {}", file, e)
        }
    }

    /**
     * 获取配置实例（无锁读取）。
     * 由于 INSTANCE 是 volatile 的，读取是线程安全的。
     * 配置修改通过 load()/save() 完成，它们是 synchronized 的。
     */
    @JvmStatic
    fun get(): ModConfig = INSTANCE
}
