package net.coremc.skyblock.omnitools.command;

import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.omnitools.gui.OmniMenu;
import org.bukkit.entity.Player;

import java.util.List;

/** /tools - open the Omnitool menu for the player's current role. */
public final class ToolsCommand implements org.bukkit.command.CommandExecutor,
        org.bukkit.command.TabCompleter {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final OmniMenu menu;

    public ToolsCommand(final org.bukkit.plugin.java.JavaPlugin plugin, final OmniMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(final org.bukkit.command.CommandSender sender,
                             final org.bukkit.command.Command cmd, final String label,
                             final String[] args) {
        if (!(sender instanceof final Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        menu.openMain(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(final org.bukkit.command.CommandSender sender,
                                      final org.bukkit.command.Command cmd, final String label,
                                      final String[] args) {
        return List.of();
    }
}
