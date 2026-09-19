package dev.thathunky.trodden.claims;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

/**
 * The claims plugin integration Trodden needs: find the claim at a location, its key and its
 * owner (if any). Nothing more — this is not a general-purpose claims abstraction, just what the
 * wear brush and the {@code /paths} command actually use.
 */
public interface Claims {

    /** The claim at a location, if any. */
    Optional<Found> at(Location loc);

    /** Display name for logging, e.g. "HuskClaims" or "WorldGuard". */
    String name();

    /**
     * A claim: its key (stable identity used by {@code claims.yml} toggles) and its owner (empty
     * for admin claims, and for plugins that have no single owner for the area). An empty owner is
     * what makes a claim untoggleable by anyone but {@code trodden.admin} (or the legacy
     * {@code wear.admin}/{@code matsuri.wear.admin}).
     */
    record Found(String key, Optional<UUID> owner) {
    }
}
