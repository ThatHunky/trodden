package dev.thathunky.trodden;

import dev.thathunky.trodden.claims.ClaimsProvider;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

/** Server-free checks: build.sh runs this against the libraries/ classpath. */
public final class TestMain {

    private static int passed;
    private static int failed;

    static void check(boolean condition, String name) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    public static void main(String[] args) {
        rulesTests();
        packTests();
        claimKeyTests();
        claimsProviderOrderTests();
        storeCodecTests();
        storeLegacyChainTests();
        seasonTests();
        seasonJsonTests();
        regrowthTests();
        agingTests();
        agingConfigTests();
        litterTests();
        mudTests();
        messageTests();
        System.out.println("checks: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void rulesTests() {
        WearRules r = new WearRules(Set.of("GRASS_BLOCK", "PODZOL"), 50, 100);
        check(r.wears("GRASS_BLOCK"), "grass wears");
        check(r.wears("COARSE_DIRT"), "coarse dirt wears");
        check(!r.wears("DIRT_PATH"), "a path is already the final stage");
        check(!r.wears("STONE"), "stone does not wear");
        check(r.next("GRASS_BLOCK", 49) == null, "49 steps — still grass");
        check("COARSE_DIRT".equals(r.next("GRASS_BLOCK", 50)), "50 steps — coarse dirt");
        check(r.next("COARSE_DIRT", 99) == null, "99 steps — still coarse dirt");
        check("DIRT_PATH".equals(r.next("COARSE_DIRT", 100)), "100 steps — a path");
        check("DIRT_PATH".equals(r.next("GRASS_BLOCK", 100)), "grass with a counter of 100 becomes a path immediately");
        check("COARSE_DIRT".equals(r.next("PODZOL", 60)), "podzol wears too");
        check(r.next("STONE", 500) == null, "stone never wears");
        check(r.startCount("COARSE_DIRT") == 50, "placed coarse dirt starts at 50");
        check(WearRules.scatters("LEAF_LITTER"), "a step scatters leaf litter");
        check(!WearRules.scatters("AIR"), "a step over nothing scatters nothing");
        check(!WearRules.scatters("SHORT_GRASS"),
                "only litter is scattered on every step — grass and flowers still go at the threshold");
        check(r.startCount("GRASS_BLOCK") == 0, "grass starts at 0");
        boolean threw = false;
        try {
            new WearRules(Set.of("GRASS_BLOCK"), 100, 50);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "thresholds in the wrong order are rejected");
    }

    static void packTests() {
        int[][] cases = {{0, -64, 0}, {15, 319, 15}, {-1, 70, -17}, {33, 128, 47}, {-1100, 125, 351}};
        for (int[] c : cases) {
            int p = WearRules.pack(c[0], c[1], c[2]);
            check(WearRules.unpackX(p) == (c[0] & 15), "pack x " + c[0]);
            check(WearRules.unpackY(p) == c[1], "pack y " + c[1]);
            check(WearRules.unpackZ(p) == (c[2] & 15), "pack z " + c[2]);
        }
        check(WearRules.pack(1, 70, 2) != WearRules.pack(2, 70, 1), "x and z are not mixed up");
    }

    static void claimKeyTests() {
        String k = ClaimToggles.key("world", -120, 40, -80, 90);
        check("world;-120;40;-80;90".equals(k), "claim key");
        ClaimToggles t = new ClaimToggles();
        check(!t.enabled(k), "off by default");
        t.set(k, true);
        check(t.enabled(k), "on");
        t.set(k, false);
        check(!t.enabled(k), "off again");
    }

    static void claimsProviderOrderTests() {
        check(ClaimsProvider.resolveOrder(null).equals(ClaimsProvider.DEFAULT_ORDER),
                "null claims.prefer — default order");
        check(ClaimsProvider.resolveOrder(List.of()).equals(ClaimsProvider.DEFAULT_ORDER),
                "empty claims.prefer list — default order");
        List<String> custom = List.of("worldguard", "huskclaims");
        check(ClaimsProvider.resolveOrder(custom).equals(custom), "a custom order from config is kept as is");
        check(ClaimsProvider.DEFAULT_ORDER.equals(
                List.of("huskclaims", "lands", "towny", "griefprevention", "worldguard")),
                "default adapter order");
    }

    static void storeCodecTests() {
        Map<Integer, WearStore.Entry> m = Map.of(
                WearRules.pack(3, 70, 4), new WearStore.Entry(12, 20300),
                WearRules.pack(15, -10, 0), new WearStore.Entry(WearStore.MUD_MARK, 20345));
        int[] arr = WearStore.encode(m);
        check(arr.length == 6, "array of triples");
        check(WearStore.decode(arr).equals(m), "encoding triples round-trips");
        check(WearStore.decode(new int[] {1, 2, 3, 4}).size() == 1, "an incomplete tail is ignored");
        check(WearStore.decode(null).isEmpty(), "an empty chunk");

        Map<Integer, WearStore.Entry> legacy = WearStore.decodeLegacy(new int[] {WearRules.pack(1, 64, 2), 77}, 20350);
        check(legacy.size() == 1, "the legacy format is readable");
        WearStore.Entry e = legacy.get(WearRules.pack(1, 64, 2));
        check(e.count() == 77 && e.day() == 20350, "legacy counters get today's date");
        check(WearStore.decodeLegacy(null, 20350).isEmpty(), "no legacy key — empty");
    }

    /**
     * The Trodden rename changed the PDC namespace from {@code matsuriwear} to {@code trodden}, so
     * {@link WearStore} has to keep reading the pre-rename keys as a fallback or every path a player
     * already walked would be orphaned. {@link WearStore#resolve} is the pure priority function
     * {@link WearStore} itself uses to decide which of the three possible PDC entries to trust; these
     * checks pin down both legacy formats still decoding correctly and the order being exactly what
     * the javadoc on {@code chunkMap} claims: current key, then legacy {@code counts2} triples, then
     * legacy {@code counts} pairs.
     */
    static void storeLegacyChainTests() {
        Map<Integer, WearStore.Entry> currentData = Map.of(WearRules.pack(2, 65, 3), new WearStore.Entry(40, 20400));
        int[] currentTriples = WearStore.encode(currentData);

        Map<Integer, WearStore.Entry> legacyTripleData = Map.of(WearRules.pack(5, 70, 9), new WearStore.Entry(63, 20200));
        int[] legacyTriples = WearStore.encode(legacyTripleData);

        // The pair format (position, count) has no day of its own — decodeLegacy stamps "today" on
        // it, so decoding the same array with two different "today"s must disagree on the day.
        int[] legacyPairs = {WearRules.pack(1, 64, 2), 77};
        check(!WearStore.decodeLegacy(legacyPairs, 20111).equals(WearStore.decodeLegacy(legacyPairs, 20222)),
                "the legacy pair format still decodes, stamped with whatever day it is read on");
        check(WearStore.decode(legacyTriples).equals(legacyTripleData),
                "the legacy matsuriwear:counts2 triple format decodes exactly like the current one — same encoding, different key");

        check(WearStore.resolve(currentTriples, legacyTriples, legacyPairs, 20500).equals(currentData),
                "reading order: the current key wins when it has data, even with both legacy keys present");
        check(WearStore.resolve(null, legacyTriples, legacyPairs, 20500).equals(legacyTripleData),
                "reading order: with no current key, the legacy counts2 triples win over the older counts pairs");
        check(WearStore.resolve(null, null, legacyPairs, 20500)
                .equals(WearStore.decodeLegacy(legacyPairs, 20500)),
                "reading order: with neither newer key, the oldest legacy counts pairs are still read, stamped with today");
        check(WearStore.resolve(null, null, null, 20500).isEmpty(),
                "reading order: nothing stored under any of the three keys — empty, not an error");
    }

    static void seasonTests() {
        Seasons s = Seasons.ofDefaults(); // spring 03-01, summer 06-01, autumn 09-01, winter 12-01
        check(s.at(LocalDate.of(2026, 1, 15)) == Season.WINTER, "January — winter");
        check(s.at(LocalDate.of(2026, 3, 1)) == Season.SPRING, "March 1 — the first day of spring");
        check(s.at(LocalDate.of(2026, 5, 31)) == Season.SPRING, "May 31 — still spring");
        check(s.at(LocalDate.of(2026, 6, 1)) == Season.SUMMER, "June 1 — summer");
        check(s.at(LocalDate.of(2026, 9, 19)) == Season.AUTUMN, "September 19 — autumn");
        check(s.at(LocalDate.of(2026, 12, 1)) == Season.WINTER, "December 1 — winter");
        check(s.at(LocalDate.of(2026, 12, 31)) == Season.WINTER, "winter carries over the new year");
        Seasons shifted = new Seasons(Map.of(Season.SPRING, MonthDay.of(4, 1), Season.SUMMER, MonthDay.of(7, 1),
                Season.AUTUMN, MonthDay.of(10, 1), Season.WINTER, MonthDay.of(1, 15)));
        check(shifted.at(LocalDate.of(2026, 1, 10)) == Season.AUTUMN, "before January 15 the previous season continues");
        check(shifted.at(LocalDate.of(2026, 1, 20)) == Season.WINTER, "after January 15 — winter");
    }

    static void seasonJsonTests() {
        check(Seasons.fromJson("{\"id\": 3, \"key\": \"autumn\", \"from\": \"2026-09-01\"}") == Season.AUTUMN, "autumn from season.json");
        check(Seasons.fromJson("{\"key\":\"winter\"}") == Season.WINTER, "winter from season.json");
        check(Seasons.fromJson("{\"key\":\"unknown\"}") == null, "an unknown key — null");
        check(Seasons.fromJson("not json") == null, "garbage — null");
    }

    static void regrowthTests() {
        RegrowthRules r = new RegrowthRules(7, Map.of(
                Season.SPRING, 0.5, Season.SUMMER, 0.8, Season.AUTUMN, 1.2, Season.WINTER, 0.0));
        check(r.stagesBack(3, Season.SUMMER) == 0, "three days — still too early");
        check(r.stagesBack(6, Season.SUMMER) == 1, "6 days at a 0.8 factor — one stage back");
        check(r.stagesBack(12, Season.SUMMER) == 2, "12 days — two stages back");
        check(r.stagesBack(4, Season.SPRING) == 1, "in spring the threshold is 4 days");
        check(r.stagesBack(8, Season.AUTUMN) == 0, "in autumn the threshold is 9 days, 8 isn't enough");
        check(r.stagesBack(9, Season.AUTUMN) == 1, "in autumn 9 days — one stage back");
        check(r.stagesBack(400, Season.WINTER) == 0, "in winter it never regrows");
        check("COARSE_DIRT".equals(r.previous("DIRT_PATH")), "a path reverts to coarse dirt");
        check(RegrowthRules.GRASS_MARKER.equals(r.previous("COARSE_DIRT")), "coarse dirt reverts to grass");
        check(r.previous("GRASS_BLOCK") == null,
                "a grass block has nothing to revert to: a partial counter on it is simply dropped");
        check(r.previous("STONE") == null, "stone does not regrow");
    }

    static void agingTests() {
        AgingRules a = AgingRules.ofDefaults();
        check("MOSSY_COBBLESTONE".equals(a.aged("COBBLESTONE", Set.of(AgingRules.Condition.WATER))), "cobblestone near water grows moss");
        check("MOSSY_COBBLESTONE".equals(a.aged("COBBLESTONE", Set.of(AgingRules.Condition.RAIN))), "cobblestone under rain grows moss");
        check(a.aged("COBBLESTONE", Set.of()) == null, "dry cobblestone does not change");
        check("MOSSY_COBBLESTONE_STAIRS".equals(a.aged("COBBLESTONE_STAIRS", Set.of(AgingRules.Condition.WATER))), "stairs too");
        check("MOSSY_STONE_BRICKS".equals(a.aged("STONE_BRICKS", Set.of(AgingRules.Condition.WATER))), "bricks near water grow moss");
        check("CRACKED_STONE_BRICKS".equals(a.aged("STONE_BRICKS", Set.of(AgingRules.Condition.LAVA))), "bricks near lava crack");
        check("CRACKED_DEEPSLATE_BRICKS".equals(a.aged("DEEPSLATE_BRICKS", Set.of(AgingRules.Condition.LAVA))), "deepslate bricks crack");
        check(a.aged("OAK_PLANKS", Set.of(AgingRules.Condition.WATER)) == null, "wood does not age");
        check("COBBLESTONE".equals(a.cleaned("MOSSY_COBBLESTONE")), "a brush cleans moss");
        check("STONE_BRICKS".equals(a.cleaned("CRACKED_STONE_BRICKS")), "a brush cleans cracks");
        check(a.cleaned("COBBLESTONE") == null, "a clean block has nothing to clean");
    }

    static void agingConfigTests() {
        Predicate<String> knownMaterials = Set.of("COBBLESTONE", "MOSSY_COBBLESTONE")::contains;
        AgingRules a = AgingRules.fromConfig(List.of(
                "COBBLESTONE > MOSSY_COBBLESTONE : WATER",
                "OAK_LOG > FAKE_MATERIAL : WATER",
                "COBBLESTONE > MOSSY_COBBLESTONE : NOPE"
        ), knownMaterials, Logger.getLogger("test"));
        check("MOSSY_COBBLESTONE".equals(a.aged("COBBLESTONE", Set.of(AgingRules.Condition.WATER))),
                "a working config line loads");
        check(!a.mayAge("OAK_LOG"), "a line with an unknown material is dropped without throwing");
        check(a.cleaned("FAKE_MATERIAL") == null, "a nonexistent material does not make it into the table");
        check(a.aged("COBBLESTONE", Set.of()) == null,
                "a line with a broken condition is dropped, the rest of the table still works");
    }

    static void litterTests() {
        LitterRules l = new LitterRules(Set.of("GRASS_BLOCK", "DIRT", "COARSE_DIRT", "DIRT_PATH", "PODZOL"), 3);
        check(l.canLitter("GRASS_BLOCK", "AIR", true, 0, Season.AUTUMN), "in autumn, under a canopy, it settles");
        check(!l.canLitter("GRASS_BLOCK", "AIR", true, 0, Season.SUMMER), "in summer it does not settle");
        check(!l.canLitter("GRASS_BLOCK", "AIR", false, 0, Season.AUTUMN), "with no leaves above it does not settle");
        check(!l.canLitter("STONE", "AIR", true, 0, Season.AUTUMN), "it does not settle on stone");
        check(!l.canLitter("GRASS_BLOCK", "SHORT_GRASS", true, 0, Season.AUTUMN), "the spot is occupied");
        check(!l.canLitter("GRASS_BLOCK", "AIR", true, 3, Season.AUTUMN), "there is already enough litter nearby");
        check(l.canLitter("DIRT_PATH", "AIR", true, 1, Season.AUTUMN), "it settles on a path too");
        check(l.inSeason(Season.AUTUMN), "litter only settles in autumn");
        check(!l.inSeason(Season.SUMMER) && !l.inSeason(Season.SPRING) && !l.inSeason(Season.WINTER),
                "the season test alone rejects the other three seasons, before any neighbour scan");
        check(l.shouldClear(Season.SPRING), "in spring litter disappears");
        check(!l.shouldClear(Season.AUTUMN), "in autumn it does not disappear");
    }

    static void mudTests() {
        check(MudProbe.wets("DIRT_PATH", true, true), "a path under rain and open sky turns to mud");
        check(!MudProbe.wets("DIRT_PATH", false, true), "no rain — no mud");
        check(!MudProbe.wets("DIRT_PATH", true, false), "under a roof it does not turn to mud");
        check(!MudProbe.wets("GRASS_BLOCK", true, true), "grass does not turn to mud");
        check(MudProbe.dries("MUD", false, true), "our mud dries without rain");
        check(!MudProbe.dries("MUD", true, true), "it does not dry in the rain");
        check(!MudProbe.dries("MUD", false, false), "someone else's mud is left alone");
        check(MudProbe.keepsMark("MUD", WearStore.MUD_MARK), "our mud stays mud — the mark holds");
        check(!MudProbe.keepsMark("STONE", WearStore.MUD_MARK), "stone where our mud was — the stale mark is dropped");
        check(!MudProbe.keepsMark("DIRT_PATH", 40), "an ordinary path with a counter — not our mark, mud logic is left alone");
    }

    static void messageTests() {
        check("paths are on in <b>Home</b>".equals(
                Messages.fill("paths are on in <b>{claim}</b>", Map.of("claim", "Home"))), "key substitution");
        check("no key".equals(Messages.fill("no key", Map.of("claim", "Home"))), "a string with no keys is unchanged");
        check("{unknown} stays".equals(Messages.fill("{unknown} stays", Map.of())), "an unknown key is left as is");
    }
}
