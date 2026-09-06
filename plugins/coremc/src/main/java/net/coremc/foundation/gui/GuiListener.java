package net.coremc.foundation.gui;
import org.bukkit.plugin.java.JavaPlugin;
import net.coremc.foundation.CoreFoundation;


import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Routes inventory clicks/closes to the registered {@link InventoryGui}.
 */
public final class GuiListener implements Listener {

    private final JavaPlugin plugin;

    public GuiListener(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(final @NotNull InventoryCloseEvent event) {
        final InventoryGui gui = CoreFoundation.getInstance().getGui(event.getInventory());
        if (gui != null) {
            gui.handleClose(event);
        }
    }

    @EventHandler
    public void onClick(final @NotNull InventoryClickEvent event) {
        final InventoryGui gui = CoreFoundation.getInstance().getGui(event.getInventory());
        if (gui != null) {
            gui.handleClick(event);
        }
    }
}
