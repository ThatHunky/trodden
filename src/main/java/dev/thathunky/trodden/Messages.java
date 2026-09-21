package dev.thathunky.trodden;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Player-facing strings for the /paths command, picked per player from the client locale.
 *
 * <p>Which language: the player's locale matched against the available files — exact tag first
 * ({@code pt_BR}), then the bare language ({@code de} for {@code de_AT}), then any file of the same
 * language ({@code zh_CN} for {@code zh_TW}). No match means the server-wide {@code language} from
 * config.yml, and that one is also what the console gets. English is the last resort.
 *
 * <p>Lookup order, key by key, inside the chosen language: the file in the plugin's data folder
 * ({@code lang/<tag>.yml}) wins; then the bundled copy of that language from the jar, which covers
 * keys added by a later version that an older on-disk file never had. A key missing from both falls
 * through to the config language, then to English, with a warning once per language and key.
 *
 * <p>Only the config language is extracted into the data folder, so the server owner has something
 * to edit; any other file dropped into {@code lang/} there overrides the bundled one of the same
 * name, or adds a new language. Values are MiniMessage strings.
 */
final class Messages {

    /** Every file shipped in the jar's {@code lang/}. TestMain checks this against the folder. */
    static final List<String> BUNDLED = List.of("en", "uk", "de", "es", "fr", "pl", "pt_BR", "ja", "zh_CN");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** One language: the data-folder copy over the bundled copy, either possibly empty. */
    private record Layer(YamlConfiguration disk, YamlConfiguration jar) {
        String get(String key) {
            String v = disk.getString(key);
            return v != null ? v : jar.getString(key);
        }
    }

    private final File dataFolder;
    private final String defaultTag;
    private final Logger log;
    /** Lower-cased tag -> the tag as the file spells it. */
    private final Map<String, String> available = new LinkedHashMap<>();
    private final Map<String, Layer> layers = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    Messages(File dataFolder, String language, Logger log) {
        this.dataFolder = dataFolder;
        this.log = log;
        for (String tag : BUNDLED) {
            available.put(tag.toLowerCase(Locale.ROOT), tag);
        }
        File[] custom = new File(dataFolder, "lang").listFiles((d, n) -> n.endsWith(".yml"));
        if (custom != null) {
            for (File f : custom) {
                String tag = f.getName().substring(0, f.getName().length() - 4);
                available.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
            }
        }
        String wanted = language == null ? "en" : language.replace('-', '_');
        String found = available.get(wanted.toLowerCase(Locale.ROOT));
        if (found == null) {
            log.warning("Language \"" + language + "\" from config.yml has no lang/ file — using English.");
            found = "en";
        }
        this.defaultTag = found;
        extract(new File(dataFolder, "lang/" + found + ".yml"), found, log);
    }

    /** The config language's tag as its file spells it (e.g. {@code pt_BR}). */
    String defaultLanguage() {
        return defaultTag;
    }

    /** The language tag a player with this locale gets; {@code null} locale means the config one. */
    String languageFor(Locale locale) {
        return resolve(locale, available.values(), defaultTag);
    }

    /**
     * Matches a client locale against the available tags: exact, then bare language, then the
     * first tag of the same language. Pure, so TestMain can check it without a server.
     */
    static String resolve(Locale locale, Iterable<String> tags, String fallback) {
        if (locale == null || locale.getLanguage().isEmpty()) {
            return fallback;
        }
        String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
        String exact = (lang + "_" + locale.getCountry()).toLowerCase(Locale.ROOT);
        String sameLanguage = null;
        String bare = null;
        for (String tag : tags) {
            String t = tag.toLowerCase(Locale.ROOT);
            if (t.equals(exact)) {
                return tag;
            }
            if (t.equals(lang)) {
                bare = tag;
            } else if (sameLanguage == null && t.startsWith(lang + "_")) {
                sameLanguage = tag;
            }
        }
        if (bare != null) {
            return bare;
        }
        return sameLanguage != null ? sameLanguage : fallback;
    }

    private Layer layer(String tag) {
        return layers.computeIfAbsent(tag, t -> {
            File file = new File(dataFolder, "lang/" + t + ".yml");
            YamlConfiguration disk = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
            YamlConfiguration jar = loadBundled(t, log);
            return new Layer(disk, jar != null ? jar : new YamlConfiguration());
        });
    }

    private static void extract(File file, String tag, Logger log) {
        if (file.isFile()) {
            return;
        }
        try (InputStream in = Messages.class.getClassLoader().getResourceAsStream("lang/" + tag + ".yml")) {
            if (in != null) {
                file.getParentFile().mkdirs();
                Files.copy(in, file.toPath());
            }
        } catch (IOException e) {
            log.warning("Could not extract lang/" + tag + ".yml to the plugin folder: " + e);
        }
    }

    /** The jar's copy of {@code lang/<tag>.yml}, or {@code null} if the jar has none. */
    static YamlConfiguration loadBundled(String tag, Logger log) {
        try (InputStream in = Messages.class.getClassLoader().getResourceAsStream("lang/" + tag + ".yml")) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warning("Could not read the bundled lang/" + tag + ".yml: " + e);
            return null;
        }
    }

    /** Looks {@code key} up in {@code tag}, then the config language, then English. */
    Component get(String tag, String key, Map<String, String> placeholders) {
        List<String> chain = new ArrayList<>(List.of(tag));
        if (!chain.contains(defaultTag)) {
            chain.add(defaultTag);
        }
        if (!chain.contains("en")) {
            chain.add("en");
        }
        String template = null;
        for (String t : chain) {
            template = layer(t).get(key);
            if (template != null) {
                break;
            }
            if (warned.add(t + "/" + key)) {
                log.warning("Missing message key \"" + key + "\" in lang/" + t + ".yml — falling back.");
            }
        }
        if (template == null) {
            template = key;
        }
        return MM.deserialize(fill(template, placeholders));
    }

    /** Players get their client's language; the console and command blocks get the config one. */
    void send(CommandSender to, String key, Map<String, String> placeholders) {
        String tag = to instanceof Player p ? languageFor(p.locale()) : defaultTag;
        to.sendMessage(get(tag, key, placeholders));
    }

    /** Replaces every {key} that the map knows; unknown braces are left alone on purpose. */
    static String fill(String template, Map<String, String> placeholders) {
        String out = template;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    /** Every key of a lang file, nested ones included — TestMain compares these across languages. */
    static Set<String> keys(YamlConfiguration yaml) {
        return new HashSet<>(yaml.getKeys(true));
    }
}
