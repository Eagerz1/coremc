package net.coremc.corestaff.command;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * /captcha <player> - open a randomised mob-head captcha to the target.
 * They must click the correct mob among distractors. Configurable timeout/attempts.
 */
public final class CaptchaCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private static final Material[] MOBS = {
            Material.PLAYER_HEAD, Material.CREEPER_HEAD, Material.ZOMBIE_HEAD,
            Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL, Material.PIGLIN_HEAD
    };
    private static final String[] MOB_NAMES = {
            "Panda", "Creeper", "Zombie", "Skeleton", "Wither", "Piglin"
    };

    public CaptchaCommand(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!sender.hasPermission("corestaff.captcha")) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("/captcha <player>");
            return true;
        }
        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            CoreFoundation.getInstance().messages().sendRaw(sender, CoreFoundation.getInstance().messages().getPrefix()
                    + " <red>Player not found.");
            return true;
        }
        open(target);
        CoreMC.getInstance().staff().log(sender.getName() + " sent captcha to " + target.getName());
        CoreFoundation.getInstance().messages().sendRaw(sender, CoreFoundation.getInstance().messages().getPrefix()
                + " <green>Sent captcha to " + target.getName());
        return true;
    }

    private void open(final Player target) {
        final int targetIdx = random.nextInt(MOBS.length);
        final int attempts = plugin.getConfig().getInt("captcha.attempts", 3);
        final int timeout = plugin.getConfig().getInt("captcha.timeout-seconds", 30);

        final InventoryGui gui = new InventoryGui(plugin, 3,
                "<gradient:#00eaff:#7a5cff>Click: " + MOB_NAMES[targetIdx] + "</gradient>", Material.BLACK_STAINED_GLASS_PANE);

        // Place target + distractors in random slots.
        final List<Integer> slots = new ArrayList<>();
        for (int i = 10; i <= 16; i++) {
            slots.add(i);
        }
        java.util.Collections.shuffle(slots, random);
        final List<Integer> chosen = new ArrayList<>(slots.subList(0, Math.min(8, MOBS.length)));
        // Ensure target is included.
        if (!chosen.isEmpty()) {
            gui.setItem(chosen.get(0), head(MOBS[targetIdx], MOB_NAMES[targetIdx]), ev -> success(target));
        }
        int idx = 1;
        for (int i = 0; i < MOBS.length; i++) {
            if (i == targetIdx || idx >= chosen.size()) {
                continue;
            }
            final int mob = i;
            gui.setItem(chosen.get(idx), head(MOBS[i], MOB_NAMES[i]), ev -> fail(target, attempts));
            idx++;
        }
        gui.open(target);

        // If the player closes the GUI themselves (e.g. ESC), re-open it on the next
        // tick so they cannot skip the captcha — they must solve it or let it time out.
        gui.setOnForceClose(v -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (v instanceof Player p && p.isOnline()) {
                open(p);
            }
        }));

        // Timeout.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (target.getOpenInventory().getTopInventory().getHolder() instanceof InventoryGui) {
                gui.suppressForceClose();
                target.closeInventory();
                CoreFoundation.getInstance().messages().sendRaw(target, CoreFoundation.getInstance().messages().getPrefix()
                        + " <red>Captcha timed out.");
            }
        }, timeout * 20L);
    }

    private void success(final Player target) {
        guiFor(target).ifPresent(g -> g.suppressForceClose());
        target.closeInventory();
        CoreFoundation.getInstance().messages().sendRaw(target, CoreFoundation.getInstance().messages().getPrefix()
                + " <green>Captcha passed.");
    }

    private void fail(final Player target, final int attempts) {
        guiFor(target).ifPresent(g -> g.suppressForceClose());
        target.closeInventory();
        CoreFoundation.getInstance().messages().sendRaw(target, CoreFoundation.getInstance().messages().getPrefix()
                + " <red>Captcha failed. (" + attempts + " attempts allowed)");
    }

    /** Find the currently-open InventoryGui for a player (if any). */
    private java.util.Optional<InventoryGui> guiFor(final Player p) {
        final org.bukkit.inventory.Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof InventoryGui g) return java.util.Optional.of(g);
        return java.util.Optional.empty();
    }

    private ItemStack head(final Material mat, final String name) {
        return ItemUtil.create(mat, "<yellow>" + name, null);
    }
}
