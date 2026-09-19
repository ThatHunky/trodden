package dev.thathunky.trodden.claims;

import java.util.Optional;
import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.Area;
import me.angeschossen.lands.api.land.Land;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/** Lands integration. Admin lands have no player owner, so their owner comes back empty. */
final class LandsAdapter implements Claims {

    private final LandsIntegration api;

    LandsAdapter(Plugin plugin) {
        this.api = LandsIntegration.of(plugin);
    }

    @Override
    @SuppressWarnings("deprecation") // Area#getId() — see the comment on its use below
    public Optional<Found> at(Location loc) {
        Area area = api.getArea(loc);
        if (area == null) {
            return Optional.empty();
        }
        Land land = area.getLand();
        // Area#getULID() looked appealing (globally unique on its own) but javap on the ULID
        // interface shows only randomULID()/fromString(String) — no declared or inherited
        // toString(), and the concrete implementation lives in the Lands plugin jar itself, which
        // this project does not have. Relying on Object's default toString() would put an identity
        // hash in the key, changing on every lookup and silently breaking claims.yml persistence.
        // Area#getId() is deprecated for removal but its contract (a stable int per area) is
        // unchanged in this version, and both it and Land#getName() have a well-defined
        // stringification, so they are used instead.
        String key = "lands;" + land.getName() + ";" + area.getId();
        return Optional.of(new Found(key, Optional.ofNullable(area.getOwnerUID())));
    }

    @Override
    public String name() {
        return "Lands";
    }
}
