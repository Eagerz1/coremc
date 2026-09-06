package net.coremc.foundation.gui;
import net.coremc.foundation.CoreFoundation;


import net.coremc.foundation.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Lightweight clickable GUI.
 *
 * <p>Register actions per slot; filler is applied automatically from config.
 * A {@code <prefix>} token in the title is substituted by the plugin prefix.</p>
 */
public final class InventoryGui implements InventoryHolder {

    private final CoreFoundation plugin;
    private final int size;
    private final Component title;
    private final ItemStack[] contents;
    private final Consumer<InventoryClickEvent>[] handlers;
    private Consumer<HumanEntity> onForceClose;
    private boolean suppressForceClose;
    private Inventory inventory;

    @SuppressWarnings("unchecked")
    public InventoryGui(final @NotNull Plugin plugin, final int rows,
                        final @NotNull String titleMiniMessage,
                        final @Nullable Material fillerMaterial) {
        this.plugin = CoreFoundation.getInstance();
        this.size = Math.max(9, Math.min(54, rows * 9));
        this.contents = new ItemStack[size];
        this.handlers = new Consumer[size];
        this.title = net.coremc.foundation.util.ColorUtil.parse(
                titleMiniMessage, this.plugin.messages().getPrefix());

        if (fillerMaterial != null) {
            final ItemStack filler = ItemUtil.create(fillerMaterial,
                    this.plugin.getConfig().getString("gui.filler-name", " "), null);
            for (int i = 0; i < size; i++) {
                contents[i] = filler;
                handlers[i] = ev -> {
                    if (this.plugin.getConfig().getBoolean("gui.close-on-filler-click", true)) {
                        ev.getWhoClicked().closeInventory();
                    }
                };
            }
        }
    }

    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, size, title);
            inventory.setContents(contents.clone());
            CoreFoundation.getInstance().registerGui(this);
        }
        return inventory;
    }

    /** Set the item for a slot (0-based); no click handler. */
    public void setItem(final int slot, final @Nullable ItemStack item) {
        setItem(slot, item, null);
    }

    /** Set the item and click handler for a slot (0-based). */
    public void setItem(final int slot, final @Nullable ItemStack item,
                        final @Nullable Consumer<InventoryClickEvent> onClick) {
        if (slot < 0 || slot >= size) {
            return;
        }
        contents[slot] = item;
        handlers[slot] = onClick;
        if (inventory != null) {
            inventory.setItem(slot, item);
        }
    }

    /** Open this GUI for a viewer and register it. */
    public void open(final @NotNull HumanEntity viewer) {
        viewer.openInventory(getInventory());
    }

    /**
     * Mark that the next programmatic close should NOT trigger {@link #onForceClose}
     * (used by success/fail/timeout paths that intentionally dismiss the GUI).
     */
    public void suppressForceClose() {
        this.suppressForceClose = true;
    }

    /** Set a callback invoked when the player closes the GUI themselves (e.g. ESC). */
    public void setOnForceClose(final @Nullable Consumer<HumanEntity> onForceClose) {
        this.onForceClose = onForceClose;
    }

    void handleClick(final @NotNull InventoryClickEvent event) {
        event.setCancelled(true);
        final int slot = event.getRawSlot();
        if (slot < 0 || slot >= size || slot != event.getSlot()) {
            return;
        }
        final Consumer<InventoryClickEvent> handler = handlers[slot];
        if (handler != null) {
            handler.accept(event);
        }
    }

    void handleClose(final @NotNull InventoryCloseEvent event) {
        CoreFoundation.getInstance().unregisterGui(this);
        inventory = null;
        if (!suppressForceClose && onForceClose != null) {
            onForceClose.accept(event.getPlayer());
        }
    }

    /** Build a GUI from a config section (used by dependent plugins optionally). */
    public static @Nullable Material fillerFromConfig(final @NotNull Plugin plugin) {
        final String name = CoreFoundation.getInstance()
                .getConfig().getString("gui.filler-material", "");
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Material.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
