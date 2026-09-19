package dev.thathunky.trodden;

import dev.thathunky.trodden.claims.Claims;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /paths on|off|state (aliases: стежки, stezhky) — the owner turns trampling on in their own claim. */
final class StezhkyCommand implements TabExecutor {

    private static final Set<String> STATE_WORDS = Set.of("стан", "status", "state");
    private static final Set<String> ON_WORDS = Set.of("увімкнути", "on");
    private static final Set<String> OFF_WORDS = Set.of("вимкнути", "off");

    private final Trodden plugin;
    private final Messages messages;

    StezhkyCommand(Trodden plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only: you need to be standing in your own claim.");
            return true;
        }
        String sub = args.length == 0 ? "state" : args[0].toLowerCase();
        Claims claims = plugin.claims();
        Optional<Claims.Found> claim = claims == null ? Optional.empty() : claims.at(p.getLocation());
        if (claim.isEmpty()) {
            messages.send(p, "not-in-claim", Map.of());
            return true;
        }
        Claims.Found c = claim.get();
        boolean on = plugin.toggles().enabled(c.key());
        if (STATE_WORDS.contains(sub)) {
            messages.send(p, on ? "paths-state-on" : "paths-state-off", Map.of());
        } else if (ON_WORDS.contains(sub) || OFF_WORDS.contains(sub)) {
            boolean mine = c.owner().isPresent() && c.owner().get().equals(p.getUniqueId());
            if (!mine && !(p.hasPermission("trodden.admin") || p.hasPermission("wear.admin")
                    || p.hasPermission("matsuri.wear.admin"))) {
                messages.send(p, "not-your-claim", Map.of());
                return true;
            }
            boolean want = ON_WORDS.contains(sub);
            plugin.toggles().set(c.key(), want);
            plugin.saveToggles();
            messages.send(p, want ? "paths-on" : "paths-off", Map.of());
        } else {
            messages.send(p, "usage", Map.of());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1
                ? List.of("on", "off", "state", "увімкнути", "вимкнути", "стан")
                : List.of();
    }
}
