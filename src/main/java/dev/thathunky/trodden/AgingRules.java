package dev.thathunky.trodden;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * How stone blocks age near water, under rain, or near lava, and how a brush cleans an aged block
 * back. Pure logic, checked without a server: material names are plain strings.
 *
 * <p>Rules are tried in table order and the first one whose {@code from} matches the block and
 * whose {@link Condition} is present in the caller's set wins — table order decides when a block
 * could qualify for more than one outcome (e.g. stone bricks that see both water and lava).
 */
final class AgingRules {

    enum Condition {
        WATER, RAIN, LAVA
    }

    record Rule(String from, String to, Condition condition) {
    }

    private final List<Rule> rules;
    private final Set<String> ageable;
    private final Map<String, String> cleanup = new HashMap<>();

    AgingRules(List<Rule> rules) {
        this.rules = List.copyOf(rules);
        Set<String> from = new HashSet<>();
        for (Rule rule : this.rules) {
            from.add(rule.from());
            // First rule that produces a given "to" decides where the brush sends it back.
            cleanup.putIfAbsent(rule.to(), rule.from());
        }
        this.ageable = Set.copyOf(from);
    }

    /** Cheap check before a probe touches any neighbour blocks: could this material ever age. */
    boolean mayAge(String material) {
        return ageable.contains(material);
    }

    /** The material this block ages into given the conditions present around it, or null if none apply. */
    String aged(String material, Set<Condition> present) {
        for (Rule rule : rules) {
            if (rule.from().equals(material) && present.contains(rule.condition())) {
                return rule.to();
            }
        }
        return null;
    }

    /** The material a brush restores this block to, or null if it is not an aged form of anything. */
    String cleaned(String material) {
        return cleanup.get(material);
    }

    /**
     * Default rules for 26.2: moss on cobblestone and stone brick shapes near water or under rain,
     * cracks on stone and deepslate bricks near lava. Only names verified to exist against
     * {@code org.bukkit.Material} are used — the brick family's shaped forms are singular
     * ({@code STONE_BRICK_STAIRS}), the full block is plural ({@code STONE_BRICKS}), and cracked
     * bricks have no shaped (stairs/slab/wall) or mossy-cracked forms in vanilla, so none are listed.
     */
    static AgingRules ofDefaults() {
        List<Rule> rules = new ArrayList<>();
        mossFamily(rules, "COBBLESTONE", "MOSSY_COBBLESTONE");
        mossFamily(rules, "COBBLESTONE_STAIRS", "MOSSY_COBBLESTONE_STAIRS");
        mossFamily(rules, "COBBLESTONE_SLAB", "MOSSY_COBBLESTONE_SLAB");
        mossFamily(rules, "COBBLESTONE_WALL", "MOSSY_COBBLESTONE_WALL");
        mossFamily(rules, "STONE_BRICKS", "MOSSY_STONE_BRICKS");
        mossFamily(rules, "STONE_BRICK_STAIRS", "MOSSY_STONE_BRICK_STAIRS");
        mossFamily(rules, "STONE_BRICK_SLAB", "MOSSY_STONE_BRICK_SLAB");
        mossFamily(rules, "STONE_BRICK_WALL", "MOSSY_STONE_BRICK_WALL");
        // Moss rules for STONE_BRICKS come first in the table, so lava only cracks it when water/rain
        // are not present — table order decides, as documented above.
        rules.add(new Rule("STONE_BRICKS", "CRACKED_STONE_BRICKS", Condition.LAVA));
        rules.add(new Rule("DEEPSLATE_BRICKS", "CRACKED_DEEPSLATE_BRICKS", Condition.LAVA));
        return new AgingRules(rules);
    }

    private static void mossFamily(List<Rule> rules, String from, String to) {
        rules.add(new Rule(from, to, Condition.WATER));
        rules.add(new Rule(from, to, Condition.RAIN));
    }

    /**
     * Parses {@code aging.rules} config lines ({@code "FROM > TO : CONDITION"}) into a full rule
     * table that replaces the defaults entirely. Malformed lines are skipped with a warning, and
     * so is any line whose {@code from} or {@code to} is not a real material according to
     * {@code materialExists} — this keeps a config typo from surviving to an
     * {@code IllegalArgumentException} out of {@code Material.valueOf(...)} on a region thread
     * later. This class stays free of Bukkit types so it can be unit-tested without a server; the
     * caller passes a predicate backed by {@code Material.getMaterial(name) != null}.
     */
    static AgingRules fromConfig(List<String> lines, Predicate<String> materialExists, Logger logger) {
        List<Rule> rules = new ArrayList<>();
        for (String line : lines) {
            Rule rule = parseRule(line, logger);
            if (rule == null) {
                continue;
            }
            if (!materialExists.test(rule.from()) || !materialExists.test(rule.to())) {
                logger.warning("Aging rule \"" + line + "\" refers to an unknown material — skipped.");
                continue;
            }
            rules.add(rule);
        }
        return new AgingRules(rules);
    }

    private static Rule parseRule(String line, Logger logger) {
        try {
            String[] fromTo = line.split(">", 2);
            String from = fromTo[0].trim();
            String[] toCondition = fromTo[1].split(":", 2);
            String to = toCondition[0].trim();
            Condition condition = Condition.valueOf(toCondition[1].trim().toUpperCase());
            return new Rule(from, to, condition);
        } catch (RuntimeException e) {
            logger.warning("Could not parse aging rule \"" + line + "\": " + e);
            return null;
        }
    }
}
