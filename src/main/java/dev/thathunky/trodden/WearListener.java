package dev.thathunky.trodden;

import dev.thathunky.trodden.claims.Claims;
import java.util.Set;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Player footsteps: when a player moves onto another block, that block's counter grows, and at
 * the thresholds grass becomes coarse dirt, then a path. A path gives a speed bonus.
 */
final class WearListener implements Listener {

    private final Trodden plugin;
    private final WearRules rules;
    private final WearStore store;
    private final Set<String> worlds;
    private final NamespacedKey speedKey;
    private final double speedBonus;
    private final AgingRules aging;
    private final boolean brushCleans;
    private final boolean snowTracks;
    private final boolean mountsWear;

    private static final Set<EntityType> MOUNTS = Set.of(EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
            EntityType.CAMEL);

    WearListener(Trodden plugin, WearRules rules, WearStore store, Set<String> worlds, double speedBonus,
            AgingRules aging, boolean brushCleans, boolean snowTracks, boolean mountsWear) {
        this.plugin = plugin;
        this.rules = rules;
        this.store = store;
        this.worlds = worlds;
        this.speedBonus = speedBonus;
        this.speedKey = new NamespacedKey(plugin, "path_speed");
        this.aging = aging;
        this.brushCleans = brushCleans;
        this.snowTracks = snowTracks;
        this.mountsWear = mountsWear;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() == to.getWorld()) {
            return;
        }
        Player p = e.getPlayer();
        @SuppressWarnings("deprecation")
        boolean onGround = p.isOnGround();
        if (!onGround) {
            return; // mid-jump, leave everything as is: the bonus doesn't flicker, steps aren't counted
        }
        Block ground = groundUnder(to);
        updateSpeed(p, ground.getType() == Material.DIRT_PATH);
        if (canWear(p) && worlds.contains(to.getWorld().getName())) {
            step(ground);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrush(PlayerInteractEvent e) {
        if (!brushCleans || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND
                || e.getItem() == null || e.getItem().getType() != Material.BRUSH || e.getClickedBlock() == null) {
            return;
        }
        Block block = e.getClickedBlock();
        String clean = aging.cleaned(block.getType().name());
        if (clean == null) {
            return;
        }
        if (!canClean(e.getPlayer(), block)) {
            return; // someone else's claim: do nothing, and do not cancel — no vanilla brushing either
        }
        block.setType(Material.valueOf(clean), true);
        e.setCancelled(true); // stop this from also starting a vanilla brushing animation
    }

    /**
     * Deliberately conservative: only the claim's raw owner (or an admin) may clean with the
     * brush here. Trusted members of someone else's claim cannot, because we do not read any
     * claims plugin's trust/member API in this plugin. This check is independent of
     * {@code wearAllowedAt} —
     * cleaning must work even when the owner has turned wear off for the claim, or they would
     * never be able to undo moss/cracks that formed before they turned it off.
     */
    private boolean canClean(Player player, Block block) {
        if (player.hasPermission("trodden.admin") || player.hasPermission("wear.admin")
                || player.hasPermission("matsuri.wear.admin")) {
            return true;
        }
        Claims claims = plugin.claims();
        if (claims == null) {
            return true; // no claims plugin installed: nothing to protect
        }
        return claims.at(block.getLocation())
                .map(found -> found.owner().isPresent() && found.owner().get().equals(player.getUniqueId()))
                .orElse(true); // not inside any claim
    }

    /**
     * A shod hoof wears the ground faster than a boot. Guards are ordered cheapest-first: entity
     * type, config flag, whether the block actually changed, whether the mount is on the ground
     * (not jumping, falling or swimming — same as the player path's own on-ground check), whether
     * a player is riding, then the world — each check is pricier than the last, and every one of
     * them rejects most calls.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityMove(EntityMoveEvent e) {
        if (!MOUNTS.contains(e.getEntity().getType()) || !mountsWear || !e.hasChangedBlock()) {
            return;
        }
        if (!e.getEntity().isOnGround()) {
            return; // mid-jump, falling or swimming: hooves never touched what's below
        }
        if (e.getEntity().getPassengers().stream().noneMatch(p -> p instanceof Player)) {
            return;
        }
        Location to = e.getTo();
        if (!worlds.contains(to.getWorld().getName())) {
            return;
        }
        Block ground = groundUnder(to);
        step(ground);
        step(ground); // a shod hoof counts double
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        updateSpeed(e.getPlayer(), false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        updateSpeed(e.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        updateSpeed(e.getPlayer(), false);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        updateSpeed(e.getPlayer(), false);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        store.flush(e.getChunk(), true);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        plugin.regrowth().catchUp(e.getChunk());
    }

    /** The block the player's feet stand on: a path is 1/16 lower, so the feet are already "in it". */
    static Block groundUnder(Location loc) {
        Block feet = loc.getBlock();
        return feet.getType() == Material.DIRT_PATH ? feet : feet.getRelative(BlockFace.DOWN);
    }

    private static boolean canWear(Player p) {
        GameMode gm = p.getGameMode();
        return (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE)
                && !p.isFlying() && !p.isGliding() && !p.isSwimming() && !p.isInsideVehicle();
    }

    void step(Block ground) {
        // Claim check first: everything below, including melting a snow layer, must obey the
        // owner's wear toggle exactly like every other block change this plugin makes.
        if (!plugin.wearAllowedAt(ground.getLocation())) {
            return;
        }
        if (ground.getBlockData() instanceof org.bukkit.block.data.type.Snow snow && snowTracks) {
            // A snow layer is not a counter-bearing block: snowfall piles it back up vanilla-style,
            // so there is nothing to store here, and any counter for the block under the snow is
            // untouched — it belongs to a different block. This must run before rules.wears(type)
            // below, since snow is never in the configured "grass" set and would otherwise be
            // mistaken for a block that stopped wearing.
            if (snow.getLayers() <= snow.getMinimumLayers()) {
                ground.setType(Material.AIR, true);
            } else {
                snow.setLayers(snow.getLayers() - 1);
                ground.setBlockData(snow, true);
            }
            return;
        }
        // Walking scatters autumn leaf litter. This happens on every step and before every early
        // return below, because the blocks that keep litter longest are the ones that no longer
        // wear at all: a dirt path is both a place litter settles on and the last wear stage.
        Block above = ground.getRelative(BlockFace.UP);
        if (WearRules.scatters(above.getType().name())) {
            above.setType(Material.AIR, true);
        }
        String type = ground.getType().name();
        int packed = WearRules.pack(ground.getX(), ground.getY(), ground.getZ());
        org.bukkit.Chunk chunk = ground.getChunk();
        if (!rules.wears(type)) {
            // MUD is not a wearing block either, but our own mud (WearStore.MUD_MARK) must keep
            // its mark through footsteps — that mark is the only way MudProbe later tells "our
            // mud, dry it back" from natural or player-placed mud, and a path gets walked on by
            // definition. keepsMark also requires the block to still actually be mud, so a stale
            // mark left behind by whatever replaced our mud (stone, a slab, water, ...) falls
            // through to the removal below instead of being trusted or kept forever.
            WearStore.Entry current = store.get(chunk, packed);
            if (current != null && MudProbe.keepsMark(type, current.count())) {
                return;
            }
            store.remove(chunk, packed); // the block was changed into something else — no counter needed anymore
            return;
        }
        if (!above.isPassable() && !above.isEmpty()) {
            return; // under a slab, carpet or structure — does not get trampled
        }
        WearStore.Entry current = store.get(chunk, packed);
        int count = Math.max(current == null ? 0 : current.count(), rules.startCount(type)) + 1;
        String next = rules.next(type, count);
        if (next == null) {
            store.put(chunk, packed, count, Days.today());
            return;
        }
        if (trampled(above.getType())) {
            above.setType(Material.AIR, true);
        }
        if (!above.isEmpty() && next.equals(WearRules.PATH)) {
            return; // a path doesn't form under snow, moss or another block impassable for it
        }
        ground.setType(Material.valueOf(next), true);
        store.put(chunk, packed, count, Days.today());
    }

    private static boolean trampled(Material m) {
        return Tag.REPLACEABLE.isTagged(m) || Tag.SMALL_FLOWERS.isTagged(m) || m == Material.SHORT_GRASS
                || m == Material.FERN || Compat.is(m, Compat.LEAF_LITTER);
    }

    void updateSpeed(Player p, boolean onPath) {
        AttributeInstance inst = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (inst == null) {
            return;
        }
        boolean has = inst.getModifier(speedKey) != null;
        if (onPath && !has) {
            inst.addTransientModifier(new AttributeModifier(speedKey, speedBonus, AttributeModifier.Operation.ADD_SCALAR));
        } else if (!onPath && has) {
            inst.removeModifier(speedKey);
        }
    }
}
