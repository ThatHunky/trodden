package dev.thathunky.trodden;

import java.util.EnumMap;
import java.util.Map;

/**
 * How a worn block walks back to grass when nobody steps on it. One stage per {@code daysPerStage}
 * idle days, scaled by the season: spring is fast, winter frozen.
 */
final class RegrowthRules {

    /** Stands for "grass again, of whatever kind a neighbour block is". */
    static final String GRASS_MARKER = "*GRASS*";

    private final int daysPerStage;
    private final Map<Season, Double> factors;

    RegrowthRules(int daysPerStage, Map<Season, Double> factors) {
        if (daysPerStage < 1) {
            throw new IllegalArgumentException("days-per-stage must be at least 1, got " + daysPerStage);
        }
        this.daysPerStage = daysPerStage;
        this.factors = new EnumMap<>(factors);
    }

    int stagesBack(int idleDays, Season season) {
        double factor = factors.getOrDefault(season, 1.0);
        if (factor <= 0) {
            return 0;
        }
        int threshold = (int) Math.ceil(daysPerStage * factor);
        return idleDays / threshold;
    }

    String previous(String material) {
        if (WearRules.PATH.equals(material)) {
            return WearRules.COARSE;
        }
        return WearRules.COARSE.equals(material) ? GRASS_MARKER : null;
    }
}
