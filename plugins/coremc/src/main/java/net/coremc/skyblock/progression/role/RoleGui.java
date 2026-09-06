package net.coremc.skyblock.progression.role;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.ColorUtil;
import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.omnitools.tool.ToolManager;
import net.coremc.skyblock.progression.ProgressionModule;
import net.coremc.skyblock.progression.role.RoleManager.Role;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Improved role progression GUI.
 * Shows, for the opened role:
 *   <li>Current role / level / XP / XP-required + a progress bar.
 *   <li>The next-level reward (milestone) so the player sees what grinding gets them.
 *   <li>Unlocked perks (green) and locked perks (gray, with unlock level).
 *   <li>Upcoming milestones (level + reward summary).
 *   <li>Per-role statistics (blocks / crops / logs / fish / mobs / xp).
 * Uses existing CoreMC GUI styling (bold titles, normal font, proper colours, item materials
 * matching the role). Opening a different role from the overview never resets progression.
 * GUI is a small chest (27 slots) with deliberate spacing between role options.
 */
public final class RoleGui {
    private final ProgressionModule prog;

    public RoleGui(final ProgressionModule prog) {
        this.prog = prog;
    }

    private RoleProgression rp() { return prog.roleProgression(); }
    private RoleManager roles() { return prog.roles(); }
    private RoleMilestone milestones() { return rp().milestones(); }

    private static String roleColour(final Role role) {
        return switch (role) {
            case MINING -> "blue";
            case FARMING -> "green";
            case FISHING -> "aqua";
            case LOGGING -> "dark_green";
            case SLAYING -> "red";
            case UNIVERSAL -> "gold";
        };
    }

    private static Material roleMaterial(final Role role) {
        return switch (role) {
            case MINING -> Material.DIAMOND_PICKAXE;
            case FARMING -> Material.DIAMOND_HOE;
            case FISHING -> Material.FISHING_ROD;
            case LOGGING -> Material.DIAMOND_AXE;
            case SLAYING -> Material.DIAMOND_SWORD;
            case UNIVERSAL -> Material.NETHER_STAR;
        };
    }

    /** Open the role-selection overview (small chest: 27 slots). */
    public void openMain(final Player player) {
        final InventoryGui gui = new InventoryGui(CoreMC.getInstance(), 27,
                "<bold><white>Roles</white></bold>", Material.BLACK_STAINED_GLASS_PANE);
        final UUID uuid = player.getUniqueId();
        int slot = 0;
        final ToolManager tm = CoreMC.getInstance().omnitools().tools();
        for (final Role role : Role.values()) {
            final boolean selected = roles().get(uuid) == role;
            final long xp = roles().getXp(uuid, role.tree());
            final int lvl = roles().getLevel(uuid, role.tree());
            final long toNext = roles().xpToNext(uuid, role.tree());
            final String col = roleColour(role);
            final List<String> lore = new ArrayList<>();
            lore.add("<gray>Level: <white>" + lvl + "</white> / <white>" + roles().maxLevel() + "</white>");
            lore.add("<gray>XP: <white>" + FormatUtil.formatNumber(xp) + "</white>");
            lore.add(toNext < 0 ? "<green>MAX LEVEL</green>" : "<gray>XP to next: <white>" + FormatUtil.formatNumber(toNext) + "</white>");
            lore.add("<dark_gray>" + roles().progressBar(uuid, role.tree(), 12));
            lore.add(selected ? "<green>■ Selected role</>" : "<yellow>Click to select</>");
            final Role r = role;
            gui.setItem(slot, ItemUtil.create(roleMaterial(role),
                    "<bold><" + col + ">" + role.display() + "</" + col + "></bold>", lore), ev -> {
                // Get the player's current role before switching
                final String previousRole = roles().get(uuid) != null ? roles().get(uuid).name() : null;

                // Switch to the new role
                prog.roles().set(uuid, r);
                prog.roleSets().grantRoleSet(player, r);

                // Remove the old role's Omnitool from inventory (preserving progression)
                if (previousRole != null && previousRole != r.name()) {
                    // Find and remove any existing OmniTool for the previous role
                    for (int i = 0; i < player.getInventory().getSize(); i++) {
                        final ItemStack item = player.getInventory().getItem(i);
                        if (item != null && tm.roleOf(item) != null && tm.roleOf(item).equals(previousRole)) {
                            player.getInventory().setItem(i, null);
                        }
                    }
                }

                // Give the new role's Omnitool
                final ItemStack tool = tm.makeTool(r.name());
                if (tool != null) player.getInventory().addItem(tool);

                CoreFoundation.getInstance().messages().sendRaw(player,
                        CoreFoundation.getInstance().messages().getPrefix() + " <green>Role set to " + r.display()
                                + ". You received the " + r.display() + " Omnitool.</green>");
                openDetail(player, r);
            });
            slot += 4;  // Space out roles with gaps between them
        }
        // Fill remaining slots with black stained glass for spacing
        for (int i = Role.values().length * 4; i < 27; i++) {
            gui.setItem(i, ItemUtil.create(Material.BLACK_STAINED_GLASS_PANE, "", null));
        }
        gui.open(player);
    }

    /** Open the detailed progression view for a single role. */
    public void openDetail(final Player player, final Role role) {
        final UUID uuid = player.getUniqueId();
        final InventoryGui gui = new InventoryGui(CoreMC.getInstance(), 6,
                "<bold><" + roleColour(role) + ">" + role.display() + " Role</" + roleColour(role) + "</bold>", Material.BLACK_STAINED_GLASS_PANE);

        final long xp = roles().getXp(uuid, role.tree());
        final int lvl = roles().getLevel(uuid, role.tree());
        final long toNext = roles().xpToNext(uuid, role.tree());
        final int max = roles().maxLevel();

        // ---- Header: role / level / xp / progress bar ----
        final List<String> head = new ArrayList<>();
        head.add("<gray>Level: <white>" + lvl + "</white> / <white>" + max + "</white>");
        head.add("<gray>XP: <white>" + FormatUtil.formatNumber(xp) + "</white>");
        head.add(toNext < 0
                ? "<green>MAX LEVEL — all milestones claimed</green>"
                : "<gray>XP to next level: <white>" + FormatUtil.formatNumber(toNext) + "</white>");
        head.add("<dark_gray>" + roles().progressBar(uuid, role.tree(), 30));
        head.add("<dark_gray>Total XP: <white>" + FormatUtil.formatNumber(xp) + "</white>");
        gui.setItem(4, ItemUtil.create(roleMaterial(role),
                "<bold><" + roleColour(role) + ">" + role.display() + " Progress</" + roleColour(role) + "></bold>",
                head), ev -> {});

        // ---- Next-level reward (what you get for continuing) ----
        final int next = milestones().nextMilestone(role, uuid, lvl);
        final List<String> nxt = new ArrayList<>();
        if (next < 0) {
            nxt.add("<green>You have reached every milestone for this role.</green>");
            nxt.add("<gray>Keep grinding for the passive perks.");
        } else {
            nxt.add("<gray>Next milestone at <white>Level " + next + "</white>:");
            for (final String r : milestones().rewardSummary(role, next)) {
                nxt.add("<white>• " + r);
            }
        }
        gui.setItem(13, ItemUtil.create(Material.NETHER_STAR, "<bold><gold>Next Reward</gold></bold>", nxt), ev -> {});

        // ---- Perks (unlocked green / locked gray) ----
        int pSlot = 18;
        final int lvlN = lvl;
        for (final RolePerk p : rp().modifiers().registryForGui().forRole(role.name())) {
            final boolean unlocked = p.unlockedAt(lvlN);
            final String col = unlocked ? "green" : "dark_gray";
            final List<String> pl = new ArrayList<>();
            pl.add("<gray>" + p.description);
            pl.add(unlocked ? "<green>UNLOCKED</>" : "<gray>Unlocks at Level <white>" + p.unlockLevel + "</white>");
            gui.setItem(pSlot++, ItemUtil.create(unlocked ? Material.ENCHANTED_BOOK : Material.BOOK,
                    "<bold><" + col + ">" + p.name + "</" + col + "></bold>", pl), ev -> {});
            if (pSlot == 26) pSlot = 27; // keep tidy, wrap to next row
            if (pSlot > 35) break;
        }

        // ---- Upcoming milestones (right column) ----
        int mSlot = 38;
        final List<Integer> allMs = milestones().milestoneLevels(role);
        int shown = 0;
        for (final int m : allMs) {
            if (mSlot > 44 || shown >= 7) break;
            final boolean claimed = roles().hasClaimedMilestone(uuid, role, m);
            final String col = claimed ? "dark_gray" : "gold";
            final List<String> ml = new ArrayList<>();
            ml.add(claimed ? "<dark_gray>Claimed</>" : "<gray>Reach Level " + m);
            for (final String r : milestones().rewardSummary(role, m)) ml.add("<white>• " + r);
            gui.setItem(mSlot++, ItemUtil.create(claimed ? Material.LEATHER_HELMET : Material.TOTEM_OF_UNDYING,
                    "<bold><" + col + ">" + "Milestone " + m + "</" + col + "></bold>", ml), ev -> {});
            shown++;
        }

        // ---- Statistics (bottom row) ----
        final var stats = rp().stats().snapshot(uuid, role);
        final List<String> st = new ArrayList<>();
        st.add("<gray>Blocks: <white>" + FormatUtil.formatNumber(stats.getOrDefault("blocks", 0L)));
        st.add("<gray>Logs: <white>" + FormatUtil.formatNumber(stats.getOrDefault("logs", 0L)));
        st.add("<gray>Crops: <white>" + FormatUtil.formatNumber(stats.getOrDefault("crops", 0L)));
        st.add("<gray>Fish: <white>" + FormatUtil.formatNumber(stats.getOrDefault("fish", 0L)));
        st.add("<gray>Mobs: <white>" + FormatUtil.formatNumber(stats.getOrDefault("mobs", 0L)));
        st.add("<gray>XP earned: <white>" + FormatUtil.formatNumber(stats.getOrDefault("xp", 0L)));
        gui.setItem(49, ItemUtil.create(Material.PAPER, "<bold><aqua>Statistics</aqua></bold>", st), ev -> {});

        // ---- Back ----
        gui.setItem(45, ItemUtil.create(Material.ARROW, "<gray>Back to Roles</gray>", null), ev -> openMain(player));

        gui.open(player);
    }
}