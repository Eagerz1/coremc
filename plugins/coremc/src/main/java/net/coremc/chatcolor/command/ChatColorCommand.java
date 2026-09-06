package net.coremc.chatcolor.command;

import net.coremc.chatcolor.CoreChatColor;
import net.coremc.chatcolor.manager.ColourDef;
import net.coremc.chatcolor.manager.ColourManager;
import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /chatcolor — main menu with three centred options:
 *   Colours (solid dye colours), Gradients, Tags.
 */
public final class ChatColorCommand implements org.bukkit.command.CommandExecutor,
        org.bukkit.command.TabCompleter {

    private final CoreChatColor plugin;
    private final ColourManager colours;

    public ChatColorCommand(final CoreChatColor plugin, final ColourManager colours) {
        this.plugin = plugin;
        this.colours = colours;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!(sender instanceof final Player player)) {
            CoreFoundation.getInstance().messages().sendRaw(sender,
                    CoreFoundation.getInstance().messages().getPrefix() + " <red>Players only.");
            return true;
        }
        if (!player.hasPermission("core.chatcolor.gui") && !player.isOp()) {
            CoreFoundation.getInstance().messages().send(player, "messages.no-permission");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("bold")) {
            final boolean on = !plugin.colours().boldOn(player.getUniqueId());
            plugin.colours().setBold(player.getUniqueId(), on);
            CoreFoundation.getInstance().messages().sendRaw(player,
                    CoreFoundation.getInstance().messages().getPrefix()
                            + (on ? " <green>Bold chat enabled." : " <gray>Bold chat disabled."));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
            final ColourDef def = colours.byId(args[1]);
            if (def == null) {
                CoreFoundation.getInstance().messages().sendRaw(player,
                        CoreFoundation.getInstance().messages().getPrefix() + " <red>Unknown colour.");
                return true;
            }
            if (!colours.canUse(player, def)) {
                CoreFoundation.getInstance().messages().send(player, "messages.no-permission");
                return true;
            }
            plugin.api().setChatColor(player, def.id());
            CoreFoundation.getInstance().messages().sendRaw(player,
                    CoreFoundation.getInstance().messages().getPrefix()
                    + " <green>Chat colour set to " + def.display() + "<green>.");
            return true;
        }
        openMain(player);
        return true;
    }

    // ---- main menu: 3 centred options ----
    private void openMain(final Player player) {
        final InventoryGui gui = new InventoryGui(plugin, 3,
                "<white>Chat Colour", Material.BLACK_STAINED_GLASS_PANE);
        gui.setItem(11, ItemUtil.create(Material.PAINTING,
                "<yellow>Chat colour", List.of("<gray>Pick a solid chat colour", "<green>Click to open")),
                e -> openColours(player));
        gui.setItem(13, ItemUtil.create(Material.LIGHT_BLUE_CONCRETE,
                "<aqua>Gradients", List.of("<gray>Pick a multi-colour gradient", "<green>Click to open")),
                e -> openGradients(player));
        gui.setItem(15, ItemUtil.create(Material.NAME_TAG,
                "<light_purple>Tags", List.of("<gray>Collectible chat tags", "<green>Click to open")),
                e -> {
                    if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("core-tags")) {
                        plugin.openTagsSection(player);
                    } else {
                        CoreFoundation.getInstance().messages().sendRaw(player, " <red>Tags unavailable.");
                    }
                });

        // Global bold toggle — row 2 centre.
        final boolean bold = colours.boldOn(player.getUniqueId());
        gui.setItem(22, ItemUtil.create(bold ? Material.ENCHANTED_BOOK : Material.BOOK,
                "<bold>Chat Bold: " + (bold ? "<green>ON" : "<gray>OFF"),
                List.of("<gray>Toggle bold chat for every message.",
                        bold ? "<green>Currently enabled." : "<gray>Currently disabled.",
                        "<aqua>Click to toggle.")),
                e -> { e.setCancelled(true);
                    plugin.colours().setBold(player.getUniqueId(), !bold);
                    openMain(player);
                });
        gui.open(player);
    }

    // ---- solid colours: actual dye items in a clean grid ----
    private void openColours(final Player player) {
        final List<ColourDef> solids = new ArrayList<>();
        for (final ColourDef d : colours.catalogue()) {
            if (d.kind() == ColourDef.Kind.SOLID) {
                solids.add(d);
            }
        }
        final InventoryGui gui = new InventoryGui(plugin, 6,
                "<yellow>Chat Colours", Material.BLACK_STAINED_GLASS_PANE);
        int slot = 0;
        for (final ColourDef def : solids) {
            final boolean unlocked = colours.canUse(player, def);
            final boolean selected = def.id().equalsIgnoreCase(colours.getSelected(player.getUniqueId()));
            final ColourDef chosen = def;
            final Material mat = def.material() != null ? def.material() : Material.WHITE_DYE;
            gui.setItem(slot++, buildItem(mat, def, unlocked, selected), e -> {
                e.setCancelled(true);
                if (!unlocked) {
                    CoreFoundation.getInstance().messages().sendRaw(player,
                            CoreFoundation.getInstance().messages().getPrefix()
                            + " <red>Locked. Requires <yellow>"
                            + (chosen.requiredNote().isEmpty() ? chosen.permission() : chosen.requiredNote()));
                    return;
                }
                plugin.api().setChatColor(player, chosen.id());
                CoreFoundation.getInstance().messages().sendRaw(player,
                        CoreFoundation.getInstance().messages().getPrefix()
                        + " <green>Chat colour set to " + chosen.display() + "<green>.");
                openColours(player);
            });
        }
        addClear(gui, player, this::openColours);
        addBack(gui, player, this::openMain);
        gui.open(player);
    }

    // ---- gradients: enchanted concrete items ----
    private void openGradients(final Player player) {
        final List<ColourDef> grads = new ArrayList<>();
        for (final ColourDef d : colours.catalogue()) {
            if (d.kind() == ColourDef.Kind.GRADIENT) {
                grads.add(d);
            }
        }
        final InventoryGui gui = new InventoryGui(plugin, 6,
                "<aqua>Gradients", Material.BLACK_STAINED_GLASS_PANE);
        int slot = 0;
        for (final ColourDef def : grads) {
            final boolean unlocked = colours.canUse(player, def);
            final boolean selected = def.id().equalsIgnoreCase(colours.getSelected(player.getUniqueId()));
            final ColourDef chosen = def;
            gui.setItem(slot++, buildItem(Material.WHITE_CONCRETE, def, unlocked, selected), e -> {
                e.setCancelled(true);
                if (!unlocked) {
                    CoreFoundation.getInstance().messages().sendRaw(player,
                            CoreFoundation.getInstance().messages().getPrefix()
                            + " <red>Locked. Requires <yellow>"
                            + (chosen.requiredNote().isEmpty() ? chosen.permission() : chosen.requiredNote()));
                    return;
                }
                plugin.api().setChatColor(player, chosen.id());
                CoreFoundation.getInstance().messages().sendRaw(player,
                        CoreFoundation.getInstance().messages().getPrefix()
                        + " <green>Gradient set to " + chosen.display() + "<green>.");
                openGradients(player);
            });
        }
        addClear(gui, player, this::openGradients);
        addBack(gui, player, this::openMain);
        gui.open(player);
    }

    // ---- shared item builder (glows + lore) ----
    private ItemStack buildItem(final Material mat, final ColourDef def,
                                final boolean unlocked, final boolean selected) {
        final List<String> lore = new ArrayList<>();
        if (selected) {
            lore.add("<green>Selected");
        } else if (!unlocked) {
            lore.add("<red>Locked");
            lore.add("<gray>Needs: " + (def.requiredNote().isEmpty() ? def.permission() : def.requiredNote()));
        } else {
            lore.add("<yellow>Click to select");
        }
        final ItemStack item = ItemUtil.create(unlocked ? mat : Material.GRAY_DYE, def.display(), lore);
        if (selected) {
            addGlow(item);
        }
        return item;
    }

    private void addClear(final InventoryGui gui, final Player player, final java.util.function.Consumer<Player> reOpen) {
        gui.setItem(48, ItemUtil.create(Material.BARRIER,
                "<red>Clear colour", List.of("<gray>Reset to default chat colour")), e -> {
            e.setCancelled(true);
            plugin.api().setChatColor(player, "");
            CoreFoundation.getInstance().messages().sendRaw(player,
                    CoreFoundation.getInstance().messages().getPrefix() + " <green>Chat colour cleared.");
            reOpen.accept(player);
        });
    }

    private void addBack(final InventoryGui gui, final Player player, final java.util.function.Consumer<Player> back) {
        gui.setItem(50, ItemUtil.create(Material.ARROW,
                "<gray>Back", null), e -> {
            e.setCancelled(true);
            back.accept(player);
        });
    }

    private static void addGlow(final ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (args.length == 1) {
            return List.of("set", "bold");
        }
        return new ArrayList<>();
    }
}
