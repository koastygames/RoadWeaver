package net.shiroha233.roadweaver.map.permission.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.shiroha233.roadweaver.map.permission.MapAccessPolicy;

public final class NeoForgeMapAccessSavedData extends SavedData {
    private static final String DATA_NAME = "roadweaver_map_access";
    private static final String KEY_POLICY = "policy";
    private static final SavedDataType<NeoForgeMapAccessSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            level -> new NeoForgeMapAccessSavedData(),
            NeoForgeMapAccessSavedData::makeCodec
    );
    private static final Codec<NeoForgeMapAccessSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MapAccessPolicy.CODEC.optionalFieldOf(KEY_POLICY, MapAccessPolicy.DEFAULT).forGetter(NeoForgeMapAccessSavedData::getPolicy)
    ).apply(instance, NeoForgeMapAccessSavedData::new));

    private MapAccessPolicy policy;

    public NeoForgeMapAccessSavedData() {
        this(MapAccessPolicy.DEFAULT);
    }

    private NeoForgeMapAccessSavedData(MapAccessPolicy policy) {
        this.policy = policy != null ? policy : MapAccessPolicy.DEFAULT;
    }

    private static Codec<NeoForgeMapAccessSavedData> makeCodec(ServerLevel level) {
        return CODEC;
    }

    public static NeoForgeMapAccessSavedData get(MinecraftServer server) {
        ServerLevel level = server != null ? server.getLevel(Level.OVERWORLD) : null;
        if (level == null) {
            return new NeoForgeMapAccessSavedData();
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public MapAccessPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(MapAccessPolicy policy) {
        this.policy = policy != null ? policy : MapAccessPolicy.DEFAULT;
        setDirty();
    }
}
