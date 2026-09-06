package net.coremc.store.command;

import net.coremc.foundation.CoreFoundation;
import net.coremc.store.CoreStore;
import net.coremc.store.credits.CreditsManager;
import net.coremc.store.storage.StoreStorage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /storeclaim — claim undelivered rewards. */
public final class StoreClaimCommand implements CommandExecutor, TabCompleter {

    private final CoreStore plugin;
    private final StoreStorage storage;
    private final CreditsManager credits;

    public StoreClaimCommand(final CoreStore plugin, final StoreStorage storage, final CreditsManager credits) {
        this.plugin = plugin;
        this.storage = storage;
        this.credits = credits;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!(sender instanceof final Player p)) {
            CoreFoundation.getInstance().messages().sendRaw(sender,
                    CoreFoundation.getInstance().messages().getPrefix() + " <red>Players only.");
            return true;
        }
        CoreStore.getInstance().api().claimPendingRewards(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        return new ArrayList<>();
    }
}
