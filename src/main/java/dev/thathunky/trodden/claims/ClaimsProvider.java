package dev.thathunky.trodden.claims;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/**
 * Picks the first available claims integration, in preference order. Each adapter is constructed
 * only after its plugin is confirmed enabled, and only inside a defensive try/catch — a version
 * mismatch disables that one integration instead of breaking the whole plugin.
 */
public final class ClaimsProvider {

    /** Preference order used when the config gives none, or gives an unknown/empty list. */
    public static final List<String> DEFAULT_ORDER =
            List.of("huskclaims", "lands", "towny", "griefprevention", "worldguard");

    private ClaimsProvider() {
    }

    /** The order {@link #detect} walks: {@code prefer} as given, or {@link #DEFAULT_ORDER} when it's null/empty. */
    public static List<String> resolveOrder(List<String> prefer) {
        return prefer == null || prefer.isEmpty() ? DEFAULT_ORDER : prefer;
    }

    /**
     * Detects the first enabled, constructible claims plugin from {@code prefer} (or from
     * {@link #DEFAULT_ORDER} when {@code prefer} is null or empty; a name in the list that is not
     * recognized is logged and skipped, not replaced by anything), logs which one was picked, and
     * returns {@code null} when none is present — wear then applies everywhere, same as when no
     * claims plugin exists at all.
     */
    public static Claims detect(Plugin plugin, Logger log, List<String> prefer) {
        List<String> order = resolveOrder(prefer);
        Map<String, Supplier<Claims>> factories = new LinkedHashMap<>();
        factories.put("huskclaims", HuskClaimsAdapter::new);
        factories.put("worldguard", WorldGuardAdapter::new);
        factories.put("griefprevention", GriefPreventionAdapter::new);
        factories.put("lands", () -> new LandsAdapter(plugin));
        factories.put("towny", TownyAdapter::new);
        Map<String, String> pluginNames = Map.of(
                "huskclaims", "HuskClaims",
                "worldguard", "WorldGuard",
                "griefprevention", "GriefPrevention",
                "lands", "Lands",
                "towny", "Towny");

        for (String id : order) {
            String key = id.toLowerCase(Locale.ROOT);
            Supplier<Claims> factory = factories.get(key);
            String pluginName = pluginNames.get(key);
            if (factory == null || pluginName == null) {
                log.warning("claims.prefer: unknown claims plugin \"" + id + "\" — skipping");
                continue;
            }
            if (!plugin.getServer().getPluginManager().isPluginEnabled(pluginName)) {
                continue;
            }
            try {
                Claims claims = factory.get();
                log.info("Claims: found " + claims.name());
                return claims;
            } catch (Exception | LinkageError e) {
                log.warning(pluginName + " is on the server, but its API is unavailable — skipping: " + e);
            }
        }
        log.info("Claims: no supported plugin found — trampling applies everywhere");
        return null;
    }
}
