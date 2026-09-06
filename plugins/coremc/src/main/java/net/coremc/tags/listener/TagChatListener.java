package net.coremc.tags.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.coremc.tags.CoreTags;
import net.coremc.tags.manager.TagManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** Composes the player's active tag (gradient only) in front of the message. */
public final class TagChatListener implements Listener {

    private final JavaPlugin plugin;
    private final TagManager manager;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public TagChatListener(final JavaPlugin plugin, final TagManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Runs after core-chatcolor (LOWEST) has coloured the message, so
     * {@code event.message()} is already the coloured Component. We prepend the
     * tag gradient as a sibling Component. Both render as Adventure Components
     * so MiniMessage gradients actually display.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onChat(final AsyncChatEvent event) {
        final String tag = manager.getActiveDisplay(event.getPlayer().getUniqueId());
        if (tag == null || tag.isEmpty()) {
            return;
        }
        final Component base = event.message();
        event.message(MM.deserialize(tag).append(Component.text(" ")).append(base));
    }
}
