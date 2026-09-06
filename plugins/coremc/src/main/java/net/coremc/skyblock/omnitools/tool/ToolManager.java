package net.coremc.skyblock.omnitools.tool;

import net.coremc.foundation.util.ColorUtil;
import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.omnitools.perk.OmniPerk;
import net.coremc.skyblock.omnitools.stat.ModifierEngine;
import net.coremc.skyblock.omnitools.stat.Stat;
import net.coremc.skyblock.omnitools.OmniConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central Omnitool data + item hub.
 *
 * <p>One physical Omnitool per role. Each Omnitool has its own {@link ToolProgress}
 * (its own Tool Level and upgrade levels) — stored independently of the player's
 * global Role Level. Role currencies live in {@link RoleCurrencyManager}.</p>
 */
public final class ToolManager {

    private final JavaPlugin plugin;
    private final NamespacedKey KEY;
    private final File file;
    private final YamlConfiguration data;
    private final RoleCurrencyManager currency;
    private final OmniConfig omni;

    // key = uuid + ":" + ROLE -> progress
    private final Map<String, ToolProgress> cache = new ConcurrentHashMap<>();
    private OmniPerk.Registry perkRegistry;

    public ToolManager(final JavaPlugin plugin, final RoleCurrencyManager currency, final OmniConfig omni) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "omnitool");
        this.currency = currency;
        this.omni = omni;
        this.perkRegistry = OmniPerk.Registry.load(omni.config());
        this.file = new File(plugin.getDataFolder(), "tools.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        for (final String r : RoleCurrencyManager.ROLES) {
            final var sec = data.getConfigurationSection(r);
            if (sec == null) continue;
            for (final String k : sec.getKeys(false)) {
                try {
                    final UUID uuid = UUID.fromString(k);
                    cache.put(uuid + ":" + r, ToolProgress.fromConfig(sec.getConfigurationSection(k)));
                } catch (final IllegalArgumentException ignored) {}
            }
        }
    }

    public RoleCurrencyManager currency() { return currency; }

    private static String key(final UUID uuid, final String role) {
        return uuid.toString() + ":" + role.toUpperCase(java.util.Locale.ROOT);
    }

    public ToolProgress get(final UUID uuid, final String role) {
        return cache.computeIfAbsent(key(uuid, role), k -> new ToolProgress());
    }

    // ---- tool xp / level (per omnitool) ----

    /** Award tool XP to a specific role's Omnitool; returns the new tool level.
     *  Honours the live "2x OmniTool Progress" event multiplier centrally. */
    public int addToolXp(final UUID uuid, final String role, final long amount) {
        final long boosted = Math.round(amount * net.coremc.skyblock.events.Events.omniToolMultiplier());
        final ToolProgress p = get(uuid, role);
        final java.util.List<Long> curve = toolXpCurve();
        final int before = p.toolLevel();
        p.addToolXp(boosted, curve);
        final int after = p.toolLevel();
        save(uuid, role, p);
        if (after > before) {
            // New perk unlocks may have happened — refresh the held tool's lore.
            refreshHeldTool(uuid, role);
        }
        return after;
    }

    /** Rebuild lore of the matching Omnitool currently in the player's hand (if any). */
    public void refreshHeldTool(final UUID uuid, final String role) {
        final org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(uuid);
        if (p == null) return;
        final ItemStack main = p.getInventory().getItemInMainHand();
        if (roleOf(main) != null && roleOf(main).equals(role.toUpperCase(java.util.Locale.ROOT))) {
            p.getInventory().setItemInMainHand(refreshLore(uuid, main));
        }
    }

    /** Cumulative tool-XP-per-level curve (index = level). Linear fallback from config. */
    private java.util.List<Long> toolXpCurve() {
        final java.util.List<Long> list = omni.config().getLongList("omnitools.tool-xp-curve");
        if (!list.isEmpty()) return list;
        final long per = Math.max(1, omni.config().getLong("omnitools.tool-xp-per-level", 1000));
        final java.util.List<Long> lin = new java.util.ArrayList<>();
        for (int i = 0; i <= 100; i++) lin.add(i * per);
        return lin;
    }

    public int toolLevel(final UUID uuid, final String role) {
        return get(uuid, role).toolLevel();
    }

    // ---- upgrades ----

    public int getUpgradeLevel(final UUID uuid, final String role, final String upgrade) {
        return get(uuid, role).upgradeLevel(upgrade);
    }

    /**
     * Attempt to buy {@code amount} levels of an upgrade using the role's currency.
     * Cost is the sum of the per-level costs up to the requested amount (respecting
     * the upgrade's max level). Returns levels actually purchased (may be < amount if
     * currency or max-level caps it).
     */
    public int purchaseUpgrade(final UUID uuid, final String role, final String upgrade, final int amount) {
        final var sec = omni.config().getConfigurationSection("omnitools.upgrades." + upgrade);
        if (sec == null) return 0;
        final int max = sec.getInt("max-level", 1);
        final long base = sec.getLong("base-cost", 100);
        final double mult = sec.getDouble("cost-multiplier", 1.5);
        final ToolProgress p = get(uuid, role);
        int current = p.upgradeLevel(upgrade);
        int bought = 0;
        long cost = 0;
        while (bought < amount && current + bought < max) {
            final long levelCost = (long) (base * Math.pow(mult, current + bought));
            if (cost + levelCost > currency.get(uuid, role)) break;
            cost += levelCost;
            bought++;
        }
        if (bought <= 0) return 0;
        currency.take(uuid, role, cost);
        p.raiseUpgrade(upgrade, bought);
        save(uuid, role, p);
        return bought;
    }

    /** Max-upgrade: buy as many levels as the player's currency allows. */
    public int purchaseUpgradeMax(final UUID uuid, final String role, final String upgrade) {
        final var sec = omni.config().getConfigurationSection("omnitools.upgrades." + upgrade);
        if (sec == null) return 0;
        final int max = sec.getInt("max-level", 1);
        final int headroom = max - getUpgradeLevel(uuid, role, upgrade);
        if (headroom <= 0) return 0;
        return purchaseUpgrade(uuid, role, upgrade, headroom);
    }

    /** Cost to buy {@code amount} more levels from the current level (uses live progress). */
    public long costFor(final UUID uuid, final String role, final String upgrade, final int amount) {
        final var sec = omni.config().getConfigurationSection("omnitools.upgrades." + upgrade);
        if (sec == null) return Long.MAX_VALUE;
        final int max = sec.getInt("max-level", 1);
        final long base = sec.getLong("base-cost", 100);
        final double mult = sec.getDouble("cost-multiplier", 1.5);
        int current = getUpgradeLevel(uuid, role, upgrade);
        long cost = 0;
        int bought = 0;
        while (bought < amount && current + bought < max) {
            cost += (long) (base * Math.pow(mult, current + bought));
            bought++;
        }
        return cost;
    }

    // ---- rebirth ----

    public boolean canRebirth(final UUID uuid, final String role) {
        final var sec = omni.config().getConfigurationSection("omnitools.rebirth");
        if (sec == null) return false;
        final int reqLevel = sec.getInt("required-tool-level", 50);
        final int reqUpgrades = sec.getInt("required-total-upgrade-levels", 60);
        final ToolProgress p = get(uuid, role);
        if (p.toolLevel() < reqLevel) return false;
        int total = 0;
        for (final int v : p.upgrades().values()) total += v;
        return total >= reqUpgrades;
    }

    /** Reset upgrade progression, grant permanent perks. Returns true on success. */
    public boolean rebirth(final UUID uuid, final String role) {
        if (!canRebirth(uuid, role)) return false;
        final var sec = omni.config().getConfigurationSection("omnitools.rebirth.perks");
        final Map<String, Double> granted = new java.util.HashMap<>();
        if (sec != null) {
            for (final String k : sec.getKeys(false)) {
                granted.put(k, sec.getDouble(k, 0.0));
            }
        }
        final ToolProgress p = get(uuid, role);
        p.resetUpgrades();
        p.addRebirth(granted);
        save(uuid, role, p);
        return true;
    }

    // ---- item building ----

    /** Build the physical Omnitool item for a role (PDC-tagged, per-role material). */
        public ItemStack makeTool(final String role) {
            final String r = role.toUpperCase(java.util.Locale.ROOT);
            final var cfg = omni.config();
            final String matName = cfg.getString("omnitools.roles." + r + ".material", "DIAMOND_PICKAXE");
            final Material mat;
            try {
                mat = Material.valueOf(matName.toUpperCase(java.util.Locale.ROOT));
            } catch (final IllegalArgumentException e) {
                return null;
            }
            final String colour = cfg.getString("omnitools.roles." + r + ".colour", "white");
            final String display = cfg.getString("omnitools.roles." + r + ".name", r.substring(0, 1)
                + r.substring(1).toLowerCase() + " Omnitool");
            final ItemStack it = new ItemStack(mat);
            final ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtil.parse("<bold><" + colour + ">" + display + "</" + colour + "></bold>"));
                final var lore = java.util.List.of(
                                        ColorUtil.parse("<gray>Shift + Right Click to open.</gray>"));
                                meta.lore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
                meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, r);
                it.setItemMeta(meta);
            }
            // Build initial dynamic lore (role + level + xp + bonuses). A fresh tool has no
            // progress yet, so pass a transient UUID-less snapshot via the item's own PDC role.
            return refreshLoreOnCreate(it, r);
        }

        /** Apply lore to a freshly-built tool (no player progress; shows role + base bonuses). */
        private ItemStack refreshLoreOnCreate(final ItemStack it, final String role) {
            final ItemMeta meta = it.getItemMeta();
            if (meta == null) return it;
            final java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("<gray>Tool Level: <white>1</white>");
            lore.add("<gray>Tool XP: <white>0</white>/<white>"
                    + FormatUtil.formatNumber(xpCurve().get(2)) + "</white>");
            lore.add("<gray>Upgrades & perks unlock as you level.</gray>");
            lore.add("<dark_gray>Shift + Right Click to open.</dark_gray>");
            meta.lore(lore.stream().map(ColorUtil::parse).toList());
            it.setItemMeta(meta);
            return it;
        }

    /** True if the item is a CoreMC Omnitool; returns its role or null. */
    public String roleOf(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        final String tag = item.getItemMeta().getPersistentDataContainer()
                .get(KEY, PersistentDataType.STRING);
        return tag == null ? null : tag;
    }

    /** Total multiplier from tool level: +0.05x per tool level (capped sensibly). */
    public double toolStatMultiplier(final UUID uuid, final String role) {
        return 1.0 + 0.05 * toolLevel(uuid, role);
    }

    /**
     * Grant free levels of an Omnitool upgrade (used by role milestones / progression unlocks).
     * Routes through the existing purchased-upgrade logic so it composes with bought levels and
     * never duplicates cost/state handling. Idempotent in effect (just adds levels).
     *
     * @return the number of levels actually granted (0 if the upgrade is unknown / already maxed).
     */
    public int grantFreeUpgrade(final UUID uuid, final String role, final String upgrade, final int levels) {
        if (levels <= 0) return 0;
        final var sec = omni.config().getConfigurationSection("omnitools.upgrades." + upgrade);
        if (sec == null) {
            plugin.getLogger().warning("[omnitools] milestone references unknown upgrade: " + upgrade);
            return 0;
        }
        final int max = sec.getInt("max-level", 1);
        final ToolProgress p = get(uuid, role);
        final int current = p.upgradeLevel(upgrade);
        final int room = max - current;
        if (room <= 0) return 0;
        final int granted = Math.min(room, levels);
        p.raiseUpgrade(upgrade, granted);
        save(uuid, role, p);
        return granted;
    }

    private void save(final UUID uuid, final String role, final ToolProgress p) {
        final String base = role.toUpperCase(java.util.Locale.ROOT) + "." + uuid.toString();
        data.set(base + ".tool-xp", p.toolXp());
        data.set(base + ".tool-level", p.toolLevel());
        data.set(base + ".rebirth", p.rebirthCount());
        for (final var e : p.upgrades().entrySet()) {
            data.set(base + ".upgrades." + e.getKey(), e.getValue());
        }
        for (final var e : p.rebirthPerks().entrySet()) {
            data.set(base + ".rebirth-perks." + e.getKey(), e.getValue());
        }
        try {
            data.save(file);
        } catch (final java.io.IOException e) {
            plugin.getLogger().warning("Could not save tools.yml: " + e.getMessage());
        }
    }

    // =====================================================================
    //  Central modifier snapshot (the single place bonuses are combined)
    // =====================================================================

    /**
     * Build the full stat modifier snapshot for a player's role Omnitool.
     *
     * <p>Every OmniTool bonus flows through here instead of being hardcoded in a listener:
     * <ol>
     *   <li>base role-bonus from Tool Level (omnitools.role-bonus.&lt;ROLE&gt;);</li>
     *   <li>upgrades (omnitools.upgrade-effects);</li>
     *   <li>OmniTool perks unlocked at the current Tool Level (omnitools.perks);</li>
     *   <li>active timed abilities (AbilityManager) — passed in;</li>
     *   <li>rebirth perks, mapped onto stats (omnitools.rebirth.stat-map).</li>
     * </ol>
     * The returned {@link ModifierEngine.Snapshot} resolves a final multiplier per stat.</p>
     */
    public ModifierEngine.Snapshot buildModifiers(final UUID uuid, final String role,
                                                  final java.util.List<ModifierEngine.Modifier> activeAbilityMods) {
        final ModifierEngine.Snapshot s = ModifierEngine.snapshot();
        final ToolProgress p = get(uuid, role);
        final var cfg = omni.config();

        // 1. Base role bonus from Tool Level.
        final var rb = cfg.getConfigurationSection("omnitools.role-bonus." + role.toUpperCase(Locale.ROOT));
        if (rb != null) {
            final Stat st = Stat.fromConfig(rb.getString("stat", ""));
            final double per = rb.getDouble("per-tool-level", 0.0);
            if (st != null && per > 0) s.add(ModifierEngine.Modifier.percent(st, per * p.toolLevel()));
        }

        // 2. Upgrade levels -> stat bonuses.
        final var ue = cfg.getConfigurationSection("omnitools.upgrade-effects");
        if (ue != null) {
            for (final String up : ue.getKeys(false)) {
                final Stat st = Stat.fromConfig(ue.getString(up + ".stat", ""));
                if (st == null) continue;
                final int lvl = p.upgradeLevel(up);
                if (lvl <= 0) continue;
                s.add(ModifierEngine.Modifier.percent(st, ue.getDouble(up + ".per-level", 0.0) * lvl));
            }
        }

        // 3. Omnitool perks unlocked at this Tool Level.
        for (final OmniPerk perk : perkRegistry.forRole(role)) {
            if (!perk.unlockedAt(p.toolLevel())) continue;
            for (final var m : perk.modifiers()) s.add(m);
        }

        // 4. Active timed abilities (passed in so the engine stays stateless here).
        if (activeAbilityMods != null) {
            for (final var m : activeAbilityMods) s.add(m);
        }

        // 5. Rebirth perks -> stats (each rebirth stacks).
        final var rmap = cfg.getConfigurationSection("omnitools.rebirth.stat-map");
        if (rmap != null) {
            for (final var e : p.rebirthPerks().entrySet()) {
                final Stat st = Stat.fromConfig(rmap.getString(e.getKey(), ""));
                if (st != null) s.add(ModifierEngine.Modifier.percent(st, e.getValue()));
            }
        }
        return s;
    }

    /** Convenience: resolve a single stat multiplier (1.0 if none). */
    public double statMultiplier(final UUID uuid, final String role, final Stat stat,
                                 final java.util.List<ModifierEngine.Modifier> activeAbilityMods) {
        return buildModifiers(uuid, role, activeAbilityMods).multiplier(stat);
    }

    /** Reload the OmniTool perk registry from config (call on plugin load / /coremc reload). */
    public void reloadPerks() {
        this.perkRegistry = OmniPerk.Registry.load(omni.config());
    }

    /** Read-only access to the OmniTool perk registry (for GUIs). */
    public OmniPerk.Registry perkRegistry() { return perkRegistry; }

    // ---- tool xp info for GUI / lore ----

    /** Tool XP needed to reach the next Tool Level, or -1 if at cap. */
    public long toolXpToNext(final UUID uuid, final String role) {
        final java.util.List<Long> curve = xpCurve();
        final long total = get(uuid, role).toolTotalXp();
        final int lvl = toolLevel(uuid, role);
        if (lvl >= curve.size() - 1) return -1;
        return curve.get(lvl + 1) - total;
    }

    /** Current-Tool-Level XP progress fraction (0..1), 1.0 at cap. */
    public double toolXpProgress(final UUID uuid, final String role) {
        final java.util.List<Long> curve = xpCurve();
        final long total = get(uuid, role).toolTotalXp();
        final int lvl = toolLevel(uuid, role);
        if (lvl >= curve.size() - 1) return 1.0;
        final long base = curve.get(lvl);
        final long next = curve.get(lvl + 1);
        if (next <= base) return 1.0;
        return Math.max(0.0, Math.min(1.0, (double) (total - base) / (next - base)));
    }

    /** XP within the current level (towards next) and the requirement for next. */
    public long[] toolXpWindow(final UUID uuid, final String role) {
        final java.util.List<Long> curve = xpCurve();
        final long total = get(uuid, role).toolTotalXp();
        final int lvl = toolLevel(uuid, role);
        if (lvl >= curve.size() - 1) return new long[]{0, 0};
        final long base = curve.get(lvl);
        final long next = curve.get(lvl + 1);
        return new long[]{total - base, next - base};
    }

    /** Cached XP curve from config (omnitools.tool-xp-curve), with a safe flat fallback. */
    public java.util.List<Long> xpCurve() {
        final java.util.List<Long> list = omni.config().getLongList("omnitools.tool-xp-curve");
        if (!list.isEmpty()) return list;
        final long per = Math.max(1, omni.config().getLong("omnitools.tool-xp-per-level", 1000));
        final java.util.List<Long> lin = new java.util.ArrayList<>();
        for (int i = 0; i <= 100; i++) lin.add(i * per);
        return lin;
    }

    public int toolLevelCap() {
        return omni.config().getInt("omnitools.tool-level-cap", xpCurve().size() - 1);
    }

    // ---- dynamic lore ----

    /**
     * Rebuild the lore of a player's physical Omnitool item to reflect role, Tool Level,
     * XP and current bonuses. Keeps the lore compact (role + level + xp + a few key
     * bonuses). Returns the (possibly unchanged) item. Call after any XP/level/perk/stat change.
     */
    public ItemStack refreshLore(final UUID uuid, final ItemStack item) {
        if (item == null) return item;
        final String role = roleOf(item);
        if (role == null) return item;
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        final int lvl = toolLevel(uuid, role);
        final long[] win = toolXpWindow(uuid, role);
        final var snap = buildModifiers(uuid, role, java.util.List.of());

        final java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("<gray>Tool Level: <white>" + lvl + "</white>"
                + (lvl >= toolLevelCap() ? " <gold>(MAX)</gold>" : ""));
        if (lvl >= toolLevelCap()) {
            lore.add("<gray>Tool XP: <white>MAXED</white>");
        } else {
            lore.add("<gray>Tool XP: <white>" + FormatUtil.formatNumber(win[0]) + "</white>/<white>"
                    + FormatUtil.formatNumber(win[1]) + "</white>");
        }
        // A few key current bonuses (keep it short).
        addBonus(lore, snap, Stat.MINING_SPEED, "Mining Speed");
        addBonus(lore, snap, Stat.FARMING_SPEED, "Farming Speed");
        addBonus(lore, snap, Stat.LOGGING_SPEED, "Logging Speed");
        addBonus(lore, snap, Stat.FISHING_SPEED, "Fishing Speed");
        addBonus(lore, snap, Stat.MOB_DAMAGE, "Mob Damage");
        addBonus(lore, snap, Stat.UNIVERSAL_SPEED, "Universal Speed");
        addBonus(lore, snap, Stat.XP_MULTIPLIER, "XP");
        lore.add("<dark_gray>Shift + Right Click to open.</dark_gray>");
        meta.lore(lore.stream().map(ColorUtil::parse).toList());
        item.setItemMeta(meta);
        return item;
    }

    private void addBonus(final java.util.List<String> lore, final ModifierEngine.Snapshot snap,
                          final Stat stat, final String label) {
        if (!snap.has(stat)) return;
        final int pct = (int) Math.round((snap.multiplier(stat) - 1.0) * 100);
        if (pct <= 0) return;
        lore.add("<gray>" + label + ": <green>+" + pct + "%</green>");
    }

    private String roleName(final String role) {
        return omni.config().getString("omnitools.roles." + role + ".name", role);
    }

    public void saveAll() {
        try {
            data.save(file);
        } catch (final java.io.IOException e) {
            plugin.getLogger().warning("Could not save tools.yml: " + e.getMessage());
        }
    }
}
