package net.coremc.tags.api;

import net.coremc.tags.manager.TagManager;
import net.coremc.tags.model.TagDef;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Public CoreTags API. Other CoreMC plugins (crate, store, chatcolor) use this
 * instead of touching the database directly.
 */
public final class CoreTagsApi {

    private final JavaPlugin plugin;
    private final TagManager manager;

    public CoreTagsApi(final JavaPlugin plugin, final TagManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** Permanently unlock a tag for a player. Returns false if the tag is unknown. */
    public boolean unlockTag(final Player player, final String tagId) {
        if (!manager.isDefined(tagId)) {
            return false;
        }
        final String id = tagId.toLowerCase(java.util.Locale.ROOT);
        final boolean already = manager.hasTag(player.getUniqueId(), id);
        manager.unlock(player.getUniqueId(), id, "api");
        if (already) {
            handleDuplicate(player, id);
            return true;
        }
        new TagUnlockEvent(player, id).callEvent();
        return true;
    }

    /** Remove a tag from a player. Fires {@link TagRemoveEvent}. */
    public void removeTag(final Player player, final String tagId) {
        final String id = tagId.toLowerCase(java.util.Locale.ROOT);
        if (!manager.hasTag(player.getUniqueId(), id)) {
            return;
        }
        manager.remove(player.getUniqueId(), id);
        new TagRemoveEvent(player, id).callEvent();
    }

    public boolean hasTag(final Player player, final String tagId) {
        return manager.hasTag(player.getUniqueId(), tagId);
    }

    public Set<String> getTags(final Player player) {
        return manager.getOwned(player.getUniqueId());
    }

    /** The active tag id, or null. */
    public String getActiveTag(final Player player) {
        return manager.getActive(player.getUniqueId());
    }

    /** Equip a tag. Fires {@link TagEquipEvent}. No-op if the player doesn't own it. */
    public void setActiveTag(final Player player, final String tagId) {
        final String id = tagId == null ? null : tagId.toLowerCase(java.util.Locale.ROOT);
        if (id != null && !manager.hasTag(player.getUniqueId(), id)) {
            return;
        }
        final String previous = manager.getActive(player.getUniqueId());
        manager.setActive(player.getUniqueId(), id);
        if (id != null) {
            new TagEquipEvent(player, id, previous).callEvent();
        }
    }

    /** Unequip the current tag. */
    public void clearActiveTag(final Player player) {
        setActiveTag(player, null);
    }

    public TagDef getTag(final String tagId) {
        return manager.getTag(tagId);
    }

    public List<TagDef> getAllTags() {
        return manager.catalogue();
    }

    // Configurable duplicate behaviour when a crate/store awards an owned tag.
    private void handleDuplicate(final Player player, final String id) {
        final String behaviour = plugin.getConfig().getString("duplicate.behaviour", "NONE");
        if ("NONE".equalsIgnoreCase(behaviour)) {
            return;
        }
        final String cmd = plugin.getConfig().getString("duplicate.command", "");
        final int amount = plugin.getConfig().getInt("duplicate.amount", 0);
        if (cmd.isEmpty() || amount <= 0) {
            return;
        }
        final String filled = cmd.replace("{player}", player.getName()).replace("{amount}", String.valueOf(amount));
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), filled));
    }
}
