package dev.thathunky.trodden.stats;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Starts standard bStats metrics (no custom charts). This class lives outside {@code src/main/java}
 * on purpose: it is only compiled and shaded into the Gradle build's jar, so the fast local
 * {@code build.sh} path (which ships to the live server) never needs {@code org.bstats} on its
 * classpath and never bundles a metrics reporter. {@link dev.thathunky.trodden.Trodden} loads this
 * class by reflection and simply does nothing if it is absent, which is exactly the case for a
 * {@code build.sh} jar.
 *
 * <p>The plugin id below is a placeholder. To collect real data, register the plugin at
 * <a href="https://bstats.org/what-is-my-plugin-id">bstats.org</a> and replace {@link #PLUGIN_ID}
 * with the id it issues. Server owners can turn off reporting globally regardless of this id, via
 * {@code plugins/bStats/config.yml} ({@code enabled: false}) — see the README.
 */
public final class PluginMetrics {

    private static final int PLUGIN_ID = 0; // TODO: replace with the id from bstats.org before release

    @SuppressWarnings("unused") // constructed reflectively by Trodden
    public PluginMetrics(JavaPlugin plugin) {
        if (PLUGIN_ID <= 0) {
            plugin.getLogger().warning("bStats plugin id is still the placeholder (0) — metrics not started. "
                    + "Register at bstats.org and set PluginMetrics.PLUGIN_ID.");
            return;
        }
        new Metrics(plugin, PLUGIN_ID);
    }
}
