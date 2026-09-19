package dev.thathunky.trodden.claims;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Towny integration. A town block's owner is its plot resident if one is set, otherwise the
 * town's mayor for town-owned land.
 */
final class TownyAdapter implements Claims {

    private final TownyAPI api;

    TownyAdapter() {
        this.api = TownyAPI.getInstance();
        if (api == null) {
            throw new IllegalStateException("TownyAPI not ready");
        }
    }

    @Override
    public Optional<Found> at(Location loc) {
        TownBlock block = api.getTownBlock(loc);
        if (block == null) {
            return Optional.empty();
        }
        String key = "towny;" + block.getWorldCoord().getWorldName() + ";" + block.getX() + ";" + block.getZ();
        Resident resident = block.getResidentOrNull();
        UUID owner = resident != null && resident.hasUUID() ? resident.getUUID() : mayorOf(block);
        return Optional.of(new Found(key, Optional.ofNullable(owner)));
    }

    private UUID mayorOf(TownBlock block) {
        Town town = block.getTownOrNull();
        if (town == null || !town.hasMayor()) {
            return null;
        }
        Resident mayor = town.getMayor();
        return mayor.hasUUID() ? mayor.getUUID() : null;
    }

    @Override
    public String name() {
        return "Towny";
    }
}
