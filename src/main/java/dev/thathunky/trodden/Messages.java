package dev.thathunky.trodden;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Player-facing strings for the /paths command. Lookup order, key by key: the file in the plugin's
 * data folder ({@code lang/<language>.yml}) wins; then the bundled copy of that same language from
 * the jar, which covers keys added by a later version of the plugin that an older on-disk file
 * never had; then the English default, which logs a warning once per key, not once per lookup.
 *
 * <p>The data-folder file is extracted from the jar when it is missing, so the server owner has
 * something to edit — but it is never rewritten afterwards, which is exactly why the bundled copy
 * has to stay in memory as a layer underneath it. Values are MiniMessage strings.
 */
final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final YamlConfiguration texts;
    private final YamlConfiguration bundled;
    private final YamlConfiguration englishDefaults;
    private final Logger log;
    private final Set<String> warnedKeys = new HashSet<>();

    Messages(File dataFolder, String language, Logger log) {
        this.log = log;
        this.englishDefaults = loadBundled("en", log);
        YamlConfiguration jar = loadBundled(language, log);
        if (jar == null) {
            log.warning("Language file lang/" + language + ".yml not found in the jar — all messages will be in English.");
            jar = new YamlConfiguration();
        }
        this.bundled = jar;
        this.texts = loadFromDisk(dataFolder, language, log);
    }

    /** The data-folder copy of {@code lang/<language>.yml}, extracted from the jar first if missing. */
    private static YamlConfiguration loadFromDisk(File dataFolder, String language, Logger log) {
        File file = new File(dataFolder, "lang/" + language + ".yml");
        if (!file.isFile()) {
            extract(file, language, log);
        }
        return file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private static void extract(File file, String language, Logger log) {
        try {
            file.getParentFile().mkdirs();
            try (InputStream in = Messages.class.getClassLoader().getResourceAsStream("lang/" + language + ".yml")) {
                if (in != null) {
                    Files.copy(in, file.toPath());
                }
            }
        } catch (IOException e) {
            log.warning("Could not extract lang/" + language + ".yml to the plugin folder: " + e);
        }
    }

    private static YamlConfiguration loadBundled(String language, Logger log) {
        try (InputStream in = Messages.class.getClassLoader().getResourceAsStream("lang/" + language + ".yml")) {
            if (in == null) {
                return language.equals("en") ? new YamlConfiguration() : null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warning("Could not read the bundled lang/" + language + ".yml: " + e);
            return language.equals("en") ? new YamlConfiguration() : null;
        }
    }

    /**
     * Looks up {@code key} in the on-disk file for the chosen language, then in the bundled copy of
     * that language, then in the English default.
     */
    Component get(String key, Map<String, String> placeholders) {
        String template = texts.getString(key);
        if (template == null) {
            template = bundled.getString(key); // a key this version added after the on-disk copy was written
        }
        if (template == null) {
            template = englishDefaults.getString(key);
            if (template != null && warnedKeys.add(key)) {
                log.warning("Missing message key \"" + key + "\" in the chosen language — using the English default.");
            }
        }
        if (template == null) {
            template = key;
        }
        return MM.deserialize(fill(template, placeholders));
    }

    void send(CommandSender to, String key, Map<String, String> placeholders) {
        to.sendMessage(get(key, placeholders));
    }

    /** Replaces every {key} that the map knows; unknown braces are left alone on purpose. */
    static String fill(String template, Map<String, String> placeholders) {
        String out = template;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }
}
