package dev.thathunky.trodden;

import java.util.Set;

/** Where and when autumn leaf litter settles, and when spring clears it away. */
final class LitterRules {

    private final Set<String> ground;
    private final int maxNearby;

    LitterRules(Set<String> ground, int maxNearby) {
        this.ground = Set.copyOf(ground);
        this.maxNearby = maxNearby;
    }

    /**
     * Whether litter can settle at all this season. Split out of {@link #canLitter} so a caller can
     * ask it before paying for the neighbour scans that {@code canLitter}'s arguments need — for
     * nine months of the year the answer is no and nothing has to be looked at.
     */
    boolean inSeason(Season season) {
        return season == Season.AUTUMN;
    }

    boolean canLitter(String groundMaterial, String aboveMaterial, boolean leavesAbove, int nearbyLitter, Season season) {
        return inSeason(season)
                && leavesAbove
                && ground.contains(groundMaterial)
                && "AIR".equals(aboveMaterial)
                && nearbyLitter < maxNearby;
    }

    boolean shouldClear(Season season) {
        return season == Season.SPRING;
    }
}
