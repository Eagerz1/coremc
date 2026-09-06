package net.coremc.skyblock.crates.config;

import net.coremc.skyblock.crates.rarity.Rarity;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Loads, validates and serves a single premium lootbox configuration file
 * ({@code lootboxes/<name>.yml}).
 *
 * <p>A lootbox file contains: display settings (name, icon, material, colour, description),
 * a list of rewards (each with an id, type, rarity, chance as a percentage, plus type-specific
 * fields), animation settings, sound/particle definitions, and permission requirements.
 *
 * <p>Chance validation: every reward's {@code chance} must be between 0.0 and 100.0 inclusive.
 * The manager normalises them to a cumulative weight for weighted random selection. If the
 * total chance across all rewards is not 100.0, a warning is logged but the box still works
 * (chances are treated as relative weights).
 *
 * <p>Reward types reuse the existing {@link net.coremc.skyblock.crates.RewardResolver} dispatch
 * table (money, tokens, credits, item, armour, progression_armour, companion, key, cosmetic,
 * generator, spawner). Two new types — {@code skin} and {@code gradient} — delegate to the
 * cosmetics bridge (same as {@code cosmetic}) so no new persistence system is created.
 */
public final class LootboxConfig {

    private final JavaPlugin plugin;
    private final String id;
    private final File file;
    private final YamlConfiguration cfg;
    private final Logger logger;

    // Cached / validated
    private String displayName;
    private String description;
    private Material iconMaterial;
    private String iconColour;
    private String theme;
    private String permission;
    private String soundOpen;
    private String soundSpin;
    private String soundReveal;
    private String soundLegendary;
    private String soundMythic;
    private String particleType;
    private String particleColour;
    private List<Reward> rewards;
    private AnimationConfig animation;
    private boolean loaded;

    public LootboxConfig(final JavaPlugin plugin, final String id) {
        this.plugin = plugin;
        this.id = id;
        this.logger = plugin.getLogger();
        this.file = new File(plugin.getDataFolder(), "lootboxes" + File.separator + id + ".yml");
        // If the file doesn't exist on disk, try to load it from the jar resources.
        this.cfg = loadConfig();
        this.loaded = true;
        reload();
    }

    /** Load the YamlConfiguration, copying from resources if not on disk. */
    private YamlConfiguration loadConfig() {
        final YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        if (yml.getKeys(false).isEmpty() && !file.exists()) {
            // Try loading from jar resources.
            plugin.saveResource("lootboxes" + File.separator + id + ".yml", true);
            return YamlConfiguration.loadConfiguration(file);
        }
        return yml;
    }

    /** Re-read the file from disk and re-validate. */
    public void reload() {
        // Re-read from disk.
        final YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        this.displayName = yml.getString("display.name", id);
        this.description = yml.getString("display.description", "");
        final String matStr = yml.getString("display.icon-material", "ENDER_EYE");
        Material mat;
        try {
            mat = Material.valueOf(matStr.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            mat = Material.ENDER_EYE;
            logger.warning("[lootbox] " + id + ": invalid icon-material '" + matStr + "', defaulting to ENDER_EYE.");
        }
        this.iconMaterial = mat;
        this.iconColour = yml.getString("display.icon-colour", "dark_purple");
        this.theme = yml.getString("display.theme", id);
        this.permission = yml.getString("permission", "");

        // Sounds (with defaults).
        this.soundOpen = yml.getString("sounds.open", "ENTITY_ENDER_CHEST_OPEN");
        this.soundSpin = yml.getString("sounds.spin", "ENTITY_ITEM_PICKUP");
        this.soundReveal = yml.getString("sounds.reveal", "UI_TOAST_CHALLENGE_COMPLETE");
        this.soundLegendary = yml.getString("sounds.legendary", "ENTITY_LIGHTNING_BOLT_THUNDER");
        this.soundMythic = yml.getString("sounds.mythic", "ENTITY_ENDER_DRAGON_DEATH");
        // Particles.
        this.particleType = yml.getString("particles.type", "PORTAL");
        this.particleColour = yml.getString("particles.colour", "#aa00ff");

        // Load rewards.
        final List<Reward> list = new ArrayList<>();
        final ConfigurationSection rewardsSec = yml.getConfigurationSection("rewards");
        if (rewardsSec != null) {
            for (final String key : rewardsSec.getKeys(false)) {
                final ConfigurationSection rSec = rewardsSec.getConfigurationSection(key);
                if (rSec == null) continue;
                final Reward r = Reward.fromConfig(id, key, rSec, logger);
                if (r != null) list.add(r);
            }
        }
        // Validate chances.
        validateChances(list);

        // Sort by rarity order descending so the preview GUI can group by rarity.
        list.sort(Comparator.<Reward>comparingInt(r -> r.rarity().order()).reversed()
                .thenComparing(r -> r.display()));

        this.rewards = list;
        this.animation = new AnimationConfig(yml);
        this.loaded = true;
    }

    /** Check that all chances are valid (0–100) and log a warning if they don't sum to 100. */
    private void validateChances(final List<Reward> list) {
        double sum = 0;
        for (final Reward r : list) {
            if (r.chance() < 0.0 || r.chance() > 100.0) {
                logger.warning("[lootbox] " + id + ": reward '" + r.id()
                        + "' has invalid chance " + r.chance() + " (must be 0–100).");
            }
            sum += r.chance();
        }
        if (sum > 0 && Math.abs(sum - 100.0) > 0.01) {
            logger.warning("[lootbox] " + id + ": total chance is " + String.format(Locale.ROOT, "%.2f", sum)
                    + "% (expected 100.00%). Rewards will still be selectable using relative weights.");
        }
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public Material iconMaterial() { return iconMaterial; }
    public String iconColour() { return iconColour; }
    public String theme() { return theme; }
    public String permission() { return permission == null ? "" : permission; }
    public String soundOpen() { return soundOpen; }
    public String soundSpin() { return soundSpin; }
    public String soundReveal() { return soundReveal; }
    public String soundLegendary() { return soundLegendary; }
    public String soundMythic() { return soundMythic; }
    public String particleType() { return particleType; }
    public String particleColour() { return particleColour; }
    public List<Reward> rewards() { return rewards; }
    public AnimationConfig animation() { return animation; }
    public boolean hasPermission() { return permission != null && !permission.isEmpty(); }
    public YamlConfiguration rawConfig() { return cfg; }

    /** Build the physical lootbox item stack for a player's inventory. */
    public org.bukkit.inventory.ItemStack buildItem(final int amount) {
        final List<String> lore = new ArrayList<>();
        lore.add("<gray>Rare lootbox — place to open.</gray>");
        lore.add("");
        lore.add("<gray>Rarity tiers: " + raritySummary() + " </gray>");
        lore.add("<gray>Total rewards: " + rewards.size() + "</gray>");
        if (hasPermission()) {
            lore.add("<gray>Permission: " + permission + "</gray>");
        }
        lore.add("");
        lore.add("<" + iconColour + ">Right-click to preview. Place to open.</" + iconColour + ">");
        final org.bukkit.inventory.ItemStack item = net.coremc.foundation.util.ItemUtil.create(iconMaterial,
                "<bold><" + iconColour + ">" + displayName + "</" + iconColour + "></bold>", lore);
        final var meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "coremc.lootbox"),
                    org.bukkit.persistence.PersistentDataType.STRING, id);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                    org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                    org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Build a premium display item for the selection GUI card. */
    public org.bukkit.inventory.ItemStack buildCardItem() {
        final List<String> lore = new ArrayList<>();
        if (description != null && !description.isEmpty()) {
            lore.add("<gray>" + description + "</gray>");
        }
        lore.add("");
        lore.add("<gray>Rarity tiers: " + raritySummary() + " </gray>");
        lore.add("<gray>Total rewards: " + rewards.size() + "</gray>");
        if (hasPermission()) {
            lore.add("<gray>Permission: " + permission + "</gray>");
        }
        lore.add("");
        lore.add("<" + iconColour + ">[ VIEW REWARDS ]</" + iconColour + ">");
        lore.add("<" + iconColour + ">[ OPEN LOOTBOX ]</" + iconColour + ">");
        final org.bukkit.inventory.ItemStack item = net.coremc.foundation.util.ItemUtil.create(iconMaterial,
                "<bold><" + iconColour + ">" + displayName + "</" + iconColour + "></bold>", lore);
        // Add a custom model data hint if the resource pack provides one for this lootbox.
        final String csd = cfg.getString("display.custom-model-data");
        if (csd != null && !csd.isEmpty()) {
            try {
                final var meta = item.getItemMeta();
                if (meta != null) {
                    meta.setCustomModelData(Integer.parseInt(csd));
                    item.setItemMeta(meta);
                }
            } catch (final NumberFormatException ignored) {}
        }
        return item;
    }

    private String raritySummary() {
        final Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        for (final Reward r : rewards) {
            counts.merge(r.rarity(), 1, Integer::sum);
        }
        final StringBuilder sb = new StringBuilder();
        for (final Rarity r : Rarity.values()) {
            final Integer c = counts.get(r);
            if (c != null && c > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(r.name().charAt(0)).append(r.name().substring(1).toLowerCase())
                        .append(" x").append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Pick a single reward using weighted random selection based on configured chances.
     * Returns {@code null} if the pool is empty.
     */
    public @Nullable Reward pickRandom() {
        if (rewards.isEmpty()) return null;
        double total = 0;
        for (final Reward r : rewards) total += r.chance();
        if (total <= 0) return null;
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (final Reward r : rewards) {
            roll -= r.chance();
            if (roll < 0) return r;
        }
        return rewards.get(rewards.size() - 1);
    }

    /** Get all rewards of a specific rarity. */
    public List<Reward> ofRarity(final Rarity r) {
        return rewards.stream().filter(rw -> rw.rarity() == r).collect(Collectors.toList());
    }

    /**
     * Pick {@code count} random rewards from this lootbox's pool.
     * Each pick is independent weighted selection.
     */
    public List<Reward> pickRandom(final int count) {
        final List<Reward> won = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final Reward r = pickRandom();
            if (r != null) won.add(r);
        }
        return won;
    }

    /**
     * Pick a reward of at least the given rarity tier (for legendary+ slots).
     * Falls back to a normal pick if no reward of that tier exists.
     */
    public @Nullable Reward pickRarityAtLeast(final Rarity minRarity) {
        final List<Reward> pool = rewards.stream()
                .filter(r -> r.rarity().order() >= minRarity.order())
                .collect(Collectors.toList());
        if (pool.isEmpty()) return pickRandom();
        double total = 0;
        for (final Reward r : pool) total += r.chance();
        if (total <= 0) return pool.get(0);
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (final Reward r : pool) {
            roll -= r.chance();
            if (roll < 0) return r;
        }
        return pool.get(pool.size() - 1);
    }

    /**
     * Animation configuration loaded from the {@code animation} section of the lootbox YAML.
     * Controls timing, sounds, particles and effect visibility for the opening sequence.
     */
    public static final class AnimationConfig {
        public final int riseTicks;
        public final int spinTicks;
        public final int rewardAppearDelay;
        public final int rewardAppearInterval;
        public final int spinSlowdownTicks;
        public final int revealPauseTicks;
        public final double riseHeight;
        public final String particleType;
        public final String particleColour;
        public final String soundOpen;
        public final String soundSpinStart;
        public final String soundSpinLoop;
        public final String soundReveal;
        public final String soundLegendary;
        public final String soundMythic;
        public final boolean lightningEnabled;
        public final int lightningStrikes;
        public final String lightningSound;
        public final boolean screenShake;
        public final boolean playSounds;
        public final int staggerDelayTicks;

        AnimationConfig(final ConfigurationSection yml) {
            ConfigurationSection anim = yml.getConfigurationSection("animation");
            if (anim == null) anim = yml.getConfigurationSection("animations");
            if (anim == null) anim = yml.createSection("animation");

            this.riseTicks = anim.getInt("rise-ticks", 50);
            this.spinTicks = anim.getInt("spin-ticks", 100);
            this.rewardAppearDelay = anim.getInt("reward-appear-delay-ticks", 10);
            this.rewardAppearInterval = anim.getInt("reward-appear-interval-ticks", 4);
            this.spinSlowdownTicks = anim.getInt("spin-slowdown-ticks", 30);
            this.revealPauseTicks = anim.getInt("reveal-pause-ticks", 20);
            this.riseHeight = anim.getDouble("rise-height", 2.0);
            this.particleType = anim.getString("particle-type", "PORTAL");
            this.particleColour = anim.getString("particle-colour", "#aa00ff");
            this.soundOpen = anim.getString("sound-open", "ENTITY_ENDER_CHEST_OPEN");
            this.soundSpinStart = anim.getString("sound-spin-start", "ENTITY_ITEM_PICKUP");
            this.soundSpinLoop = anim.getString("sound-spin-loop", "BLOCK_AMBIENT_CRIMSON_FOREST");
            this.soundReveal = anim.getString("sound-reveal", "UI_TOAST_CHALLENGE_COMPLETE");
            this.soundLegendary = anim.getString("sound-legendary", "ENTITY_LIGHTNING_BOLT_THUNDER");
            this.soundMythic = anim.getString("sound-mythic", "ENTITY_ENDER_DRAGON_DEATH");
            this.lightningEnabled = anim.getBoolean("lightning-enabled", true);
            this.lightningStrikes = anim.getInt("lightning-strikes", 3);
            this.lightningSound = anim.getString("lightning-sound", "ENTITY_LIGHTNING_BOLT_THUNDER");
            this.screenShake = anim.getBoolean("screen-shake", true);
            this.playSounds = anim.getBoolean("play-sounds", true);
            this.staggerDelayTicks = anim.getInt("stagger-delay-ticks", 1);
        }
    }

    /**
     * A single reward definition parsed from the lootbox config.
     * Delegates type-specific granting/display to {@link net.coremc.skyblock.crates.RewardResolver}
     * by exposing the raw config section values the resolver already understands.
     */
    public static final class Reward {
        private final String boxId;
        private final String id;
        private final String type;
        private final Rarity rarity;
        private final double chance;
        private final String display;
        private final String description;
        private final String material;
        private final int amount;
        private final String set;
        private final String companion;
        private final String key;
        private final String cosmetic;
        private final String tag;
        private final String gradient;
        private final String skin;
        private final boolean announce;
        private final Map<String, Object> extra;
        private final Material displayMaterial;
        private final String rarityColour;
        private final String customModelData;

        private Reward(final String boxId, final String id, final String type,
                       final Rarity rarity, final double chance, final String display,
                       final String description, final String material, final int amount,
                       final String set, final String companion, final String key,
                       final String cosmetic, final String tag, final String gradient,
                       final String skin, final boolean announce,
                       final Map<String, Object> extra, final Material displayMaterial,
                       final String rarityColour, final String customModelData) {
            this.boxId = boxId;
            this.id = id;
            this.type = type;
            this.rarity = rarity;
            this.chance = chance;
            this.display = display;
            this.description = description;
            this.material = material;
            this.amount = amount;
            this.set = set;
            this.companion = companion;
            this.key = key;
            this.cosmetic = cosmetic;
            this.tag = tag;
            this.gradient = gradient;
            this.skin = skin;
            this.announce = announce;
            this.extra = extra;
            this.displayMaterial = displayMaterial;
            this.rarityColour = rarityColour;
            this.customModelData = customModelData;
        }

        static @Nullable Reward fromConfig(final String boxId, final String id,
                                             final ConfigurationSection sec, final Logger logger) {
            final String type = sec.getString("type", "item").toLowerCase(Locale.ROOT);
            final String rarityStr = sec.getString("rarity", "COMMON");
            final Rarity rarity = Rarity.byName(rarityStr);
            if (rarity == null) {
                logger.warning("[lootbox] " + boxId + ": reward '" + id
                        + "' has unknown rarity '" + rarityStr + "', defaulting to COMMON.");
            }
            final double chance = sec.getDouble("chance", 0.0);
            final String display = sec.getString("display", id);
            final String description = sec.getString("description", sec.getString("lore", ""));
            final String material = sec.getString("material", "PAPER");
            final int amount = sec.getInt("amount", 1);
            final String set = sec.getString("set", sec.getString("armour-set", ""));
            final String companion = sec.getString("companion", "");
            final String key = sec.getString("key", "");
            final String cosmetic = sec.getString("cosmetic", sec.getString("cosmetic_id", ""));
            final String tag = sec.getString("tag", "");
            final String gradient = sec.getString("gradient", "");
            final String skin = sec.getString("skin", sec.getString("skin_id", ""));
            final boolean announce = sec.getBoolean("announce", false);
            final String rarityColour = sec.getString("rarity-colour", "");
            final String customModelData = sec.getString("custom-model-data", "");

            // Collect extra fields for reward types that need additional data (e.g. tool).
            final Map<String, Object> extra = new HashMap<>();
            for (final String k : sec.getKeys(false)) {
                if (!List.of("type","rarity","chance","display","description","lore","material",
                        "amount","set","armour-set","companion","key","cosmetic","cosmetic_id",
                        "tag","gradient","skin","skin_id","announce","rarity-colour","custom-model-data").contains(k)) {
                    extra.put(k, sec.get(k));
                }
            }

            // Resolve display material.
            Material mat;
            try {
                mat = Material.valueOf(material.toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException e) {
                mat = Material.PAPER;
            }

            // For types where the display material is derived from the target system.
            if (companion != null && !companion.isEmpty()) {
                mat = companionDisplayMaterial(boxId, companion, mat);
            } else if (set != null && !set.isEmpty() && (type.equals("armour") || type.equals("progression_armour"))) {
                mat = Material.LEATHER_CHESTPLATE;
            } else if (key != null && !key.isEmpty() && type.equals("key")) {
                // Key material is handled by KeyItem, but for display we can use the key's material.
                // We leave mat as-is from config.
            }

            return new Reward(boxId, id, type, rarity != null ? rarity : Rarity.COMMON,
                    chance, display, description, material, amount, set, companion, key,
                    cosmetic, tag, gradient, skin, announce, extra, mat, rarityColour, customModelData);
        }

        /** Resolve the companion's display material from the CompanionManager. */
        private static Material companionDisplayMaterial(final String boxId, final String companionId,
                                                         final Material fallback) {
            try {
                final var cm = net.coremc.coremc.CoreMC.getInstance().companions().manager();
                if (cm != null && cm.exists(companionId)) {
                    final String mat = cm.material(companionId);
                    try {
                        return Material.valueOf(mat.toUpperCase(Locale.ROOT));
                    } catch (final IllegalArgumentException ignored) {}
                }
            } catch (final Throwable ignored) {}
            return fallback;
        }

        public String id() { return id; }
        public String boxId() { return boxId; }
        public String type() { return type; }
        public Rarity rarity() { return rarity; }
        public double chance() { return chance; }
        public String display() { return display; }
        public String description() { return description; }
        public String material() { return material; }
        public int amount() { return amount; }
        public String set() { return set; }
        public String companion() { return companion; }
        public String key() { return key; }
        public String cosmetic() { return cosmetic; }
        public String tag() { return tag; }
        public String gradient() { return gradient; }
        public String skin() { return skin; }
        public boolean announce() { return announce; }
        public Map<String, Object> extra() { return extra; }
        public Material displayMaterial() { return displayMaterial; }
        public String raretyColour() { return rarityColour; }
        public String customModelData() { return customModelData; }

        /**
         * Build a display ItemStack for this reward (for previews and the opening animation).
         */
        public @NotNull org.bukkit.inventory.ItemStack displayItem(final JavaPlugin plugin,
                                                                    final net.coremc.skyblock.crates.RewardResolver resolver) {
            final String colour = rarityColour.isEmpty()
                    ? rarity.configuredColor(plugin) : rarityColour;

            // Try the composite resolver first — it loads from the per-lootbox YAML.
            final org.bukkit.inventory.ItemStack existing = resolver.displayItemComposite("lootbox:" + boxId + ":" + id);
            if (existing != null && existing.getType() != org.bukkit.Material.BARRIER) {
                final var meta = existing.getItemMeta();
                if (meta != null) {
                    final var mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
                    meta.displayName(mm.deserialize("<bold><" + colour + ">" + display + "</" + colour + "></bold>")
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                                    net.kyori.adventure.text.format.TextDecoration.State.FALSE));
                    final List<String> loreLines = new ArrayList<>();
                    if (meta.lore() != null) {
                        for (final var c : meta.lore()) loreLines.add(mm.serialize(c));
                    }
                    loreLines.add("");
                    loreLines.add("<" + colour + ">Rarity: " + rarity.name() + "</" + colour + ">");
                    loreLines.add("<gray>Chance: " + String.format(Locale.ROOT, "%.2f", chance) + "%</gray>");
                    if (description != null && !description.isEmpty()) {
                        loreLines.add("");
                        loreLines.add("<gray>" + description + "</gray>");
                    }
                    meta.lore(boldLore(loreLines, mm));
                    existing.setItemMeta(meta);
                }
                // Apply custom model data if the resource pack provides it.
                if (customModelData != null && !customModelData.isEmpty()) {
                    final var meta2 = existing.getItemMeta();
                    if (meta2 != null) {
                        try {
                            meta2.setCustomModelData(Integer.parseInt(customModelData));
                            existing.setItemMeta(meta2);
                        } catch (final NumberFormatException ignored) {}
                    }
                }
                return existing;
            }

            // Fallback: build a basic display item.
            final List<String> lore = new ArrayList<>();
            if (description != null && !description.isEmpty()) lore.add("<gray>" + description + "</gray>");
            lore.add("");
            lore.add("<" + colour + ">Rarity: " + rarity.name() + "</" + colour + ">");
            lore.add("<gray>Chance: " + String.format(Locale.ROOT, "%.2f", chance) + "%</gray>");
            return net.coremc.foundation.util.ItemUtil.create(displayMaterial,
                    "<bold><" + colour + ">" + display + "</" + colour + "></bold>", lore);
        }

        private static List<net.kyori.adventure.text.Component> boldLore(final List<String> lines,
                                                                          final net.kyori.adventure.text.minimessage.MiniMessage mm) {
            final List<net.kyori.adventure.text.Component> out = new ArrayList<>(lines.size());
            for (final String l : lines) {
                out.add(mm.deserialize(l)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD,
                                net.kyori.adventure.text.format.TextDecoration.State.TRUE)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                                net.kyori.adventure.text.format.TextDecoration.State.FALSE));
            }
            return out;
        }

        /**
         * Apply this reward to a player via the existing RewardResolver.
         * The reward config is looked up by the resolver using the composite key
         * {@code lootbox:<boxId>:<rewardId>}.
         */
        public boolean apply(final net.coremc.skyblock.crates.RewardResolver resolver, final org.bukkit.entity.Player p) {
            // The RewardResolver needs to look up rewards from this lootbox config.
            // We delegate to the resolver which looks up crates.lootbox-rewards.<id> and crates.rewards.<id>.
            // For per-lootbox rewards, we need the resolver to also check the lootbox config.
            return resolver.applyLootboxReward(p, boxId, id, this);
        }
    }
}
