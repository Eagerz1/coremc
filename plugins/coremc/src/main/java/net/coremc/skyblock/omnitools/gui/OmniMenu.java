package net.coremc.skyblock.omnitools.gui;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.ColorUtil;
import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.omnitools.OmniConfig;
import net.coremc.skyblock.omnitools.ability.AbilityManager;
import net.coremc.skyblock.omnitools.ability.OmniAbility;
import net.coremc.skyblock.omnitools.perk.OmniPerk;
import net.coremc.skyblock.omnitools.stat.ModifierEngine;
import net.coremc.skyblock.omnitools.stat.Stat;
import net.coremc.skyblock.omnitools.tool.RoleCurrencyManager;
import net.coremc.skyblock.omnitools.tool.ToolManager;
import net.coremc.skyblock.omnitools.tool.ToolProgress;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Omnitool GUIs: main menu (double chest), upgrade menu (small chest), ability menu,
 * and rebirth menu. Style matches the rest of CoreMC (bold titles, no italics, normal
 * font, readable layout).
 *
 * <p>Main menu clearly shows: selected role, Role Level, Role XP (progress), OmniTool
 * (Tool) Level, Tool XP (progress), current bonuses, unlocked + locked perks, and
 * unlocked + locked abilities — with an Activate button for a ready ability.</p>
 */
public final class OmniMenu {

    private final ToolManager tools;
    private final RoleCurrencyManager currency;
    private final AbilityManager abilities;
    private final FileConfiguration omniCfg;

    public OmniMenu(final ToolManager tools, final AbilityManager abilities, final OmniConfig omni) {
        this.tools = tools;
        this.currency = tools.currency();
        this.abilities = abilities;
        this.omniCfg = omni.config();
    }

    private String currentRole(final Player p) {
        final var r = CoreMC.getInstance().progression().roles().get(p.getUniqueId());
        return r == null ? "MINING" : r.name();
    }

    public void openMain(final Player p) {
        openMain(p, currentRole(p));
    }

    public void openMain(final Player p, final String role) {
            final InventoryGui gui = new InventoryGui(p.getServer().getPluginManager().getPlugin("CoreMC"),
                    6, "<bold><gold>Omnitools</gold></bold>  -  " + roleName(role),
                    Material.BLACK_STAINED_GLASS_PANE);
            final UUID uuid = p.getUniqueId();

            // ---- Header: Role + Tool overview ----
            final int roleLevel = CoreMC.getInstance().progression().roles().getLevel(uuid, role.toLowerCase());
            final long roleXp = CoreMC.getInstance().progression().roles().getXp(uuid, role.toLowerCase());
            final long roleToNext = CoreMC.getInstance().progression().roles().xpToNext(uuid, role.toLowerCase());
            final int toolLevel = tools.toolLevel(uuid, role);
            final long toolToNext = tools.toolXpToNext(uuid, role);
            final long[] win = tools.toolXpWindow(uuid, role);
            final long cur = currency.get(uuid, role);
            final ToolProgress prog = tools.get(uuid, role);
            final var snap = tools.buildModifiers(uuid, role, abilities.activeModifiers(uuid, role));

            final List<String> head = new ArrayList<>();
            head.add("<gray>Role: <white>" + roleName(role) + "</white>");
            head.add("<gray>Role Level: <white>" + roleLevel + "</white>");
            head.add(roleToNext < 0
                    ? "<gray>Role XP: <white>MAXED</white>"
                    : "<gray>Role XP: <white>" + FormatUtil.formatNumber(roleXp) + "</white>/<white>"
                        + FormatUtil.formatNumber(roleXp + roleToNext) + "</white>");
            head.add(progressBar(CoreMC.getInstance().progression().roles().progress(uuid, role.toLowerCase()), 14)
                    + " <dark_gray>(role)</dark_gray>");
            head.add("<gray>Tool Level: <white>" + toolLevel + "</white>"
                    + (toolLevel >= tools.toolLevelCap() ? " <gold>(MAX)</gold>" : ""));
            head.add(toolToNext < 0
                    ? "<gray>Tool XP: <white>MAXED</white>"
                    : "<gray>Tool XP: <white>" + FormatUtil.formatNumber(win[0]) + "</white>/<white>"
                        + FormatUtil.formatNumber(win[1]) + "</white>");
            head.add(progressBar(tools.toolXpProgress(uuid, role), 14) + " <dark_gray>(tool)</dark_gray>");
            head.add("<gray>" + roleName(role) + " Currency: <yellow>" + FormatUtil.formatNumber(cur) + "</yellow>");
            head.add("<gray>Rebirths: <light_purple>" + prog.rebirthCount() + "</light_purple>");
            gui.setItem(4, ItemUtil.create(Material.EXPERIENCE_BOTTLE,
                    "<bold><gold>" + roleName(role) + " Omnitool</gold></bold>", head),
                    ev -> openMain(p, role));

            // ---- Middle: upgrades (existing system, unchanged behaviour) ----
        final List<String> upgrades = omniCfg
                .getConfigurationSection("omnitools.upgrades") == null ? new ArrayList<>()
                : new ArrayList<>(omniCfg
                    .getConfigurationSection("omnitools.upgrades").getKeys(false));
        int slot = 18;
        for (final String up : upgrades) {
            if (slot > 44) break;
            final var sec = omniCfg.getConfigurationSection("omnitools.upgrades." + up);
            if (sec == null) continue;
            final int lvl = tools.getUpgradeLevel(uuid, role, up);
            final int max = sec.getInt("max-level", 1);
            final long nextCost = tools.costFor(uuid, role, up, 1);
            final boolean maxed = lvl >= max;
            final String col = maxed ? "dark_gray" : (lvl > 0 ? "green" : "gray");
            final List<String> l = new ArrayList<>();
            l.add("<gray>" + sec.getString("description", "") + "</gray>");
            l.add("<gray>Level: <white>" + lvl + "</white>/<white>" + max + "</white>");
            l.add("<gray>Effect: <white>" + sec.getString("effect", "") + "</white>");
            l.add(maxed ? "<dark_gray>MAXED</dark_gray>"
                       : "<yellow>Cost: " + FormatUtil.formatNumber(nextCost) + " " + roleName(role) + " Currency</yellow>");
            l.add("<dark_gray>Click to upgrade.</dark_gray>");
            final ItemStack it = ItemUtil.create(Material.ENCHANTED_BOOK,
                    "<bold><" + col + ">" + sec.getString("name", up) + "</" + col + "></bold>", l);
            final String upId = up;
            gui.setItem(slot, it, ev -> openUpgrade(p, role, upId));
            slot += (slot % 9 == 25) ? 2 : 1;
        }

        // ---- Perks panel (locked + unlocked) ----
        final List<OmniPerk> perks = tools.perkRegistry().forRole(role);
        int perkSlot = 27;
        for (final OmniPerk pk : perks) {
            if (perkSlot > 44) break;
            final boolean unlocked = pk.unlockedAt(toolLevel);
            final String col = unlocked ? (pk.hidden ? "dark_gray" : "green") : (pk.hidden ? "dark_gray" : "gray");
            final List<String> l = new ArrayList<>();
            l.add("<gray>" + pk.description + "</gray>");
            l.add("<gray>Unlocks at Tool Level <white>" + pk.unlockLevel + "</white></gray>");
            if (!pk.effectLine().isEmpty()) l.add("<gray>Effect: <white>" + pk.effectLine() + "</white>");
            if (pk.hidden) l.add("<dark_gray>(hidden perk)</dark_gray>");
            l.add(unlocked ? "<green>UNLOCKED</green>" : "<red>LOCKED</red>");
            gui.setItem(perkSlot, ItemUtil.create(
                    unlocked ? Material.EMERALD : Material.RED_STAINED_GLASS_PANE,
                    "<bold><" + col + ">" + pk.name + "</" + col + "></bold>", l), ev -> {});
            perkSlot += (perkSlot % 9 == 25) ? 2 : 1;
        }

        // ---- Abilities panel + Activate button ----
        final List<OmniAbility> abs = abilities.registry().forRole(role);
        int abSlot = 18 + 9 * 5; // row 6 start area not used; place in row 5 right side
        for (final OmniAbility a : abs) {
            final boolean unlocked = a.unlockedAt(toolLevel);
            final long cd = abilities.cooldownLeft(uuid, a.id);
            final List<String> l = new ArrayList<>();
            l.add("<gray>" + a.description + "</gray>");
            l.add("<gray>Cooldown: <white>" + a.cooldownSeconds + "s</white>"
                    + (a.durationSeconds > 0 ? "  Duration: <white>" + a.durationSeconds + "s</white>" : "") + "</gray>");
            l.add("<gray>Unlocks at Tool Level <white>" + a.unlockLevel + "</white></gray>");
            if (unlocked) {
                if (cd > 0) l.add("<red>On cooldown: " + cd + "s</red>");
                else if (abilities.isActive(uuid, a.id)) l.add("<aqua>ACTIVE</aqua>");
                else l.add("<green>READY - Activate below</green>");
            } else {
                l.add("<red>LOCKED</red>");
            }
            gui.setItem(53, ItemUtil.create(
                    unlocked ? (cd > 0 ? Material.CLOCK : Material.NETHER_STAR) : Material.GRAY_DYE,
                    "<bold><" + (unlocked ? "light_purple" : "dark_gray") + ">" + a.name + "</" + (unlocked ? "light_purple" : "dark_gray") + "></bold>", l),
                    ev -> openAbilities(p, role));
        }

        // ---- Bottom controls ----
        gui.setItem(48, ItemUtil.create(Material.EMERALD_BLOCK, "<bold><green>MAX</green></bold>",
                List.of("<gray>Max all upgrades you can afford.")), ev -> {
            int total = 0;
            for (final String up : upgrades) total += tools.purchaseUpgradeMax(uuid, role, up);
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix() + " <green>Upgraded " + total + " levels.</green>");
            openMain(p, role);
        });
        gui.setItem(49, ItemUtil.create(Material.BARRIER, "<bold><red>Close</red></bold>",
                List.of("<gray>Close this menu.")), ev -> p.closeInventory());
        gui.setItem(50, ItemUtil.create(Material.NETHER_STAR, "<bold><light_purple>Rebirth</light_purple></bold>",
                List.of("<gray>Reset upgrades for permanent perks.", tools.canRebirth(uuid, role)
                        ? "<green>Ready! Click to rebirth." : "<red>Requires Tool Level "
                            + omniCfg.getInt("omnitools.rebirth.required-tool-level", 50)
                            + " + 60 upgrade levels.")), ev -> openRebirth(p, role));
        gui.setItem(45, ItemUtil.create(Material.FIREWORK_STAR, "<bold><light_purple>Activate Ability</light_purple></bold>",
                List.of("<gray>Trigger your ready OmniTool ability.", "<dark_gray>Or Shift+Right-Click with the tool.</dark_gray>")), ev -> {
            boolean fired = false;
            for (final var a : abilities.registry().forRole(role)) {
                if (abilities.isUnlocked(uuid, role, a.id) && abilities.cooldownLeft(uuid, a.id) <= 0) {
                    abilities.trigger(p, role, a.id);
                    fired = true;
                    break;
                }
            }
            if (!fired) CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix() + " <red>No ability ready.</red>");
            openMain(p, role);
        });
        gui.setItem(53, ItemUtil.create(Material.COMPASS, "<bold><aqua>Roles</aqua></bold>",
                List.of("<gray>Open /roles to pick your role.")), ev -> {
            p.closeInventory();
            p.performCommand("roles");
        });

        gui.open(p);
    }

    public void openAbilities(final Player p, final String role) {
        final InventoryGui gui = new InventoryGui(p.getServer().getPluginManager().getPlugin("CoreMC"),
                3, "<bold><light_purple>Abilities: " + roleName(role) + "</light_purple></bold>",
                Material.BLACK_STAINED_GLASS_PANE);
        final UUID uuid = p.getUniqueId();
        int slot = 10;
        for (final OmniAbility a : abilities.registry().forRole(role)) {
            if (slot > 25 || slot % 9 == 17) slot++;
            final boolean unlocked = a.unlockedAt(tools.toolLevel(uuid, role));
            final long cd = abilities.cooldownLeft(uuid, a.id);
            final List<String> l = new ArrayList<>();
            l.add("<gray>" + a.description + "</gray>");
            l.add("<gray>Cooldown: <white>" + a.cooldownSeconds + "s</white>  Unlock: <white>" + a.unlockLevel + "</white></gray>");
            if (unlocked) {
                if (cd > 0) l.add("<red>On cooldown: " + cd + "s</red>");
                else l.add("<green>READY</green>");
            } else l.add("<red>LOCKED</red>");
            gui.setItem(slot, ItemUtil.create(
                    unlocked ? (cd > 0 ? Material.CLOCK : Material.NETHER_STAR) : Material.GRAY_DYE,
                    "<bold><" + (unlocked ? "light_purple" : "dark_gray") + ">" + a.name + "</" + (unlocked ? "light_purple" : "dark_gray") + "></bold>", l),
                    ev -> {
                        if (unlocked && cd <= 0) abilities.trigger(p, role, a.id);
                        openAbilities(p, role);
                    });
            slot += 2;
        }
        gui.setItem(4, ItemUtil.create(Material.BOOK, "<bold><gold>Abilities</gold></bold>",
                List.of("<gray>Active abilities unlocked by Tool Level.", "<gray>Trigger from here or Shift+Right-Click.")), ev -> {});
        gui.setItem(22, ItemUtil.create(Material.BARRIER, "<bold><red>Back</red></bold>",
                List.of("<gray>Back to Omnitool menu.")), ev -> openMain(p, role));
        gui.open(p);
    }

    public void openUpgrade(final Player p, final String role, final String up) {
        final InventoryGui gui = new InventoryGui(p.getServer().getPluginManager().getPlugin("CoreMC"),
                3, "<bold><gold>Upgrade: " + upgradeName(up) + "</gold></bold>",
                Material.BLACK_STAINED_GLASS_PANE);
        final UUID uuid = p.getUniqueId();
        final var sec = omniCfg.getConfigurationSection("omnitools.upgrades." + up);

        final int[] midSlots = {11, 12, 13, 14, 15, 16, 17, 18, 19};
        final int[] amounts = {1, 2, 5, 10, 25, 50, 100, 500, 1000};
        for (int i = 0; i < amounts.length && i < midSlots.length; i++) {
            final int amt = amounts[i];
            final long cost = tools.costFor(uuid, role, up, amt);
            final long have = currency.get(uuid, role);
            final boolean afford = have >= cost && tools.getUpgradeLevel(uuid, role, up) < sec.getInt("max-level", 1);
            final String col = afford ? "green" : "dark_gray";
            gui.setItem(midSlots[i], ItemUtil.create(Material.PAPER,
                    "<bold><" + col + ">+" + amt + "</" + col + "></bold>",
                    List.of("<gray>Cost: <yellow>" + FormatUtil.formatNumber(cost) + "</yellow> " + roleName(role) + " Currency",
                            afford ? "<gray>Click to buy.</gray>" : "<red>Not enough / maxed.</red>")),
                    ev -> {
                        final int bought = tools.purchaseUpgrade(uuid, role, up, amt);
                        if (bought > 0) CoreFoundation.getInstance().messages().sendRaw(p,
                                CoreFoundation.getInstance().messages().getPrefix()
                                        + " <green>Bought " + bought + " level(s) of " + upgradeName(up) + ".</green>");
                        openUpgrade(p, role, up);
                    });
        }

        final int lvl = tools.getUpgradeLevel(uuid, role, up);
        gui.setItem(4, ItemUtil.create(Material.ENCHANTED_BOOK,
                "<bold><gold>" + upgradeName(up) + "</gold></bold>",
                List.of("<gray>" + sec.getString("description", "") + "</gray>",
                        "<gray>Level: <white>" + lvl + "</white>/<white>" + sec.getInt("max-level", 1) + "</white>",
                        "<gray>Effect: <white>" + sec.getString("effect", "") + "</white>",
                        "<gray>Cost currency: <yellow>" + roleName(role) + "</yellow></gray>")), ev -> {});

        gui.setItem(48, ItemUtil.create(Material.EMERALD_BLOCK, "<bold><green>MAX</green></bold>",
                List.of("<gray>Buy as many as you can afford.")), ev -> {
            final int bought = tools.purchaseUpgradeMax(uuid, role, up);
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix() + " <green>Bought " + bought + " level(s).</green>");
            openUpgrade(p, role, up);
        });
        gui.setItem(49, ItemUtil.create(Material.BARRIER, "<bold><red>Close</red></bold>",
                List.of("<gray>Back to Omnitool menu.")), ev -> openMain(p, role));
        gui.setItem(50, ItemUtil.create(Material.NETHER_STAR, "<bold><light_purple>Rebirth</light_purple></bold>",
                List.of("<gray>Open rebirth menu.")), ev -> openRebirth(p, role));

        gui.open(p);
    }

    public void openRebirth(final Player p, final String role) {
        final InventoryGui gui = new InventoryGui(p.getServer().getPluginManager().getPlugin("CoreMC"),
                3, "<bold><light_purple>Rebirth</light_purple></bold>",
                Material.BLACK_STAINED_GLASS_PANE);
        final UUID uuid = p.getUniqueId();
        final boolean ready = tools.canRebirth(uuid, role);

        final List<String> lore = new ArrayList<>();
        lore.add("<gray>Reset ALL upgrade levels on this Omnitool.");
        lore.add("<gray>Keep Tool Level, Role Level and currency.");
        lore.add("<gray>Gain permanent perks (stack each rebirth):</gray>");
        final var perks = omniCfg.getConfigurationSection("omnitools.rebirth.perks");
        if (perks != null) {
            for (final String k : perks.getKeys(false)) {
                lore.add("<light_purple>+" + (perks.getDouble(k) * 100) + "% " + prettyPerk(k) + "</light_purple>");
            }
        }
        lore.add(ready ? "<green>READY - click to rebirth.</green>" : "<red>Not ready yet.</red>");
        gui.setItem(13, ItemUtil.create(Material.NETHER_STAR,
                "<bold><light_purple>Rebirth " + roleName(role) + " Omnitool</light_purple></bold>", lore),
                ev -> {
                    if (tools.rebirth(uuid, role)) {
                        CoreFoundation.getInstance().messages().sendRaw(p,
                                CoreFoundation.getInstance().messages().getPrefix()
                                        + " <light_purple>You rebirthed your " + roleName(role)
                                        + " Omnitool! Permanent perks gained.</light_purple>");
                        openMain(p, role);
                    } else {
                        CoreFoundation.getInstance().messages().sendRaw(p,
                                CoreFoundation.getInstance().messages().getPrefix() + " <red>Rebirth requirements not met.</red>");
                        openRebirth(p, role);
                    }
                });

        gui.setItem(4, ItemUtil.create(Material.EXPERIENCE_BOTTLE, "<bold><gold>Requirements</gold></bold>",
                List.of("<gray>Tool Level: <white>" + tools.toolLevel(uuid, role) + "</white> / "
                        + omniCfg.getInt("omnitools.rebirth.required-tool-level", 50),
                        "<gray>Total upgrade levels: <white>" + totalUpgradeLevels(uuid, role) + "</white> / 60")), ev -> {});
        gui.setItem(22, ItemUtil.create(Material.BARRIER, "<bold><red>Back</red></bold>",
                List.of("<gray>Back to Omnitool menu.")), ev -> openMain(p, role));

        gui.open(p);
    }

    private int totalUpgradeLevels(final UUID uuid, final String role) {
        int t = 0;
        for (final int v : tools.get(uuid, role).upgrades().values()) t += v;
        return t;
    }

    private String roleName(final String role) {
        return omniCfg.getString("omnitools.roles." + role + ".name", role);
    }

    private String upgradeName(final String up) {
        final var sec = omniCfg.getConfigurationSection("omnitools.upgrades." + up);
        return sec == null ? up : sec.getString("name", up);
    }

    private String progressBar(final double progress, final int width) {
        final int total = Math.max(1, width);
        final int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, progress)) * total);
        final StringBuilder sb = new StringBuilder("<green>");
        for (int i = 0; i < total; i++) sb.append(i < filled ? "█" : "<dark_gray>░</dark_gray>");
        return sb.toString();
    }

    private String prettyPerk(final String k) {
        final StringBuilder sb = new StringBuilder();
        for (final String part : k.split("_")) {
            sb.append(part.substring(0, 1).toUpperCase()).append(part.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
