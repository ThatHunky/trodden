package dev.thathunky.trodden;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Claims where the owner has turned trampling on. The key is the world plus the claim's top corners:
 * HuskClaims 1.5 has no public id, and the corners stay stable as long as the claim is not resized.
 */
public final class ClaimToggles {

    private final Set<String> enabled = new HashSet<>();

    public static String key(String world, int nearX, int nearZ, int farX, int farZ) {
        return world + ";" + nearX + ";" + nearZ + ";" + farX + ";" + farZ;
    }

    boolean enabled(String key) {
        return enabled.contains(key);
    }

    void set(String key, boolean on) {
        if (on) {
            enabled.add(key);
        } else {
            enabled.remove(key);
        }
    }

    void load(File file) {
        enabled.clear();
        if (file.exists()) {
            enabled.addAll(YamlConfiguration.loadConfiguration(file).getStringList("enabled"));
        }
    }

    void save(File file, Logger log) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("enabled", List.copyOf(enabled));
        try {
            yml.save(file);
        } catch (IOException e) {
            log.warning("could not save " + file + ": " + e);
        }
    }
}
