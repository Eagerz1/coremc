package net.coremc.skyblock.crates;

import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.coremc.CoreMC;
import net.coremc.skyblock.crates.config.LootboxConfig;
import net.coremc.skyblock.crates.rarity.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crate / Key / Lootbox module for the consolidated CoreMC plugin.
 * <p>Wires together the key items, weighted crate opening, the lootbox animation, the
 * crate GUI, and the management commands. Uses ONLY existing CoreMC systems: money
 * (Vault/Essentials via {@link net.coremc.foundation.util.Economy}), Sky Tokens
 * ({@code progression().tokens()}), Credits (CoreMC-Store bridge), generators and
 * spawners. No new currency or progression system is introduced.
 */
public final class CrateModule {

    private final JavaPlugin plugin;
    private KeyItem keyItem;
    private ArmourSets armour;
    private RewardResolver rewards;
    private CrateOpener opener;
    private LootboxManager lootbox;
    private ProgressionArmour progressionArmour;

    public CrateModule(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        this.keyItem = new KeyItem(plugin);
        this.armour = new ArmourSets(plugin);
        this.progressionArmour = new ProgressionArmour(plugin);
        this.rewards = new RewardResolver(plugin, keyItem, armour, progressionArmour);
        this.rewards.initBridges();
        this.opener = new CrateOpener(plugin, rewards);
        this.lootbox = new LootboxManager(plugin, rewards);
        this.lootbox.load();

        // Listeners.
        final CrateListener listener = new CrateListener(plugin, keyItem, lootbox, this);
        Bukkit.getPluginManager().registerEvents((Listener) listener, plugin);

        // Physical crate blocks (placeable + floating name + existing key integration).
        final CrateBlock crateBlock = new CrateBlock(plugin, keyItem, this);
        Bukkit.getPluginManager().registerEvents((Listener) crateBlock, plugin);
        crateBlock.spawnConfiguredLocations();

        // Commands.
        final CrateCommand cmd = new CrateCommand(plugin, this);
        for (final String name : new String[]{"crate", "crates", "lootbox", "key"}) {
            final org.bukkit.command.PluginCommand pc = plugin.getCommand(name);
            if (pc != null) {
                pc.setExecutor(cmd);
                pc.setTabCompleter(cmd);
            }
        }
        // /sets — Role-bound Set progression GUI.
        final org.bukkit.command.PluginCommand setsCmd = plugin.getCommand("sets");
        if (setsCmd != null) {
            setsCmd.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                new SetsGui(plugin).open(p);
                return true;
            });
        }
        // Progression armour effect listeners (drop multipliers, etc.).
        Bukkit.getPluginManager().registerEvents(
                (org.bukkit.event.Listener) new ProgressionArmourListener(plugin, this), plugin);
        plugin.getLogger().info("[crates] Crate / Key / Lootbox module enabled.");
    }

    public KeyItem keys() { return keyItem; }
    public ArmourSets armour() { return armour; }
    public RewardResolver rewards() { return rewards; }
    public CrateOpener opener() { return opener; }
    public LootboxManager lootbox() { return lootbox; }
    public ProgressionArmour progressionArmour() { return progressionArmour; }

    // ---- Bloodfang Multi-Kill (progression armour ability) ----
    private final ConcurrentHashMap<UUID, Long> bloodfangExpiry = new ConcurrentHashMap<>();
    private static final long BLOODFANG_DURATION_MS = 30_000L;

    /** Activate Bloodfang Multi-Kill for 30s if the player has it equipped. Returns true if activated. */
    public boolean activateBloodfang(final Player p) {
        if (!progressionArmour.owns(p, "bloodfang")
                || !"bloodfang".equals(progressionArmour.equipped(p))) {
            return false;
        }
        bloodfangExpiry.put(p.getUniqueId(), System.currentTimeMillis() + BLOODFANG_DURATION_MS);
        p.sendMessage(CoreFoundation.getInstance().messages().parse(
                "<prefix> <red>Multi-Kill activated! Every kill counts as 3 for 30s.</red>",
                plugin.getConfig().getString("prefix")));
        return true;
    }

    /** True if the Bloodfang Multi-Kill ability is currently active for the player. */
    public boolean bloodfangActive(final Player p) {
        final Long exp = bloodfangExpiry.get(p.getUniqueId());
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            bloodfangExpiry.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    /** Kills to credit while Multi-Kill is active (3), else 1. */
    public int bloodfangKillCount(final Player p) {
        return bloodfangActive(p) ? 3 : 1;
    }

    /** Open the crate/key browser GUI for a player. */
    public void openGui(final Player p) {
        // If the player has lootboxes available, open the lootbox selection lobby.
        // Otherwise, fall back to the traditional key-based crate browser.
        if (lootbox.hasLootboxes()) {
            lootbox.openSelectionGui(p);
            return;
        }
        final CoreFoundation f = CoreFoundation.getInstance();
        final int rows = Math.max(4, (int) Math.ceil((KeyId.values().length + 1) / 9.0) + 1);
        final InventoryGui gui = new InventoryGui(plugin, rows,
                "<bold><gold>CoreMC Crates</gold></bold>", Material.BLACK_STAINED_GLASS_PANE);

        int slot = 10;
        for (final KeyId id : KeyId.values()) {
            gui.setItem(slot, keyDisplay(id), ev -> {
                final Player clicker = (Player) ev.getWhoClicked();
                // Opening from GUI: consume one key from inventory and open.
                if (consumeKey(clicker, id)) {
                    opener.open(clicker, id);
                    clicker.closeInventory();
                    clicker.playSound(clicker.getLocation(),
                            org.bukkit.Sound.BLOCK_CHEST_OPEN, 1f, 1f);
                } else {
                    clicker.sendMessage(f.messages().parse("<prefix> <red>You do not have a "
                            + id.defaultName() + ".</red>", plugin.getConfig().getString("prefix")));
                }
            });
            slot += (slot % 9 == 16) ? 2 : 1;
        }

        gui.open(p);
    }

    /** Open the key reward-information GUI for a player with a specific key. */
    public void openKeyRewardGui(final Player p, final KeyId id) {
        final var cfg = plugin.getConfig();
        final String section = "crates.keys." + id.id();
        final String colour = cfg.getString(section + ".colour", "white").toLowerCase(java.util.Locale.ROOT);
        final String name = cfg.getString(section + ".name", id.defaultName());

        // Build the reward info GUI using the lootbox manager's crates
        final LootboxManager manager = lootbox();
        // Find the lootbox config that corresponds to this key type.
        // Keys map to crate types: vote->vote, river->river, etc.
        LootboxConfig boxCfg = null;
        for (final String lid : manager.ids()) {
            final LootboxConfig cfg2 = manager.get(lid);
            if (cfg2 != null && lid.equals(id.id())) {
                boxCfg = cfg2;
                break;
            }
        }
        // Fallback: try matching by key name
        if (boxCfg == null) {
            for (final String lid : manager.ids()) {
                final LootboxConfig cfg2 = manager.get(lid);
                if (cfg2 != null && cfg2.displayName().equalsIgnoreCase(name)) {
                    boxCfg = cfg2;
                    break;
                }
            }
        }

        final int rows = 6;
        final InventoryGui gui = new InventoryGui(plugin, rows,
                "<bold><" + colour + ">[" + name + " Rewards]</" + colour + "></bold>",
                Material.BLACK_STAINED_GLASS_PANE);

        // Fill with dark filler
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, ItemUtil.create(Material.GRAY_STAINED_GLASS_PANE, " ", (List<String>) null));
        }

        // Back button
        gui.setItem(45, ItemUtil.create(Material.ARROW,
                "<bold><gray>← Back</gray></bold>",
                List.of("<gray>Return to crate browser.</gray>")),
                ev -> {
                    final Player clicker = (Player) ev.getWhoClicked();
                    clicker.closeInventory();
                    manager.openSelectionGui(clicker);
                });

        // Display rewards if we found a config
        if (boxCfg != null && boxCfg.rewards() != null && !boxCfg.rewards().isEmpty()) {
            // Group rewards by rarity
            final java.util.Map<Rarity, java.util.List<LootboxConfig.Reward>> byRarity = new java.util.EnumMap<>(Rarity.class);
            for (final LootboxConfig.Reward r : boxCfg.rewards()) {
                byRarity.computeIfAbsent(r.rarity(), k -> new java.util.ArrayList<>()).add(r);
            }

            // Starting slot for each rarity section
            int slot = 11; // row 1 starts at 9, leave 10 for spacing

            for (final java.util.Map.Entry<Rarity, java.util.List<LootboxConfig.Reward>> entry : byRarity.entrySet()) {
                final Rarity rarity = entry.getKey();
                final java.util.List<LootboxConfig.Reward> rewards = entry.getValue();
                final String rarityColour = rarity.configuredColor(plugin);

                // Header item showing rarity name + colour
                gui.setItem(slot, rarityHeader(rarity, rarityColour, rewards.size()));
                slot++;

                // Reward items in a row of up to 7, with spacing
                int rowSlot = slot;
                for (int i = 0; i < rewards.size(); i++) {
                    final LootboxConfig.Reward r = rewards.get(i);
                    // Place reward items with gaps between them for spacing
                    if (i > 0 && i % 5 == 0) {
                        rowSlot += 2; // skip gap
                    }
                    if (rowSlot >= 45) break;

                    gui.setItem(rowSlot, rewardItem(r, plugin), ev -> {
                        // Show detailed info on click
                        final Player clicker = (Player) ev.getWhoClicked();
                        showRewardDetails(clicker, r);
                    });
                    rowSlot++;
                }

                // Move to next section with a gap row
                slot = rowSlot;
                if (slot % 9 > 1) {
                    slot += 9 - (slot % 9);
                    slot += 2; // left margin
                }
                if (slot >= 45) break;
            }
        } else {
            // No rewards configured - show placeholder
            gui.setItem(27, ItemUtil.create(Material.BARRIER,
                "<red>No rewards configured</red>",
                List.of("<gray>This crate has no rewards defined.</gray>")));
        }

        gui.open(p);
    }

    /** Show rarity header item. */
    private ItemStack rarityHeader(final Rarity rarity, final String colour, final int count) {
        return ItemUtil.create(getRarityMaterial(rarity),
                "<bold><" + colour + ">[" + rarity.name() + "]</" + colour + "></bold>",
                List.of("<gray>" + count + " reward" + (count == 1 ? "" : "s") + "</gray>"));
    }

    /** Show detailed reward info in a small popup GUI. */
    private void showRewardDetails(final Player p, final LootboxConfig.Reward r) {
        final InventoryGui detailGui = new InventoryGui(plugin, 3,
                "<bold>" + r.display() + "</bold>", Material.BLACK_STAINED_GLASS_PANE);

        // Center the reward item
        detailGui.setItem(13, rewardItem(r, plugin));

        // Info lines around it
        final String colour = r.raretyColour().isEmpty()
                ? r.rarity().configuredColor(plugin) : r.raretyColour();

        detailGui.setItem(4, ItemUtil.create(Material.NAME_TAG,
                "<bold>Rarity:</bold>",
                List.of("<" + colour + r.rarity().name() + "</" + colour + ">")));

        detailGui.setItem(3, ItemUtil.create(Material.PAPER,
                "<bold>Chance:</bold>",
                List.of("<gray>" + String.format(java.util.Locale.ROOT, "%.2f", r.chance()) + "%</gray>")));

        detailGui.setItem(5, ItemUtil.create(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                "<bold>Type:</bold>",
                List.of("<gray>" + r.type() + "</gray>")));

        if (r.description() != null && !r.description().isEmpty()) {
            detailGui.setItem(22, ItemUtil.create(Material.WRITABLE_BOOK,
                    "<bold>Description:</bold>",
                    List.of("<gray>" + r.description() + "</gray>")));
        }

        // Close button
        detailGui.setItem(22, ItemUtil.create(Material.BARRIER,
                "<red>Close", List.of("<gray>Click to go back.</gray>")),
                ev -> p.closeInventory());

        detailGui.open(p);
    }

    /** Build a reward item for the GUI. */
    private ItemStack rewardItem(final LootboxConfig.Reward r, final JavaPlugin plugin) {
        return r.displayItem(plugin, rewards);
    }

    private Material getRarityMaterial(final Rarity rarity) {
        return switch (rarity) {
            case COMMON -> Material.WHITE_DYE;
            case UNCOMMON -> Material.YELLOW_DYE;
            case RARE -> Material.BLUE_DYE;
            case EPIC -> Material.PURPLE_DYE;
            case LEGENDARY -> Material.GOLD_INGOT;
            case MYTHIC -> Material.AMETHYST_SHARD;
        };
    }

    private ItemStack keyDisplay(final KeyId id) {
        final var cfg = plugin.getConfig();
        final String section = "crates.keys." + id.id();
        final String colour = cfg.getString(section + ".colour", "white").toLowerCase(java.util.Locale.ROOT);
        final String name = cfg.getString(section + ".name", id.defaultName());
        final List<String> lore = new ArrayList<>();
        lore.add("<white>Contains:</white>");
        final List<String> contains = cfg.getStringList(section + ".contains");
        if (contains.isEmpty()) {
            lore.add("<white>• " + id.defaultName().replace(" Key", " rewards") + "</white>");
        } else {
            for (final String c : contains) lore.add("<white>• " + c + "</white>");
        }
        final int clicks = plugin.getConfig().getInt(section + ".rewards", 1);
        lore.add("");
        lore.add("<gray>Click to open (" + clicks + " reward" + (clicks == 1 ? "" : "s") + ").</gray>");
        return ItemUtil.create(keyItem.build(id, 1).getType(),
                "<bold><" + colour + ">" + name + "</" + colour + "></bold>", lore);
    }

    /** Consume one key of the given id from the player's inventory. Returns true if one was taken. */
    public boolean consumeKey(final Player p, final KeyId id) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            final ItemStack it = p.getInventory().getItem(i);
            if (it != null && keyItem.keyId(it) == id) {
                it.setAmount(it.getAmount() - 1);
                if (it.getAmount() <= 0) p.getInventory().setItem(i, null);
                return true;
            }
        }
        return false;
    }

    /** Grant a key to a player (used by commands / store bridge). */
    public void giveKey(final Player p, final KeyId id, final int amount) {
        final ItemStack it = keyItem.build(id, amount);
        final java.util.Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (final ItemStack drop : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), drop);
        }
    }

    /** Grant a lootbox to a player by id (used by commands / store bridge). */
    public void giveLootbox(final Player p, final String boxId, final int amount) {
        final ItemStack it = lootbox.build(boxId, amount);
        final java.util.Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (final ItemStack drop : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), drop);
        }
    }

    /** Grant a default beta lootbox to a player (used by commands / store bridge). */
    public void giveLootbox(final Player p, final int amount) {
        giveLootbox(p, "beta", amount);
    }

    /** Grant a physical crate block to a player (used by commands / store bridge). */
    public void giveCrate(final Player p, final KeyId id, final int amount) {
        final ItemStack it = new CrateBlock(plugin, keyItem, this).build(id, amount);
        final java.util.Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (final ItemStack drop : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), drop);
        }
    }

    /** Reload crate configuration from config.yml + lootbox configs. */
    public void reload() {
        plugin.reloadConfig();
        CoreFoundation.getInstance().reloadConfiguration();
        progressionArmour = new ProgressionArmour(plugin);
        rewards.reload(progressionArmour);
        lootbox.reload();
        plugin.getLogger().info("[crates] configuration reloaded.");
    }
}