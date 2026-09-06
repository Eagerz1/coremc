package net.coremc.tags.manager;

import net.coremc.tags.model.TagDef;
import net.coremc.tags.storage.TagStorage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;

/**
 * Loads the tag catalogue from tags.yml and caches per-player ownership on the
 * main thread (populated asynchronously). Chat lookups read only from the cache
 * so they never touch the database.
 */
public final class TagManager {

    private final JavaPlugin plugin;
    private final TagStorage storage;
    private final Map<String, TagDef> catalogue = new ConcurrentHashMap<>();
    // uuid -> owned tags (cached, async-populated)
    private final Map<UUID, PlayerCache> cache = new ConcurrentHashMap<>();
    private final Object loadLock = new Object();

    private static final class PlayerCache {
        final Set<String> owned;
        String active;
        PlayerCache(final Set<String> owned, final String active) {
            this.owned = owned;
            this.active = active;
        }
    }

    public TagManager(final JavaPlugin plugin, final TagStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        loadCatalogue();
    }

    @SuppressWarnings("unchecked")
    private void loadCatalogue() {
        final File f = new File(plugin.getDataFolder(), "tags.yml");
        if (!f.exists()) {
            plugin.saveResource("tags.yml", false);
        }
        final org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        final ConfigurationSection root = cfg.getConfigurationSection("tags");
        if (root == null) {
            return;
        }
        for (final String id : root.getKeys(false)) {
            final ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            final List<String> gradient = s.getStringList("gradient");
            catalogue.put(id.toLowerCase(java.util.Locale.ROOT), new TagDef(
                    id,
                    s.getString("display", id),
                    gradient,
                    s.getString("source", "admin"),
                    s.getDouble("price", 0),
                    s.getString("permission", ""),
                    s.getString("description", ""),
                    s.getString("obtain", "")));
        }
    }

    public List<TagDef> catalogue() {
        return new ArrayList<>(catalogue.values());
    }

    public TagDef getTag(final String id) {
        return id == null ? null : catalogue.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean isDefined(final String id) {
        return getTag(id) != null;
    }

    // ---- ownership cache ----

    /** Trigger an async load; safe to call on the main thread. */
    public void ensureLoaded(final UUID uuid) {
        if (cache.containsKey(uuid)) {
            return;
        }
        synchronized (loadLock) {
            if (cache.containsKey(uuid)) {
                return;
            }
            cache.put(uuid, new PlayerCache(new java.util.HashSet<>(), null));
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            final TagStorage.PlayerTags pt = storage.load(uuid);
            final PlayerCache pc = cache.computeIfAbsent(uuid, k -> new PlayerCache(new java.util.HashSet<>(), null));
            pc.owned.clear();
            pc.owned.addAll(pt.owned);
            pc.active = pt.active;
        });
    }

    public Set<String> getOwned(final UUID uuid) {
        final PlayerCache pc = cache.get(uuid);
        return pc == null ? java.util.Collections.emptySet() : pc.owned;
    }

    public boolean hasTag(final UUID uuid, final String tagId) {
        final PlayerCache pc = cache.get(uuid);
        return pc != null && pc.owned.contains(tagId.toLowerCase(java.util.Locale.ROOT));
    }

    public String getActive(final UUID uuid) {
        final PlayerCache pc = cache.get(uuid);
        return pc == null ? null : pc.active;
    }

    /** MiniMessage gradient-wrapped tag for chat, or null if none active. */
    public String getActiveDisplay(final UUID uuid) {
        final String active = getActive(uuid);
        if (active == null) {
            return null;
        }
        final TagDef def = getTag(active);
        return def == null ? null : def.gradientMiniMessage();
    }

    // ---- mutations (also persisted) ----

    public void unlock(final UUID uuid, final String tagId, final String source) {
        final PlayerCache pc = cache.computeIfAbsent(uuid, k -> new PlayerCache(new java.util.HashSet<>(), null));
        pc.owned.add(tagId.toLowerCase(java.util.Locale.ROOT));
        storage.unlock(uuid, tagId.toLowerCase(java.util.Locale.ROOT), source, System.currentTimeMillis());
    }

    public void remove(final UUID uuid, final String tagId) {
        final PlayerCache pc = cache.get(uuid);
        if (pc == null) {
            return;
        }
        final String norm = tagId.toLowerCase(java.util.Locale.ROOT);
        pc.owned.remove(norm);
        if (norm.equals(pc.active)) {
            pc.active = null;
        }
        storage.remove(uuid, norm);
    }

    public void setActive(final UUID uuid, final String tagId) {
        final PlayerCache pc = cache.computeIfAbsent(uuid, k -> new PlayerCache(new java.util.HashSet<>(), null));
        final String norm = tagId == null ? null : tagId.toLowerCase(java.util.Locale.ROOT);
        pc.active = norm;
        storage.setActive(uuid, norm);
    }

    public void invalidate(final UUID uuid) {
        cache.remove(uuid);
    }

    public void shutdown() {
        // persistence is async; nothing to flush synchronously.
    }
}
