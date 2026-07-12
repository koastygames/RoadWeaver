package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 旧 H2 读取入口，只供显式导入流程使用。
 */
final class RoadDatabaseManager {
    private RoadDatabaseManager() {}

    static final String DB_ROAD = "roads";
    static final String DB_TERRAIN = "terrain";
    static final String DB_MAP = "map";

    private static final String DB_DIR = "data/roadweaver";
    private static volatile boolean driverRegistered;

    static Connection openReadOnlyConnection(ServerLevel level, String dbName) throws SQLException {
        try {
            ensureDriverRegistered();
            Path dbPath = getDbPath(level, dbName);
            Files.createDirectories(dbPath.getParent());
            String url = "jdbc:h2:" + dbPath.toAbsolutePath()
                    + ";MODE=LEGACY"
                    + ";FILE_LOCK=NO"
                    + ";AUTO_SERVER=FALSE"
                    + ";CACHE_SIZE=8192"
                    + ";LOCK_MODE=0"
                    + ";ACCESS_MODE_DATA=r";

            Connection conn = DriverManager.getConnection(url, "sa", "");
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET READONLY TRUE");
            } catch (SQLException ignored) {
            }
            return conn;
        } catch (Exception e) {
            throw new SQLException("Failed to create legacy H2 connection: " + dbName, e);
        }
    }

    static boolean hasAnyLegacyDatabase(ServerLevel level) {
        return hasLegacyDatabase(level, DB_ROAD)
                || hasLegacyDatabase(level, DB_TERRAIN)
                || hasLegacyDatabase(level, DB_MAP);
    }

    static boolean hasLegacyDatabase(ServerLevel level, String dbName) {
        if (level == null || dbName == null || dbName.isBlank()) return false;
        Path dbPath = getDbPath(level, dbName);
        return Files.exists(dbFile(dbPath, ".mv.db")) || Files.exists(dbFile(dbPath, ".h2.db"));
    }

    static void requireDriverAvailable() throws SQLException {
        ensureDriverRegistered();
    }

    static String dimKey(ServerLevel level) {
        ResourceLocation rl = level.dimension().location();
        return rl.getNamespace() + "_" + rl.getPath();
    }

    private static void ensureDriverRegistered() throws SQLException {
        if (driverRegistered) return;
        synchronized (RoadDatabaseManager.class) {
            if (driverRegistered) return;
            SQLException failure = null;
            for (String className : ListHolder.H2_DRIVER_CLASSES) {
                try {
                    Class<?> type = Class.forName(className);
                    Driver driver = (Driver) type.getDeclaredConstructor().newInstance();
                    DriverManager.registerDriver(driver);
                    driverRegistered = true;
                    return;
                } catch (Throwable t) {
                    if (failure == null) {
                        failure = new SQLException("H2 driver is not available");
                    }
                    failure.addSuppressed(t);
                }
            }
            throw failure == null ? new SQLException("H2 driver is not available") : failure;
        }
    }

    private static Path dbFile(Path dbPath, String suffix) {
        return dbPath.resolveSibling(dbPath.getFileName() + suffix);
    }

    private static Path getDbPath(ServerLevel level, String dbName) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve(DB_DIR).resolve(dimKey(level)).resolve(dbName);
    }

    private static final class ListHolder {
        private static final String[] H2_DRIVER_CLASSES = {
                "org.h2.Driver",
                "net.shiroha233.roadweaver.libs.h2.Driver"
        };
    }
}
