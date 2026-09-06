package net.coremc.store.command;

import net.coremc.foundation.CoreFoundation;
import net.coremc.store.CoreStore;
import net.coremc.store.api.CoreStoreApi;
import net.coremc.store.gui.StoreGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /store — opens the store GUI. */
public final class StoreCommand implements CommandExecutor, TabCompleter {

    private final CoreStore plugin;
    private final CoreStoreApi api;

    public StoreCommand(final CoreStore plugin, final net.coremc.store.storage.StoreStorage storage, final CoreStoreApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!(sender instanceof final Player player)) {
            CoreFoundation.getInstance().messages().sendRaw(sender,
                    CoreFoundation.getInstance().messages().getPrefix() + " <red>Players only.");
            return true;
        }
        if (!player.hasPermission("core.store.open") && !player.isOp()) {
            CoreFoundation.getInstance().messages().send(player, "messages.no-permission");
            return true;
        }
        StoreGui.openMain(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        return new ArrayList<>();
    }
}
