package dev.thathunky.trodden.claims;

import java.util.Optional;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;

/** GriefPrevention integration. Admin claims have no owner ({@code ownerID == null}). */
final class GriefPreventionAdapter implements Claims {

    private final DataStore dataStore;

    GriefPreventionAdapter() {
        this.dataStore = GriefPrevention.instance.dataStore;
        if (dataStore == null) {
            throw new IllegalStateException("GriefPrevention dataStore not ready");
        }
    }

    @Override
    public Optional<Found> at(Location loc) {
        Claim claim = dataStore.getClaimAt(loc, true, null);
        if (claim == null) {
            return Optional.empty();
        }
        while (claim.parent != null) {
            claim = claim.parent;
        }
        String key = "griefprevention;" + claim.getID();
        return Optional.of(new Found(key, Optional.ofNullable(claim.ownerID)));
    }

    @Override
    public String name() {
        return "GriefPrevention";
    }
}
