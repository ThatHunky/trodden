package dev.thathunky.trodden.claims;

import dev.thathunky.trodden.ClaimToggles;
import java.util.Optional;
import net.william278.huskclaims.api.BukkitHuskClaimsAPI;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.Region;
import org.bukkit.Location;

/**
 * HuskClaims integration. This class is only touched when HuskClaims is enabled, so a server
 * without it never loads it.
 *
 * <p>The claim key format here — {@code world;nearX;nearZ;farX;farZ}, no plugin prefix — is exactly
 * what Matsuri's live {@code claims.yml} already stores. Do not change it: doing so would silently
 * discard every owner's trampling toggle on the live server. New adapters use a
 * {@code <plugin>;<id>} format instead so they never collide with this one.
 */
final class HuskClaimsAdapter implements Claims {

    private final BukkitHuskClaimsAPI api;

    HuskClaimsAdapter() {
        this.api = BukkitHuskClaimsAPI.getInstance();
    }

    @Override
    public Optional<Found> at(Location loc) {
        Optional<Claim> found = api.getClaimAt(api.getPosition(loc));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Claim claim = found.get();
        while (claim.getParent().isPresent()) {
            claim = claim.getParent().get();
        }
        Region r = claim.getRegion();
        String key = ClaimToggles.key(loc.getWorld().getName(), r.getNearCorner().getBlockX(), r.getNearCorner().getBlockZ(),
                r.getFarCorner().getBlockX(), r.getFarCorner().getBlockZ());
        return Optional.of(new Found(key, claim.getOwner()));
    }

    @Override
    public String name() {
        return "HuskClaims";
    }
}
