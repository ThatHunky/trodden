package dev.thathunky.trodden;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

/**
 * Blocks and items that do not exist on every supported server version, looked up by name at
 * runtime instead of linked as {@code Material.X} constants. A constant that the running server
 * lacks would fail with {@code NoSuchFieldError} the first time the class touching it is used; a
 * name lookup just comes back {@code null}, and the feature that needs the block switches off.
 *
 * <p>Each lookup takes several names, newest first, for blocks that were renamed between versions.
 */
final class Compat {

    /** Vanilla leaf litter — added in 1.21.5. {@code null} on 1.21.4: the litter probe stays off. */
    static final Material LEAF_LITTER = material("leaf_litter");

    private Compat() {
    }

    /** The first of {@code names} the running server knows, or {@code null} if none. */
    static Material material(String... names) {
        for (String name : names) {
            Material m = lookup(name.toLowerCase(Locale.ROOT));
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    private static Material lookup(String name) {
        try {
            Material m = Registry.MATERIAL.get(NamespacedKey.minecraft(name));
            if (m != null) {
                return m;
            }
        } catch (Throwable ignored) {
            // No registry for Material on this version (or outside a server, in tests) — try by enum name.
        }
        return Material.getMaterial(name.toUpperCase(Locale.ROOT));
    }

    /** True if the running server has {@code m}; {@code m == null} means it does not. */
    static boolean is(Material actual, Material m) {
        return m != null && actual == m;
    }
}
