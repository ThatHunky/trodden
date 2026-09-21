package dev.thathunky.trodden;

import dev.thathunky.trodden.claims.Claims;
import dev.thathunky.trodden.claims.ClaimsProvider;
import java.io.File;
import java.time.MonthDay;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public final class Trodden extends JavaPlugin {

    private WearStore store;
    private Claims claims;
    private RegrowthTask regrowth;
    private Sampler sampler;
    private final ClaimToggles toggles = new ClaimToggles();
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(getDataFolder(), getConfig().getString("language", "en"), getLogger());
        WearRules rules = new WearRules(new HashSet<>(getConfig().getStringList("wear.grass")),
                getConfig().getInt("wear.coarse-at", 50), getConfig().getInt("wear.path-at", 100));
        store = new WearStore(this);
        claims = ClaimsProvider.detect(this, getLogger(), getConfig().getStringList("claims.prefer"));
        toggles.load(new File(getDataFolder(), "claims.yml"));
        Set<String> worlds = new HashSet<>(getConfig().getStringList("worlds"));
        double bonus = getConfig().getDouble("path-speed-bonus", 0.15);

        // Construct regrowth and sampler before listener registration to avoid null field access
        // in event handlers that may fire immediately.
        boolean regrowthEnabled = getConfig().getBoolean("regrowth.enabled", true);
        Set<String> regrowthWorlds = regrowthEnabled ? worlds : Set.of();
        String seasonsJson = getConfig().getString("seasons.json", "");
        Seasons seasons = new Seasons(seasonDates(), seasonsJson, getLogger());
        regrowth = new RegrowthTask(this,
                new RegrowthRules(getConfig().getInt("regrowth.days-per-stage", 7), seasonFactors()),
                seasons, store, regrowthWorlds);

        boolean samplingEnabled = getConfig().getBoolean("sampling.enabled", true);
        sampler = new Sampler(this, worlds, getConfig().getInt("sampling.radius", 12),
                getConfig().getInt("sampling.per-player", 6));

        boolean agingEnabled = getConfig().getBoolean("aging.enabled", true);
        List<String> agingRuleLines = getConfig().getStringList("aging.rules");
        AgingRules aging = agingRuleLines.isEmpty() ? AgingRules.ofDefaults()
                : AgingRules.fromConfig(agingRuleLines, name -> Material.getMaterial(name) != null, getLogger());
        if (agingEnabled && samplingEnabled) {
            sampler.add(new AgingProbe(this, aging, getConfig().getDouble("aging.chance", 0.02)));
        }
        boolean brushCleans = agingEnabled && getConfig().getBoolean("aging.brush-cleans", true);

        boolean litterEnabled = getConfig().getBoolean("litter.enabled", true);
        if (litterEnabled && samplingEnabled && Compat.LEAF_LITTER == null) {
            getLogger().info("Leaf litter needs Minecraft 1.21.5 or newer — litter is off on this server.");
        } else if (litterEnabled && samplingEnabled) {
            LitterRules litter = new LitterRules(Set.of("GRASS_BLOCK", "DIRT", "COARSE_DIRT", "DIRT_PATH", "PODZOL"),
                    getConfig().getInt("litter.max-nearby", 3));
            sampler.add(new LitterProbe(this, seasons, litter, getConfig().getDouble("litter.chance", 0.05)));
        }

        boolean mudEnabled = getConfig().getBoolean("mud.enabled", true);
        if (mudEnabled && samplingEnabled) {
            sampler.add(new MudProbe(this, store, rules, getConfig().getDouble("mud.wet-chance", 0.05),
                    getConfig().getDouble("mud.dry-chance", 0.2)));
        }

        boolean snowTracks = getConfig().getBoolean("snow-tracks", true);
        boolean mountsWear = getConfig().getBoolean("mounts-wear", true);
        WearListener listener = new WearListener(this, rules, store, worlds, bonus, aging, brushCleans, snowTracks,
                mountsWear);
        getServer().getPluginManager().registerEvents(listener, this);
        getCommand("paths").setExecutor(new StezhkyCommand(this, messages));
        long saveTicks = 20L * 60 * getConfig().getLong("save-minutes", 5);
        Sched.everyTicks(this, saveTicks, () -> store.flushAll(getServer().getWorlds()));

        if (regrowthEnabled) {
            Sched.everyTicks(this, 20L * 60 * getConfig().getLong("regrowth.sweep-minutes", 5), regrowth::sweepLoaded);
        }

        if (samplingEnabled) {
            Sched.everyTicks(this, 20L * getConfig().getLong("sampling.seconds", 10), sampler::run);
        }

        getLogger().info("Trampling: grass -> coarse dirt at " + getConfig().getInt("wear.coarse-at", 50)
                + ", -> path at " + rules.pathAt() + " steps; worlds " + worlds + "; path speed bonus +"
                + Math.round(bonus * 100) + "%; claims " + (claims != null ? "checked, owner opt-in" : "NOT checked")
                + "; regrowth " + (regrowthEnabled ? "enabled" : "disabled"));
    }

    /** Season start dates (MM-DD) from config, falling back to {@link Seasons#ofDefaults()}'s values. */
    private Map<Season, MonthDay> seasonDates() {
        ConfigurationSection sec = getConfig().getConfigurationSection("seasons");
        Map<Season, MonthDay> starts = new EnumMap<>(Season.class);
        starts.put(Season.SPRING, parseMonthDay(sec, "spring", MonthDay.of(3, 1)));
        starts.put(Season.SUMMER, parseMonthDay(sec, "summer", MonthDay.of(6, 1)));
        starts.put(Season.AUTUMN, parseMonthDay(sec, "autumn", MonthDay.of(9, 1)));
        starts.put(Season.WINTER, parseMonthDay(sec, "winter", MonthDay.of(12, 1)));
        return starts;
    }

    private MonthDay parseMonthDay(ConfigurationSection sec, String key, MonthDay fallback) {
        String raw = sec == null ? null : sec.getString(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            String[] parts = raw.split("-", 2);
            return MonthDay.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (RuntimeException e) {
            getLogger().warning("Could not parse season start date \"" + raw + "\" for " + key + " — using the default.");
            return fallback;
        }
    }

    private Map<Season, Double> seasonFactors() {
        ConfigurationSection sec = getConfig().getConfigurationSection("regrowth.season-factor");
        Map<Season, Double> factors = new EnumMap<>(Season.class);
        for (Season s : Season.values()) {
            factors.put(s, sec == null ? 1.0 : sec.getDouble(s.name().toLowerCase(), 1.0));
        }
        return factors;
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.flushAllNow(getServer().getWorlds());
        }
        getServer().getOnlinePlayers().forEach(p -> {
            var inst = p.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
            if (inst != null) {
                inst.removeModifier(new org.bukkit.NamespacedKey(this, "path_speed"));
            }
        });
    }

    Claims claims() {
        return claims;
    }

    ClaimToggles toggles() {
        return toggles;
    }

    RegrowthTask regrowth() {
        return regrowth;
    }

    /** Shared claim check: wear (stepping and regrowth alike) is allowed unless the owner turned it off. */
    boolean wearAllowedAt(Location loc) {
        Claims claims = claims();
        if (claims == null) {
            return true;
        }
        var claim = claims.at(loc);
        return claim.isEmpty() || toggles().enabled(claim.get().key());
    }

    void saveToggles() {
        getDataFolder().mkdirs();
        toggles.save(new File(getDataFolder(), "claims.yml"), getLogger());
    }
}
