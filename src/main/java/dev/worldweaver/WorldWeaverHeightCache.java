package dev.worldweaver;

import net.minecraft.world.level.levelgen.Heightmap;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Fixed-size, allocation-free cache for NoiseBasedChunkGenerator base-height queries.
 *
 * Each Heightmap type owns a direct-mapped region, so the full 64-bit packed X/Z key
 * can be compared exactly rather than relying on a probabilistic fingerprint. Each
 * slot uses a tiny sequence lock: readers never block, writers never allocate, and a
 * concurrent replacement can only turn a would-be hit into a miss, never a wrong
 * terrain height.
 *
 * A RandomState generation token invalidates the complete cache in O(1), avoiding a
 * large map clear and its GC spike when a generator is reused with another state.
 */
public final class WorldWeaverHeightCache {
    public static final int MISS = Integer.MIN_VALUE;

    private final AtomicLongArray keys;
    private final AtomicIntegerArray values;
    private final AtomicIntegerArray slotGenerations;
    private final AtomicIntegerArray sequences;
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
        this.slotGenerations = new AtomicIntegerArray(totalSlots);
        this.sequences = new AtomicIntegerArray(totalSlots);
    }

    public int get(int x, int z, Heightmap.Types type, int randomStateIdentity) {
        int stateGeneration = generationFor(randomStateIdentity);
        long expectedKey = packCoordinates(x, z);
        int index = indexFor(expectedKey, type);

        int firstSequence = sequences.get(index);
        if ((firstSequence & 1) != 0) {
            return MISS;
        }
        if (slotGenerations.get(index) != stateGeneration) {
            return MISS;
        }

        long observedKey = keys.get(index);
        if (observedKey != expectedKey) {
            return MISS;
        }
        int observedValue = values.get(index);

        int secondSequence = sequences.get(index);
        if (firstSequence != secondSequence || (secondSequence & 1) != 0) {
            return MISS;
        }
        if (slotGenerations.get(index) != stateGeneration) {
            return MISS;
        }

        return observedValue;
    }

    public void put(int x, int z, Heightmap.Types type, int randomStateIdentity, int value) {
        int stateGeneration = generationFor(randomStateIdentity);
        long key = packCoordinates(x, z);
        int index = indexFor(key, type);

        // A cache write is optional. If another worldgen worker owns this exact slot,
        // skip the write instead of spinning and adding contention to chunk generation.
        int sequence = sequences.get(index);
        if ((sequence & 1) != 0 || !sequences.compareAndSet(index, sequence, sequence + 1)) {
            return;
        }

        try {
            keys.set(index, key);
            values.set(index, value);
            slotGenerations.set(index, stateGeneration);
        } finally {
            // Publishing an even sequence makes the completed tuple visible to readers.
            sequences.set(index, sequence + 2);
        }
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
            // Zero is reserved for slots that have never been populated.
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
