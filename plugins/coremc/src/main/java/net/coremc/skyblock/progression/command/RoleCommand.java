package net.coremc.skyblock.progression.command;

import net.coremc.coremc.CoreMC;
import net.coremc.skyblock.progression.ProgressionModule;
import net.coremc.skyblock.progression.role.RoleGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /role - open the role progression GUI (select a role or view its perks/milestones/stats). *
 * <p>Preserves the existing role-selection behaviour: selecting a role permanently grants its
 * Role-bound Set and Omnitool, and never resets any role's progression (XP is stored per-role).
 * The heavy lifting of the view lives in {@link RoleGui}.</p>
 */
public final class RoleCommand implements CommandExecutor {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final ProgressionModule prog;
    private final RoleGui gui;

    public RoleCommand(final org.bukkit.plugin.java.JavaPlugin plugin, final ProgressionModule prog) {
        this.plugin = plugin;
        this.prog = prog;
        this.gui = new RoleGui(prog);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!(sender instanceof final Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        // Open the roles GUI instead of sending "/role" into chat
        gui.openMain(player);
        return true;
    }
}