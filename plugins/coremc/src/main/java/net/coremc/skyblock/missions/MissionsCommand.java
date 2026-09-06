package net.coremc.skyblock.missions;

import net.coremc.foundation.CoreFoundation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /missions — opens the mission browser. /missions claim <id> claims a specific
 * completed mission from chat.
 */
public final class MissionsCommand implements CommandExecutor {

    private final MissionManager missions;

    public MissionsCommand(final MissionManager missions) {
        this.missions = missions;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!(sender instanceof final Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("claim")) {
            missions.claim(player, args[1]);
            return true;
        }
        new MissionsGui(missions).open(player);
        return true;
    }
}
