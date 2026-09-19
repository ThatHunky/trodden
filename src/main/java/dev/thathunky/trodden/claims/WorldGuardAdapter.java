package dev.thathunky.trodden.claims;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * WorldGuard integration. WorldGuard has no single "region at a location" — several regions can
 * overlap, so the highest-priority non-global region is used, matching how WorldGuard itself
 * resolves conflicting flags.
 *
 * <p>WorldGuard also has no single owner the way HuskClaims does: a region carries an owner
 * <em>set</em>. When that set has exactly one player, that player is reported as the owner;
 * otherwise no owner is reported, which makes the brush's owner check conservative (nobody may
 * clean it without {@code trodden.admin}) rather than guessing wrong.
 */
final class WorldGuardAdapter implements Claims {

    WorldGuardAdapter() {
        // Touch the API now so a version mismatch throws immediately at construction, inside the
        // caller's try/catch, instead of surfacing later during gameplay.
        WorldGuard.getInstance().getPlatform().getRegionContainer();
    }

    @Override
    public Optional<Found> at(org.bukkit.Location loc) {
        Location weLoc = BukkitAdapter.adapt(loc);
        ApplicableRegionSet regions = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .createQuery().getApplicableRegions(weLoc);
        ProtectedRegion region = regions.getRegions().stream()
                .filter(r -> !ProtectedRegion.GLOBAL_REGION.equals(r.getId()))
                .max(Comparator.comparingInt(ProtectedRegion::getPriority))
                .orElse(null);
        if (region == null) {
            return Optional.empty();
        }
        String key = "worldguard;" + loc.getWorld().getName() + ";" + region.getId();
        DefaultDomain owners = region.getOwners();
        Set<UUID> ids = owners.getUniqueIds();
        Optional<UUID> owner = ids.size() == 1 ? Optional.of(ids.iterator().next()) : Optional.empty();
        // WorldGuard has no admin-claim flag. A region with no single owner behaves like an admin
        // claim for our purposes anyway: nobody can toggle wear off for it via /paths, only
        // trodden.admin can.
        return Optional.of(new Found(key, owner));
    }

    @Override
    public String name() {
        return "WorldGuard";
    }
}
