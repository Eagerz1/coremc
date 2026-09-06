package net.coremc.foundation.messages;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.foundation.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Central message system. Messages are defined in config.yml under {@code messages:}
 * and support MiniMessage plus a {@code <prefix>} token.
 */
public final class Messages {

    private final JavaPlugin plugin;
    private final Map<String, String> cache = new HashMap<>();
    private String prefix;

    public Messages(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.reload();
    }

    /** Re-read prefix + all message entries from config. */
    public void reload() {
        this.prefix = plugin.getConfig().getString("prefix",
                "<gradient:#00eaff:#7a5cff>CoreMC</gradient>");
        cache.clear();
        final var section = plugin.getConfig().getConfigurationSection("messages");
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                cache.put(key, section.getString(key, ""));
            }
        }
    }

    public @NotNull String getPrefix() {
        return prefix;
    }

    /**
     * Get a raw MiniMessage string for a key (with {@code <prefix>} intact).
     */
    public @Nullable String getRaw(final @NotNull String key) {
        return cache.get(key);
    }

    /** Parse a raw MiniMessage string (with {@code <prefix>} substituted). No forced decoration. */
    public @NotNull Component parse(final @NotNull String miniMessage, final @NotNull String prefix) {
        return ColorUtil.parse(miniMessage, prefix);
    }

    /** Resolve a config message key to a Component (prefix substituted). */
    public @NotNull Component resolve(final @NotNull String key) {
        final String raw = cache.getOrDefault(key, key);
        return ColorUtil.parse(raw, prefix);
    }

    /** Send a config message to a sender. */
    public void send(final @NotNull CommandSender sender, final @NotNull String key) {
        sender.sendMessage(resolve(key).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
    }

    /** Send a config message, substituting {@code {var}} placeholders. */
    public void send(final @NotNull CommandSender sender, final @NotNull String key,
                     final @NotNull Map<String, String> vars) {
        String raw = cache.getOrDefault(key, key);
        for (final var entry : vars.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        sender.sendMessage(ColorUtil.parse(raw, prefix).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
    }

    /** Send a fully custom MiniMessage string (prefix NOT auto-added). */
    public void sendRaw(final @NotNull CommandSender sender, final @NotNull String miniMessage) {
        sender.sendMessage(ColorUtil.parse(miniMessage, prefix).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
    }

    /** Broadcast a MiniMessage string to all players. */
    public void broadcast(final @NotNull Plugin source, final @NotNull String miniMessage) {
        source.getServer().broadcast(ColorUtil.parse(miniMessage, prefix).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
    }
}
