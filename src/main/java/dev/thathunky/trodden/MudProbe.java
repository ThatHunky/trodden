package dev.thathunky.trodden;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Turns a dirt path to mud while it rains under open sky, and dries mud this plugin made back
 * into a path once the rain stops. Runs on the region thread that already owns the block's chunk
 * (see {@link Sched#atLocation}) and must never schedule anything itself — it only ever looks at
 * the one block {@link Sampler} hands it, never a chunk or world scan.
 *
 * <p>Only mud {@link WearStore#MUD_MARK} marks as ours is ever dried back: mud a player placed,
 * or mud that occurs naturally (e.g. near water), keeps whatever counter it already has (or none)
 * and is left alone.
 *
 * <p>Wetting and drying both carry the stored day across unchanged: the day means "when this block
 * was last stepped on", and the weather never steps on anything. Restamping it would keep a path
 * that sees rain from ever counting idle days towards regrowth.
 */
final class MudProbe implements Sampler.Probe {

    private final Trodden plugin;
    private final WearStore store;
    private final WearRules rules;
    private final double wetChance;
    private final double dryChance;

    MudProbe(Trodden plugin, WearStore store, WearRules rules, double wetChance, double dryChance) {
        this.plugin = plugin;
        this.store = store;
        this.rules = rules;
        this.wetChance = wetChance;
        this.dryChance = dryChance;
    }

    @Override
    public void at(Block block) {
        World world = block.getWorld();
        boolean storming = world.hasStorm();
        String type = block.getType().name();
        boolean skyExposed = Sky.openAbove(block);

        if (wets(type, storming, skyExposed)) {
            if (ThreadLocalRandom.current().nextDouble() > wetChance || !plugin.wearAllowedAt(block.getLocation())) {
                return;
            }
            // Keep the day of the block's last real step. Rain is not a step: stamping today here
            // would restart the regrowth clock every time it rained near a player, so a path in
            // rainy weather would never accumulate idle days and would never grow back.
            WearStore.Entry entry = entryAt(block);
            block.setType(Material.MUD, true);
            mark(block, WearStore.MUD_MARK, entry == null ? Days.today() : entry.day());
            return;
        }

        if (!WearStore.MUD.equals(type)) {
            return; // cheap check before asking the store anything: only mud can dry back
        }
        WearStore.Entry entry = entryAt(block);
        if (dries(type, storming, entry != null && keepsMark(type, entry.count()))) {
            if (ThreadLocalRandom.current().nextDouble() > dryChance || !plugin.wearAllowedAt(block.getLocation())) {
                return;
            }
            // Same reasoning as above, the other way around: restore the day the mud was carrying,
            // which is still the day of the last step on the path underneath it.
            block.setType(Material.valueOf(WearRules.PATH), true);
            mark(block, rules.pathAt(), entry.day());
        }
    }

    /** The stored counter at this block's position, or null when the plugin has none for it. */
    private WearStore.Entry entryAt(Block block) {
        Chunk chunk = block.getChunk();
        return store.get(chunk, WearRules.pack(block.getX(), block.getY(), block.getZ()));
    }

    private void mark(Block block, int count, int day) {
        store.put(block.getChunk(), WearRules.pack(block.getX(), block.getY(), block.getZ()), count, day);
    }

    /** Pure decision, no Bukkit: a path under storming rain and open sky turns to mud. */
    static boolean wets(String material, boolean storming, boolean skyExposed) {
        return WearRules.PATH.equals(material) && storming && skyExposed;
    }

    /** Pure decision, no Bukkit: our mud dries back to a path once the rain stops. */
    static boolean dries(String material, boolean storming, boolean ours) {
        return WearStore.MUD.equals(material) && !storming && ours;
    }

    /**
     * Pure decision, no Bukkit: whether a stored {@code MUD_MARK} counter at a position still
     * means "our mud". True only while the block actually sitting there is still mud — once
     * something else occupies the position (broken, replaced, built over), the mark is stale and
     * must not be trusted or kept: trusting it would let a later, unrelated mud block (natural
     * generation, a player placing it) be dried as if it were ours, and keeping it forever would
     * leave a dead counter in the chunk's PDC.
     */
    static boolean keepsMark(String material, int count) {
        return WearStore.MUD.equals(material) && count == WearStore.MUD_MARK;
    }
}
