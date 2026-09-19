package dev.thathunky.trodden;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Per-block step counters. They live in memory, keyed by chunk, and go to disk in the chunk's PDC
 * (an array of triples: "packed position, counter, day of the last step") — on chunk unload, every
 * few minutes, and on shutdown.
 */
final class WearStore {

    /** Counter for one block: steps taken on it and the day of the last step (days since epoch). */
    record Entry(int count, int day) {
    }

    /** Counter value marking a dirt path this plugin turned into mud; it dries back to a path. */
    static final int MUD_MARK = -1;

    /** Material name for mud, kept in one place so it is not a scattered string literal. */
    static final String MUD = "MUD";

    /**
     * The plugin name this jar shipped under before the Trodden rename, and the two PDC keys
     * players' chunks may still carry under it. Trodden reads these as a fallback so renaming the
     * plugin (which changes the PDC namespace from {@code matsuriwear} to {@code trodden}) does not
     * orphan every path already walked into the world.
     */
    private static final String LEGACY_NAMESPACE = "matsuriwear";
    private static final String LEGACY_KEY2 = "counts2";
    private static final String LEGACY_KEY = "counts";

    private final JavaPlugin plugin;
    private final NamespacedKey key;
    private final NamespacedKey legacyKey2;
    private final NamespacedKey legacyKey;
    private final Map<String, Map<Long, Map<Integer, Entry>>> worlds = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> dirty = new ConcurrentHashMap<>();

    WearStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "counts2");
        // Built from the fixed pre-rename namespace, not this plugin's own name, so the legacy PDC
        // entries stay findable no matter what the jar is renamed to next — NamespacedKey.fromString
        // (not the deprecated two-string constructor) can return null for a malformed string, but
        // these two are string literals under our control, so that null is only a defensive guard.
        this.legacyKey2 = NamespacedKey.fromString(LEGACY_NAMESPACE + ":" + LEGACY_KEY2);
        this.legacyKey = NamespacedKey.fromString(LEGACY_NAMESPACE + ":" + LEGACY_KEY);
    }

    /**
     * The in-memory map for a chunk, decoded from the chunk's PDC the first time it is touched.
     *
     * <p>{@code retainEmpty} decides what happens to a chunk that turns out to have nothing
     * recorded in it. Writes pass {@code true}; every read passes {@code false}, and then the
     * empty map is handed back without being kept. Merely asking about a chunk — which every
     * chunk load does, through the regrowth catch-up — must never make the store start tracking
     * it, or {@link #trackedChunks} would grow to "every loaded chunk" and the regrowth sweep
     * would schedule a region task per loaded chunk with nothing to do in it.
     *
     * <p>Reads try, in order: this plugin's own current key ({@code trodden:counts2}), then the
     * pre-rename triples ({@code matsuriwear:counts2}), then the oldest pre-rename pairs
     * ({@code matsuriwear:counts}) — see {@link #resolve}. Whichever legacy key supplied the data
     * is removed and the chunk is marked dirty, exactly as the {@code counts} migration always did,
     * so the chunk gets rewritten under the current key next flush.
     */
    private Map<Integer, Entry> chunkMap(Chunk chunk, boolean retainEmpty) {
        Map<Long, Map<Integer, Entry>> byChunk = worlds.computeIfAbsent(chunk.getWorld().getName(), w -> new ConcurrentHashMap<>());
        long chunkKey = chunk.getChunkKey();
        Map<Integer, Entry> existing = byChunk.get(chunkKey);
        if (existing != null) {
            return existing;
        }
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        int[] fresh = pdc.get(key, PersistentDataType.INTEGER_ARRAY);
        int[] legacyTriples = fresh != null || legacyKey2 == null ? null
                : pdc.get(legacyKey2, PersistentDataType.INTEGER_ARRAY);
        int[] legacyPairs = fresh != null || legacyTriples != null || legacyKey == null ? null
                : pdc.get(legacyKey, PersistentDataType.INTEGER_ARRAY);
        if (fresh == null && legacyTriples == null && legacyPairs == null) {
            Map<Integer, Entry> empty = new ConcurrentHashMap<>();
            return retainEmpty ? retain(byChunk, chunkKey, empty) : empty;
        }
        Map<Integer, Entry> resolved = resolve(fresh, legacyTriples, legacyPairs, Days.today());
        Map<Integer, Entry> migrated = retain(byChunk, chunkKey, new ConcurrentHashMap<>(resolved));
        if (fresh == null) {
            // Came from one of the legacy keys — drop it, so this chunk has to be written out again
            // whatever the caller was doing; it is always retained, read or write.
            pdc.remove(legacyTriples != null ? legacyKey2 : legacyKey);
            markDirty(chunk);
        }
        return migrated;
    }

    /**
     * Pure resolution of the read-order priority described on {@link #chunkMap}: the current key's
     * triples win when present, then the legacy {@code matsuriwear:counts2} triples, then the oldest
     * {@code matsuriwear:counts} pairs (which get {@code today} stamped on, as they always have).
     * Extracted from {@link #chunkMap} so the ordering itself can be asserted in tests without a
     * live {@link Chunk}.
     */
    static Map<Integer, Entry> resolve(int[] currentTriples, int[] legacyTriples, int[] legacyPairs, int today) {
        if (currentTriples != null) {
            return decode(currentTriples);
        }
        if (legacyTriples != null) {
            return decode(legacyTriples);
        }
        if (legacyPairs != null) {
            return decodeLegacy(legacyPairs, today);
        }
        return Map.of();
    }

    /** Puts the freshly decoded map in, or returns the one another thread got in with first. */
    private static Map<Integer, Entry> retain(Map<Long, Map<Integer, Entry>> byChunk, long chunkKey,
            Map<Integer, Entry> map) {
        Map<Integer, Entry> prior = byChunk.putIfAbsent(chunkKey, map);
        return prior != null ? prior : map;
    }

    Entry get(Chunk chunk, int packed) {
        return chunkMap(chunk, false).get(packed);
    }

    void put(Chunk chunk, int packed, int count, int day) {
        chunkMap(chunk, true).put(packed, new Entry(count, day));
        markDirty(chunk);
    }

    void remove(Chunk chunk, int packed) {
        if (chunkMap(chunk, false).remove(packed) != null) {
            markDirty(chunk);
        }
    }

    Map<Integer, Entry> entries(Chunk chunk) {
        return Collections.unmodifiableMap(chunkMap(chunk, false));
    }

    /**
     * Chunk keys (as in {@link Chunk#getChunkKey()}) this store actually has counters for, in this
     * world. Chunks whose counters have all been removed are left out too: the regrowth sweep uses
     * this list, and a chunk with no entries has nothing for it to do.
     */
    Set<Long> trackedChunks(World world) {
        Map<Long, Map<Integer, Entry>> byChunk = worlds.get(world.getName());
        if (byChunk == null) {
            return Set.of();
        }
        Set<Long> tracked = new HashSet<>();
        for (Map.Entry<Long, Map<Integer, Entry>> e : byChunk.entrySet()) {
            if (!e.getValue().isEmpty()) {
                tracked.add(e.getKey());
            }
        }
        return tracked;
    }

    private void markDirty(Chunk chunk) {
        dirty.computeIfAbsent(chunk.getWorld().getName(), w -> ConcurrentHashMap.newKeySet()).add(chunk.getChunkKey());
    }

    /** Write the chunk out and, if it is unloading, forget it from memory. */
    void flush(Chunk chunk, boolean forget) {
        Map<Long, Map<Integer, Entry>> byChunk = worlds.get(chunk.getWorld().getName());
        if (byChunk == null) {
            return;
        }
        Map<Integer, Entry> m = byChunk.get(chunk.getChunkKey());
        Set<Long> d = dirty.get(chunk.getWorld().getName());
        if (m != null && d != null && d.remove(chunk.getChunkKey())) {
            PersistentDataContainer pdc = chunk.getPersistentDataContainer();
            if (m.isEmpty()) {
                pdc.remove(key);
            } else {
                pdc.set(key, PersistentDataType.INTEGER_ARRAY, encode(m));
            }
        }
        if (forget) {
            byChunk.remove(chunk.getChunkKey());
        }
    }

    /**
     * Async flush: schedule region-thread writes for all dirty chunks. Used for periodic saves
     * (every few minutes). A chunk may unload between scheduling and execution, so re-check
     * isChunkLoaded inside the scheduled task to avoid force-loading.
     */
    void flushAll(Iterable<World> loaded) {
        for (World w : loaded) {
            Set<Long> d = dirty.get(w.getName());
            if (d == null) {
                continue;
            }
            for (long k : Set.copyOf(d)) {
                int cx = (int) k;
                int cz = (int) (k >> 32);
                if (w.isChunkLoaded(cx, cz)) {
                    Sched.atChunk(plugin, w, cx, cz, () -> {
                        if (w.isChunkLoaded(cx, cz)) {
                            flush(w.getChunkAt(cx, cz), false);
                        }
                    });
                }
            }
        }
    }

    /**
     * Sync flush: write all dirty chunks directly, on this thread. Must not use the scheduler.
     * Used only at shutdown: tasks scheduled while disabling may never run, causing silent data
     * loss. On Folia, off-region PDC writes throw IllegalStateException; we log the warning.
     */
    void flushAllNow(Iterable<World> loaded) {
        for (World w : loaded) {
            Set<Long> d = dirty.get(w.getName());
            if (d == null) {
                continue;
            }
            for (long k : Set.copyOf(d)) {
                int cx = (int) k;
                int cz = (int) (k >> 32);
                if (w.isChunkLoaded(cx, cz)) {
                    try {
                        flush(w.getChunkAt(cx, cz), false);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to flush wear data for chunk [" + cx + ", " + cz + "] in " + w.getName() + ": " + e);
                    }
                }
            }
        }
    }

    static int[] encode(Map<Integer, Entry> m) {
        int[] out = new int[m.size() * 3];
        int i = 0;
        for (Map.Entry<Integer, Entry> e : m.entrySet()) {
            out[i++] = e.getKey();
            out[i++] = e.getValue().count();
            out[i++] = e.getValue().day();
        }
        return out;
    }

    static Map<Integer, Entry> decode(int[] arr) {
        Map<Integer, Entry> m = new HashMap<>();
        if (arr != null) {
            for (int i = 0; i + 2 < arr.length; i += 3) {
                m.put(arr[i], new Entry(arr[i + 1], arr[i + 2]));
            }
        }
        return m;
    }

    static Map<Integer, Entry> decodeLegacy(int[] arr, int today) {
        Map<Integer, Entry> m = new HashMap<>();
        if (arr != null) {
            for (int i = 0; i + 1 < arr.length; i += 2) {
                m.put(arr[i], new Entry(arr[i + 1], today));
            }
        }
        return m;
    }
}
