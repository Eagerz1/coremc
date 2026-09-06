package net.coremc.skyblock.crates;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.util.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves and applies a single reward from the crate/lootbox loot tables.
 *
 * <p>A reward is defined entirely in config under {@code crates.rewards.<id>}. It has a
 * {@code type} that selects how it is granted, plus type-specific fields. Supported types:
 * <ul>
 *   <li>{@code money}      - Vault/Essentials money (falls back to Sky Tokens)</li>
 *   <li>{@code tokens}     - Sky Tokens (existing progression currency)</li>
 *   <li>{@code credits}    - CoreMC Credits (via CoreMC-Store bridge, soft dependency)</li>
 *   <li>{@code item}       - a physical ItemStack (material + amount + optional display)</li>
 *   <li>{@code armour}     - a full armour set (helmet/chest/legs/boots) by id</li>
 *   <li>{@code tool}       - an Omnitools custom tool by type</li>
 *   <li>{@code tag}        - a chat tag unlocked via the chat cosmetics bridge</li>
 *   <li>{@code key}        - another CoreMC key (crates can contain keys)</li>
 *   <li>{@code cosmetic}   - a chat cosmetic unlocked via the chat cosmetics bridge</li>
 *   <li>{@code generator}  - a generator reward (handled by gens module)</li>
 *   <li>{@code spawner}    - a spawner reward (handled by spawners module)</li>
 * </ul>
 * No reward type is hard-coded into the engine — the type string drives a dispatch table.</p>
 */
public final class RewardResolver {

    private final JavaPlugin plugin;
    private final KeyItem keyItem;
    private final ArmourSets armour;
    private ProgressionArmour progressionArmour;
    private Object creditsApi;        // CreditsService instance from CoreMC-Store (reflection)
    private Method creditsAdd;        // addCredits(OfflinePlayer, long)
    private Method creditsGet;        // getCredits(OfflinePlayer)
    private Object cosmeticsApi;      // ChatCosmetics API instance
    private Method cosmeticsUnlock;   // unlock(Player, String)
    private Map<String, net.coremc.skyblock.crates.config.LootboxConfig> boxCache = new HashMap<>();

    public RewardResolver(final JavaPlugin plugin, final KeyItem keyItem, final ArmourSets armour,
                           final ProgressionArmour progressionArmour) {
        this.plugin = plugin;
        this.keyItem = keyItem;
        this.armour = armour;
        this.progressionArmour = progressionArmour;
    }

    /** Re-apply a fresh ProgressionArmour instance (used on reload). */
    public void reload(final ProgressionArmour progressionArmour) {
        this.progressionArmour = progressionArmour;
    }

    /** Best-effort bridges to CoreMC-Store (Credits) and CoreChatCosmetics. Non-fatal if absent. */
    public void initBridges() {
        try {
            final var pm = Bukkit.getPluginManager();
            final var store = pm.getPlugin("CoreMC-Store");
            if (store != null) {
                final Class<?> cs = Class.forName("net.coremc.store.CreditsService");
                final Method mCredits = store.getClass().getMethod("credits");
                creditsApi = mCredits.invoke(store);
                creditsAdd = cs.getMethod("addCredits", org.bukkit.OfflinePlayer.class, long.class);
                creditsGet = cs.getMethod("getCredits", org.bukkit.OfflinePlayer.class);
                plugin.getLogger().info("[crates] CoreMC-Store credits bridge active.");
            } else {
                plugin.getLogger().info("[crates] CoreMC-Store not found; credits rewards disabled.");
            }
        } catch (final Throwable t) {
            plugin.getLogger().warning("[crates] credits bridge failed (non-fatal): " + t.getMessage());
        }
        try {
            final var pm = Bukkit.getPluginManager();
            final var cc = pm.getPlugin("CoreChatCosmetics");
            if (cc != null) {
                final Class<?> api = Class.forName("net.coremc.chatcosmetics.ChatCosmeticsAPI");
                cosmeticsApi = cc.getClass().getMethod("getApi").invoke(cc);
                cosmeticsUnlock = api.getMethod("unlock", Player.class, String.class);
                plugin.getLogger().info("[crates] chat cosmetics bridge active.");
            }
        } catch (final Throwable t) {
            plugin.getLogger().warning("[crates] cosmetics bridge failed (non-fatal): " + t.getMessage());
        }
    }

    public long creditsBalance(final UUID uuid) {
        if (creditsApi == null || creditsGet == null) return 0;
        try {
            return (long) creditsGet.invoke(creditsApi, Bukkit.getOfflinePlayer(uuid));
        } catch (final Throwable t) {
            return 0;
        }
    }

    /** Apply the reward defined under crates.rewards.<rewardId> (or crates.lootbox-rewards.<rewardId>) to the player. Returns false if unknown. */
    public boolean apply(final Player p, final String rewardId) {
        final var cfg = plugin.getConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("crates.rewards." + rewardId);
        if (sec == null) sec = cfg.getConfigurationSection("crates.lootbox-rewards." + rewardId);
        if (sec == null) {
            plugin.getLogger().warning("[crates] missing reward definition: " + rewardId);
            return false;
        }
        final String type = sec.getString("type", "item");
        switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "money" -> grantMoney(p, sec.getLong("amount", 0));
            case "tokens" -> grantTokens(p, sec.getLong("amount", 0));
            case "credits" -> grantCredits(p, sec.getLong("amount", 0));
            case "item" -> grantItem(p, sec);
            case "armour" -> armour.grantSet(p, sec.getString("set", ""));
            case "progression_armour" -> progressionArmour.grant(p, sec.getString("set", ""));
            case "companion" -> grantCompanion(p, sec.getString("companion", ""), sec.getString("rarity", "COMMON"));
            case "tool" -> grantTool(p, sec.getString("tool", ""));
            case "tag" -> grantTag(p, sec.getString("tag", ""));
            case "key" -> grantKey(p, sec.getString("key", ""), sec.getInt("amount", 1));
            case "cosmetic" -> grantCosmetic(p, sec.getString("cosmetic", ""));
            case "generator" -> grantGenerator(p, sec.getString("gen", ""));
            case "spawner" -> grantSpawner(p, sec.getString("spawner", ""));
            case "skin" -> grantSkin(p, sec.getString("skin", ""));
            case "gradient" -> grantGradient(p, sec.getString("gradient", ""));
            case "bundle" -> applyBundle(p, sec.getConfigurationSection("bundle"));
            default -> plugin.getLogger().warning("[crates] unknown reward type: " + type);
        }
        return true;
    }

    /**
     * Apply a lootbox reward directly from a {@link LootboxConfig.Reward} object.
     * This is the primary entry point for the lootbox system — it handles all
     * standard types plus the new cosmetics types (skin, gradient, tag) that
     * delegate to the chat cosmetics bridge.
     */
    public boolean apply(final Player p, final net.coremc.skyblock.crates.config.LootboxConfig.Reward r) {
        final String type = r.type().toLowerCase(java.util.Locale.ROOT);
        switch (type) {
            case "money" -> grantMoney(p, r.amount());
            case "tokens" -> grantTokens(p, r.amount());
            case "credits" -> grantCredits(p, r.amount());
            case "item" -> give(p, buildItemFromReward(r));
            case "armour", "progression_armour" -> progressionArmour.grant(p, r.set());
            case "companion" -> grantCompanion(p, r.companion(), r.extra().getOrDefault("rarity", "COMMON").toString());
            case "key" -> grantKey(p, r.key(), r.amount());
            case "tag", "cosmetic", "skin", "gradient" -> grantCosmetic(p, resolveCosmetic(r));
            case "generator" -> grantGenerator(p, r.extra().getOrDefault("gen", "").toString());
            case "spawner" -> grantSpawner(p, r.extra().getOrDefault("spawner", "").toString());
            case "tool" -> grantTool(p, r.extra().getOrDefault("tool", "").toString());
            case "bundle" -> applyBundle(p, r.extra());
            default -> plugin.getLogger().warning("[crates] lootbox unknown reward type: " + type);
        }
        return true;
    }

    /**
     * Apply a bundle reward: a list of sub-rewards granted together as one lootbox prize.
     * The bundle can be supplied either as a {@code ConfigurationSection} (legacy config path)
     * or as a {@code Map} (lootbox Reward.extra form, where {@code bundle} holds a
     * {@code List<Map<String,Object>>} of sub-reward definitions).
     */
    private void applyBundle(final Player p, final Object bundleObj) {
        if (bundleObj == null) return;
        if (bundleObj instanceof ConfigurationSection sec) {
            for (final String key : sec.getKeys(false)) {
                applyBundleEntry(p, sec.getConfigurationSection(key));
            }
        } else if (bundleObj instanceof Map) {
            @SuppressWarnings("unchecked")
            final Object raw = ((Map<String, Object>) bundleObj).get("bundle");
            if (raw instanceof List<?> list) {
                for (final Object entry : list) {
                    if (entry instanceof Map) {
                        applyBundleEntry(p, toSection((Map<String, Object>) entry));
                    }
                }
            }
        }
    }

    /** Apply a single bundle entry (a sub-reward) to the player. */
    private void applyBundleEntry(final Player p, final ConfigurationSection sec) {
        if (sec == null) return;
        final String type = sec.getString("type", "item");
        switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "money" -> grantMoney(p, sec.getLong("amount", 0));
            case "tokens" -> grantTokens(p, sec.getLong("amount", 0));
            case "credits" -> grantCredits(p, sec.getLong("amount", 0));
            case "key" -> grantKey(p, sec.getString("key", ""), sec.getInt("amount", 1));
            case "item" -> grantItem(p, sec);
            case "tag", "cosmetic", "skin", "gradient" -> grantCosmetic(p,
                    sec.getString("tag", sec.getString("skin", sec.getString("gradient", sec.getString("cosmetic", "")))));
            case "companion" -> grantCompanion(p, sec.getString("companion", ""), sec.getString("rarity", "COMMON"));
            case "progression_armour", "armour" -> progressionArmour.grant(p, sec.getString("set", ""));
            case "generator" -> grantGenerator(p, sec.getString("gen", ""));
            case "spawner" -> grantSpawner(p, sec.getString("spawner", ""));
            case "tool" -> grantTool(p, sec.getString("tool", ""));
            default -> plugin.getLogger().warning("[crates] bundle unknown sub-reward type: " + type);
        }
    }

    /** Build a lightweight ConfigurationSection from a Map so bundle entries reuse the same apply logic. */
    private ConfigurationSection toSection(final Map<String, Object> map) {
        final org.bukkit.configuration.MemoryConfiguration cfg = new org.bukkit.configuration.MemoryConfiguration();
        for (final var e : map.entrySet()) {
            cfg.set(e.getKey(), e.getValue());
        }
        return cfg;
    }

    /** Resolve the cosmetic id from a lootbox reward (tag/skin/gradient/cosmetic all map to cosmetics). */
    private String resolveCosmetic(final net.coremc.skyblock.crates.config.LootboxConfig.Reward r) {
        if (r.tag() != null && !r.tag().isEmpty()) return r.tag();
        if (r.skin() != null && !r.skin().isEmpty()) return r.skin();
        if (r.gradient() != null && !r.gradient().isEmpty()) return r.gradient();
        return r.cosmetic();
    }

    /** Grant a skin unlock via the cosmetics bridge. */
    private void grantSkin(final Player p, final String skin) {
        grantCosmetic(p, skin);
    }

    /** Grant a gradient unlock via the cosmetics bridge. */
    private void grantGradient(final Player p, final String gradient) {
        grantCosmetic(p, gradient);
    }

    /** Build a display item from a lootbox reward (for previews / animation). */
    public ItemStack displayItem(final net.coremc.skyblock.crates.config.LootboxConfig.Reward r) {
        final String type = r.type().toLowerCase(java.util.Locale.ROOT);
        final String label = r.display();
        return switch (type) {
            case "money" -> net.coremc.foundation.util.ItemUtil.create(Material.PAPER,
                    "<bold><green>$" + format(r.amount()) + "</green></bold>",
                    List.of("<white>" + label + "</white>"));
            case "tokens" -> net.coremc.foundation.util.ItemUtil.create(Material.NETHER_STAR,
                    "<bold><aqua>" + format(r.amount()) + " Sky Tokens</aqua></bold>",
                    List.of("<white>" + label + "</white>"));
            case "credits" -> net.coremc.foundation.util.ItemUtil.create(Material.GOLD_NUGGET,
                    "<bold><gold>" + format(r.amount()) + " Credits</gold></bold>",
                    List.of("<white>" + label + "</white>"));
            case "armour" -> armour.displaySet(r.set());
            case "progression_armour" -> progressionSetDisplay(r.set());
            case "companion" -> companionDisplay(r.companion());
            case "key" -> keyItem.build(KeyId.byId(r.key()), Math.max(1, r.amount()));
            case "tag" -> net.coremc.foundation.util.ItemUtil.create(Material.NAME_TAG,
                    "<bold><yellow>" + label + "</yellow></bold>", List.of());
            case "skin" -> net.coremc.foundation.util.ItemUtil.create(Material.LEATHER_HELMET,
                    "<bold><light_purple>" + label + "</light_purple></bold>", List.of());
            case "gradient" -> net.coremc.foundation.util.ItemUtil.create(Material.BLUE_DYE,
                    "<bold><aqua>" + label + "</aqua></bold>", List.of());
            case "cosmetic" -> net.coremc.foundation.util.ItemUtil.create(Material.PAINTING,
                    "<bold><light_purple>" + label + "</light_purple></bold>", List.of());
            case "generator" -> net.coremc.foundation.util.ItemUtil.create(Material.REDSTONE_BLOCK,
                    "<bold><red>" + label + "</red></bold>", List.of());
            case "spawner" -> net.coremc.foundation.util.ItemUtil.create(Material.SPAWNER,
                    "<bold><red>" + label + "</red></bold>", List.of());
            case "tool" -> displayTool(r.extra().getOrDefault("tool", "").toString());
            case "bundle" -> net.coremc.foundation.util.ItemUtil.create(Material.CHEST,
                    "<bold><gold>" + label + "</gold></bold>",
                    List.of("<white>" + label + "</white>"));
            default -> buildItemFromReward(r);
        };
    }

    /** Build an item reward from a LootboxConfig.Reward (material + amount + display + lore). */
    private ItemStack buildItemFromReward(final net.coremc.skyblock.crates.config.LootboxConfig.Reward r) {
        Material m;
        try {
            m = Material.valueOf(r.material().toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return ItemUtilPlaceholder();
        }
        final ItemStack it = new ItemStack(m, Math.max(1, r.amount()));
        final ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            net.coremc.foundation.util.ItemUtil.setPlainName(meta, r.display());
            final var lore = r.extra().getOrDefault("lore", new java.util.ArrayList<String>());
            if (lore instanceof List) {
                final var mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
                final var lines = new java.util.ArrayList<net.kyori.adventure.text.Component>();
                for (final Object l : (List<?>) lore) {
                    lines.add(mm.deserialize(l.toString()));
                }
                meta.lore(lines);
            }
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                    org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                    org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Build the physical item shown in a GUI/animated reward slot for this reward (no grant). */
    public ItemStack displayItem(final String rewardId) {
        // First check if it's a composite lootbox key: lootbox:<boxId>:<rewardId>
        final ItemStack composite = displayItemComposite(rewardId);
        if (composite != null) return composite;
        final var cfg = plugin.getConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("crates.rewards." + rewardId);
        if (sec == null) sec = cfg.getConfigurationSection("crates.lootbox-rewards." + rewardId);
        if (sec == null) return ItemUtilPlaceholder();
        final String type = sec.getString("type", "item");
        final String label = sec.getString("display", rewardId);
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "money" -> net.coremc.foundation.util.ItemUtil.create(Material.PAPER,
                    "<bold><green>$" + format(sec.getLong("amount", 0)) + "</green></bold>",
                    List.of("<white>" + label + "</white>"));
            case "tokens" -> net.coremc.foundation.util.ItemUtil.create(Material.NETHER_STAR,
                    "<bold><aqua>" + format(sec.getLong("amount", 0)) + " Sky Tokens</aqua></bold>",
                    List.of("<white>" + label + "</white>"));
            case "credits" -> net.coremc.foundation.util.ItemUtil.create(Material.GOLD_NUGGET,
                    "<bold><gold>" + format(sec.getLong("amount", 0)) + " Credits</gold></bold>",
                    List.of("<white>" + label + "</white>"));
            case "armour" -> armour.displaySet(sec.getString("set", ""));
            case "progression_armour" -> progressionSetDisplay(sec.getString("set", ""));
            case "companion" -> companionDisplay(sec.getString("companion", ""));
            case "tool" -> displayTool(sec.getString("tool", ""));
            case "key" -> keyItem.build(KeyId.byId(sec.getString("key", "")), sec.getInt("amount", 1));
            case "tag" -> net.coremc.foundation.util.ItemUtil.create(Material.NAME_TAG,
                    "<bold><yellow>" + label + "</yellow></bold>", List.of());
            case "cosmetic" -> net.coremc.foundation.util.ItemUtil.create(Material.PAINTING,
                    "<bold><light_purple>" + label + "</light_purple></bold>", List.of());
            case "generator" -> net.coremc.foundation.util.ItemUtil.create(Material.REDSTONE_BLOCK,
                    "<bold><red>" + label + "</red></bold>", List.of());
            case "spawner" -> net.coremc.foundation.util.ItemUtil.create(Material.SPAWNER,
                    "<bold><red>" + label + "</red></bold>", List.of());
            default -> grantItemDisplay(sec);
        };
    }

    private ItemStack ItemUtilPlaceholder() {
        return net.coremc.foundation.util.ItemUtil.create(Material.BARRIER, "<red>Unknown Reward</red>", List.of());
    }

    private void grantMoney(final Player p, final long amount) {
        if (amount <= 0) return;
        final double mult = CoreMC.getInstance().companions().manager().moneyMultiplier(p);
        new Economy(plugin).addMoney(p.getUniqueId(), Math.round(amount * mult));
    }

    private void grantTokens(final Player p, final long amount) {
        if (amount <= 0) return;
        final double mult = CoreMC.getInstance().companions().manager().tokenMultiplier(p);
        CoreMC.getInstance().progression().tokens().add(p.getUniqueId(),
                Math.round(amount * mult), "Crate reward", "CRATES");
    }

    private void grantCredits(final Player p, final long amount) {
        if (amount <= 0 || creditsApi == null || creditsAdd == null) return;
        try {
            creditsAdd.invoke(creditsApi, p, amount);
        } catch (final Throwable t) {
            plugin.getLogger().warning("[crates] credits grant failed: " + t.getMessage());
        }
    }

    private void grantItem(final Player p, final org.bukkit.configuration.ConfigurationSection sec) {
        final ItemStack it = buildItem(sec);
        give(p, it);
    }

    private ItemStack grantItemDisplay(final org.bukkit.configuration.ConfigurationSection sec) {
        return buildItem(sec);
    }

    private ItemStack buildItem(final org.bukkit.configuration.ConfigurationSection sec) {
        final String matName = sec.getString("material", "PAPER");
        final Material m;
        try {
            m = Material.valueOf(matName.toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return ItemUtilPlaceholder();
        }
        final ItemStack it = new ItemStack(m, Math.max(1, sec.getInt("amount", 1)));
        final ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            final String disp = sec.getString("display");
            if (disp != null) net.coremc.foundation.util.ItemUtil.setPlainName(meta, disp);
            final var lore = sec.getStringList("lore");
            if (!lore.isEmpty()) {
                final var lines = new java.util.ArrayList<net.kyori.adventure.text.Component>();
                final var mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
                for (final String l : lore) lines.add(mm.deserialize(l));
                meta.lore(lines);
            }
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                    org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                    org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
            it.setItemMeta(meta);
        }
        return it;
    }

    private void grantTool(final Player p, final String toolType) {
        if (toolType == null || toolType.isEmpty()) return;
        try {
            final var tm = CoreMC.getInstance().omnitools().tools();
            final var t = net.coremc.skyblock.omnitools.tool.ToolType.valueOf(toolType.toUpperCase());
            final String role = toolTypeToRole(t);
            final ItemStack it = tm.makeTool(role);
            if (it != null) give(p, it);
        } catch (final IllegalArgumentException e) {
            plugin.getLogger().warning("[crates] unknown tool type: " + toolType);
        }
    }

    private static String toolTypeToRole(final net.coremc.skyblock.omnitools.tool.ToolType t) {
        return switch (t) {
            case PICKAXE -> "MINING";
            case AXE -> "LOGGING";
            case HOE -> "FARMING";
            case SWORD -> "SLAYING";
            case ROD -> "FISHING";
        };
    }

    private void grantCompanion(final Player p, final String companionId, final String rarity) {
        if (companionId == null || companionId.isEmpty()) return;
        net.coremc.skyblock.companion.CompanionManager.Rarity r;
        try {
            r = net.coremc.skyblock.companion.CompanionManager.Rarity.valueOf(rarity.toUpperCase());
        } catch (final IllegalArgumentException e) {
            r = net.coremc.skyblock.companion.CompanionManager.Rarity.COMMON;
        }
        CoreMC.getInstance().companions().manager().grant(p, companionId, r, 1);
    }

    private ItemStack companionDisplay(final String companionId) {
        if (companionId == null || companionId.isEmpty()) return ItemUtilPlaceholder();
        final var cm = CoreMC.getInstance().companions().manager();
        if (!cm.exists(companionId)) return ItemUtilPlaceholder();
        final String colour = cm.rarityColour(net.coremc.skyblock.companion.CompanionManager.Rarity.COMMON);
        final java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("<" + colour + ">Rarity: COMMON</" + colour + ">");
        lore.add("");
        lore.add("<white>Role: " + cm.role(companionId).name() + "</white>");
        lore.add("<white>XP Task: " + cm.xpTask(companionId) + "</white>");
        lore.add("");
        lore.add("<gold>Merge 6 to upgrade rarity</gold>");
        final org.bukkit.Material mat;
        try {
            mat = org.bukkit.Material.valueOf(cm.material(companionId).toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return net.coremc.foundation.util.ItemUtil.create(org.bukkit.Material.PAINTING,
                    "<bold><" + colour + ">" + cm.displayName(companionId) + "</" + colour + "></bold>", lore);
        }
        return net.coremc.foundation.util.ItemUtil.create(mat,
                "<bold><" + colour + ">" + cm.displayName(companionId) + "</" + colour + "></bold>", lore);
    }

    private ItemStack progressionSetDisplay(final String setId) {
        if (setId == null || setId.isEmpty()) return ItemUtilPlaceholder();
        final var pa = progressionArmour;
        final String colour = pa.colour(setId);
        final String name = pa.displayName(setId);
        final java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("<" + colour + ">" + pa.type(setId) + " Set</" + colour + ">");
        lore.add("");
        lore.add("<white>Abilities:</white>");
        for (final String ab : pa.abilities(setId)) lore.add("<white>• " + ab + "</white>");
        lore.add("");
        lore.add("<gold>Full Set — Virtual (Lootbox Legendary)</gold>");
        return net.coremc.foundation.util.ItemUtil.create(org.bukkit.Material.LEATHER_CHESTPLATE,
                "<bold><" + colour + ">" + name + " Set</" + colour + "></bold>", lore);
    }

    private ItemStack displayTool(final String toolType) {
        try {
            final var tm = CoreMC.getInstance().omnitools().tools();
            final var t = net.coremc.skyblock.omnitools.tool.ToolType.valueOf(toolType.toUpperCase());
            final ItemStack it = tm.makeTool(toolTypeToRole(t));
            return it == null ? ItemUtilPlaceholder() : it;
        } catch (final IllegalArgumentException e) {
            return ItemUtilPlaceholder();
        }
    }

    private void grantTag(final Player p, final String tag) {
        if (tag == null || tag.isEmpty()) return;
        // Tags are chat tags; unlock via the cosmetics bridge if present.
        grantCosmetic(p, tag);
    }

    private void grantCosmetic(final Player p, final String id) {
        if (id == null || id.isEmpty() || cosmeticsApi == null || cosmeticsUnlock == null) return;
        try {
            cosmeticsUnlock.invoke(cosmeticsApi, p, id);
        } catch (final Throwable t) {
            plugin.getLogger().warning("[crates] cosmetic unlock failed: " + t.getMessage());
        }
    }

    private void grantKey(final Player p, final String keyId, final int amount) {
        final KeyId id = KeyId.byId(keyId);
        if (id == null) return;
        give(p, keyItem.build(id, Math.max(1, amount)));
    }

    private void grantGenerator(final Player p, final String gen) {
        if (gen == null || gen.isEmpty()) return;
        final var gm = CoreMC.getInstance().gens().generators();
        if (gm.get(gen) != null) gm.give(p, gen);
    }

    private void grantSpawner(final Player p, final String spawner) {
        if (spawner == null || spawner.isEmpty()) return;
        final var sm = CoreMC.getInstance().spawners().spawners();
        if (sm.get(spawner) != null) sm.give(p, spawner);
    }

    private void give(final Player p, final ItemStack it) {
        if (it == null) return;
        final java.util.Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (final ItemStack drop : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), drop);
        }
    }

    private String format(final long n) {
        return String.format(java.util.Locale.ROOT, "%,d", n);
    }

    /** Get the display name for a reward defined in config.yml (for legacy lootbox messages). */
    public String getDisplay(final String rewardId) {
        final var cfg = plugin.getConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("crates.rewards." + rewardId);
        if (sec == null) sec = cfg.getConfigurationSection("crates.lootbox-rewards." + rewardId);
        if (sec == null) return rewardId;
        return sec.getString("display", rewardId);
    }

    /**
     * Apply a lootbox reward by composite key {@code lootbox:<boxId>:<rewardId>}.
     * Parses the composite id, loads the per-lootbox YAML via LootboxConfig,
     * and delegates to {@link #apply(Player, net.coremc.skyblock.crates.config.LootboxConfig.Reward)}.
     * Returns false if the key format is wrong or the lootbox/reward is not found.
     */
    public boolean applyLootboxReward(final Player p, final String compositeKey) {
        if (compositeKey == null || !compositeKey.startsWith("lootbox:")) return false;
        final String rest = compositeKey.substring("lootbox:".length());
        final int sep = rest.indexOf(':');
        if (sep < 0) return false;
        final String boxId = rest.substring(0, sep);
        final String rewardId = rest.substring(sep + 1);
        final net.coremc.skyblock.crates.config.LootboxConfig cfg = loadBox(boxId);
        if (cfg == null) return false;
        final net.coremc.skyblock.crates.config.LootboxConfig.Reward r = cfg.rewards().stream()
                .filter(rw -> rw.id().equals(rewardId))
                .findFirst().orElse(null);
        if (r == null) return false;
        return apply(p, r);
    }

    /**
     * Apply a lootbox reward from a {@link net.coremc.skyblock.crates.config.LootboxConfig.Reward}
     * directly. This is the primary entry point for the lootbox system — used by
     * LootboxManager which has already resolved the Reward object server-side.
     */
    public boolean applyLootboxReward(final Player p, final String boxId, final String rewardId,
                                      final net.coremc.skyblock.crates.config.LootboxConfig.Reward r) {
        return apply(p, r);
    }

    /**
     * Resolve a composite lootbox reward key {@code lootbox:<boxId>:<rewardId>}.
     * Loads the per-lootbox YAML via {@link LootboxConfig}, finds the reward by id,
     * and delegates to {@link #displayItem(LootboxConfig.Reward)}.
     * Returns a BARRIER placeholder if not found.
     */
    public ItemStack displayItemComposite(final String compositeKey) {
        if (compositeKey == null || !compositeKey.startsWith("lootbox:")) return null;
        final String rest = compositeKey.substring("lootbox:".length());
        final int sep = rest.indexOf(':');
        if (sep < 0) return null;
        final String boxId = rest.substring(0, sep);
        final String rewardId = rest.substring(sep + 1);
        final var cfg = loadBox(boxId);
        if (cfg == null) return null;
        final var r = cfg.rewards().stream().filter(rw -> rw.id().equals(rewardId)).findFirst().orElse(null);
        if (r == null) return null;
        return displayItem(r);
    }

    /** Cache-and-load a lootbox config by id. */
    private net.coremc.skyblock.crates.config.LootboxConfig loadBox(final String boxId) {
        net.coremc.skyblock.crates.config.LootboxConfig cfg = boxCache.get(boxId);
        if (cfg == null) {
            try {
                cfg = new net.coremc.skyblock.crates.config.LootboxConfig(plugin, boxId);
                boxCache.put(boxId, cfg);
            } catch (final Exception e) {
                plugin.getLogger().warning("[crates] failed to load lootbox config '" + boxId + "': " + e.getMessage());
                return null;
            }
        }
        return cfg;
    }
}
