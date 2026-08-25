package dev.worldweaver;

import net.minecraft.world.level.levelgen.Heightmap;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Fixed-size, allocation-free cache for NoiseBasedChunkGenerator base-height queries.
 *
 * Each Heightmap type owns a direct-mapped region, so the full 64-bit packed X/Z key
 * can be compared exactly rather than relying on a probabilistic fingerprint. Reads
 * verify the slot twice so concurrent replacement can only turn a would-be hit into
 * a miss; it cannot return a height belonging to another coordinate.
 *
 * A generation stamp is advanced whenever the RandomState identity changes. That
 * invalidates the complete cache in O(1), avoiding a large map clear and its GC spike.
 */
public final class WorldWeaverHeightCache {
    public static final int MISS = Integer.MIN_VALUE;

    private final AtomicLongArray keys;
    private final AtomicIntegerArray values;
    private final AtomicIntegerArray stamps;
    private final int slotsPerType;
    private final int slotMask;
    private final AtomicLong stateToken = new AtomicLong(packState(Integer.MIN_VALUE, 1));

    public WorldWeaverHeightCache(int requestedTotalEntries) {
        int typeCount = Math.max(1, Heightmap.Types.values().length);
        int requestedPerType = Math.max(128, requestedTotalEntries / typeCount);
        this.slotsPerType = Math.max(128, Integer.highestOneBit(requestedPerType));
        this.slotMask = this.slotsPerType - 1;
        int totalSlots = Math.multiplyExact(this.slotsPerType, typeCount);
        this.keys = new AtomicLongArray(totalSlots);
        this.values = new AtomicIntegerArray(totalSlots);
        this.stamps = new AtomicIntegerArray(totalSlots);
    }

    public int get(int x, int z, Heightmap.Types type, int randomStateIdentity) {
        int generation = generationFor(randomStateIdentity);
        long key = packCoordinates(x, z);
        int index = indexFor(key, type);

        int firstStamp = stamps.get(index);
        if (firstStamp != generation) {
            return MISS;
        }

        long firstKey = keys.get(index);
        if (firstKey != key) {
            return MISS;
        }

        int value = values.get(index);
        int secondStamp = stamps.get(index);
        long secondKey = keys.get(index);

        if (secondStamp == firstStamp && secondStamp == generation && secondKey == firstKey) {
            return value;
        }
        return MISS;
    }

    public void put(int x, int z, Heightmap.Types type, int randomStateIdentity, int value) {
        int generation = generationFor(randomStateIdentity);
        long key = packCoordinates(x, z);
        int index = indexFor(key, type);

        // Publish payload first and the generation stamp last. Readers re-check both
        // key and stamp, so racing replacement safely degrades to a cache miss.
        values.set(index, value);
        keys.set(index, key);
        stamps.set(index, generation);
    }

    public int capacity() {
        return keys.length();
    }

    private int generationFor(int randomStateIdentity) {
        while (true) {
            long current = stateToken.get();
            int currentIdentity = (int) (current >> 32);
            int currentGeneration = (int) current;
            if (currentIdentity == randomStateIdentity) {
                return currentGeneration;
            }

            int nextGeneration = currentGeneration + 1;
            // Generation zero is reserved for never-written slots. A wrap would take
            // billions of RandomState swaps; skip zero to retain that invariant.
            if (nextGeneration == 0) {
                nextGeneration = 1;
            }
            long next = packState(randomStateIdentity, nextGeneration);
            if (stateToken.compareAndSet(current, next)) {
                return nextGeneration;
            }
        }
    }

    private int indexFor(long key, Heightmap.Types type) {
        long mixed = key;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53l;
        mixed ^= mixed >>> 33;

        int slot = ((int) mixed) & slotMask;
        return type.ordinal() * slotsPerType + slot;
    }

    private static long packCoordinates(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static long packState(int identity, int generation) {
        return ((long) identity << 32) | (generation & 0xffffffffL);
    }
}
