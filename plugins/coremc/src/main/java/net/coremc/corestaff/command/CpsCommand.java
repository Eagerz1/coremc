package net.coremc.corestaff.command;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.foundation.CoreFoundation;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;
import java.util.UUID;

/**
 * /cps <player> - continuously show the target's clicks-per-second.
 * Disabled by running the command again.
 */
public final class CpsCommand implements org.bukkit.command.CommandExecutor, Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Integer> cpsCounts;
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> cpsTasks;

    public CpsCommand(final JavaPlugin plugin, final Map<UUID, Integer> cpsCounts,
                      final Map<UUID, org.bukkit.scheduler.BukkitTask> cpsTasks) {
        this.plugin = plugin;
        this.cpsCounts = cpsCounts;
        this.cpsTasks = cpsTasks;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!sender.hasPermission("corestaff.cps")) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("/cps <player>");
            return true;
        }
        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            CoreFoundation.getInstance().messages().sendRaw(sender, CoreFoundation.getInstance().messages().getPrefix()
                    + " <red>Player not found.");
            return true;
        }
        final UUID uuid = target.getUniqueId();
        if (cpsTasks.containsKey(uuid)) {
            cpsTasks.remove(uuid).cancel();
            CoreFoundation.getInstance().messages().sendRaw(sender, CoreFoundation.getInstance().messages().getPrefix()
                    + " <yellow>Stopped CPS tracking for " + target.getName());
            return true;
        }
        final org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            final int cps = cpsCounts.getOrDefault(uuid, 0);
            CoreFoundation.getInstance().messages().sendRaw(sender,
                    CoreFoundation.getInstance().messages().getPrefix()
                            + " <aqua>" + target.getName() + " CPS: <white>" + cps);
        }, 20L, 20L);
        cpsTasks.put(uuid, task);
        // Listen for their clicks to count.
        Bukkit.getPluginManager().registerEvents(this, plugin);
        CoreMC.getInstance().staff().log(sender.getName() + " started CPS tracking on " + target.getName());
        return true;
    }

    @EventHandler
    public void onInteract(final PlayerInteractEvent event) {
        final Player p = event.getPlayer();
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            cpsCounts.merge(p.getUniqueId(), 1, Integer::sum);
        }
    }
}
