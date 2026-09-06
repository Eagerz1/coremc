package net.coremc.corestaff.command;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.foundation.CoreFoundation;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/**
 * /invsee <player> - open the target's inventory read/write to the staff.
 */
public final class InvseeCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;

    public InvseeCommand(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!sender.hasPermission("corestaff.invsee")) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("/invsee <player>");
            return true;
        }
        if (!(sender instanceof final Player staff)) {
            sender.sendMessage("Console cannot open inventories.");
            return true;
        }
        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            CoreFoundation.getInstance().messages().sendRaw(staff, CoreFoundation.getInstance().messages().getPrefix()
                    + " <red>Player not found.");
            return true;
        }
        staff.openInventory(target.getInventory());
        CoreMC.getInstance().staff().log(staff.getName() + " opened " + target.getName() + "'s inventory.");
        return true;
    }
}
