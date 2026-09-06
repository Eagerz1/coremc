package net.coremc.chatcolor.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.coremc.chatcolor.manager.ColourManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies the player's selected colour to their chat message.
 *
 * <p>Uses Paper's {@link AsyncChatEvent} so the coloured message is set as an
 * Adventure {@link Component} — MiniMessage gradients actually render instead
 * of appearing as literal text.</p>
 */
public final class ChatListener implements Listener {

    private final ColourManager colours;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    // Strip leading colour/gradient tags so re-applying doesn't stack.
    private static final Pattern LEADING_TAG = Pattern.compile("^(<(?:color|gradient)[^>]*>)(.*)(</(?:color|gradient)>)$");

    public ChatListener(final org.bukkit.plugin.java.JavaPlugin plugin, final ColourManager colours) {
        this.colours = colours;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(final AsyncChatEvent event) {
        String msg = MM.serialize(event.originalMessage());
        final Matcher m = LEADING_TAG.matcher(msg);
        if (m.matches()) {
            msg = m.group(2);
        }
        final Component coloured = MM.deserialize(colours.format(event.getPlayer().getUniqueId(), msg));
        event.message(coloured);
    }
}
