package net.coremc.chatcolor.api;

import net.coremc.chatcolor.manager.ColourDef;
import net.coremc.chatcolor.manager.ColourManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/** Public API for other CoreMC plugins. */
public final class ChatColorApi {

    private final JavaPlugin plugin;
    private final ColourManager colours;

    public ChatColorApi(final JavaPlugin plugin, final ColourManager colours) {
        this.plugin = plugin;
        this.colours = colours;
    }

    /** The player's selected colour id (empty = none). */
    public String getSelected(final UUID uuid) {
        return colours.getSelected(uuid);
    }

    /** Persist a selection and fire {@link ChatColorChangeEvent}. */
    public void setChatColor(final Player player, final String colourId) {
        colours.setSelected(player.getUniqueId(), colourId);
        plugin.getServer().getPluginManager().callEvent(
                new ChatColorChangeEvent(player, colourId));
    }

    /** Whether the player may use the given colour. */
    public boolean canUse(final Player player, final String colourId) {
        final ColourDef def = colours.byId(colourId);
        return def != null && colours.canUse(player, def);
    }

    /** Wrap a message in the player's selected colour. */
    public String format(final UUID uuid, final String message) {
        return colours.format(uuid, message);
    }

    /** Wrap a message in a specific colour id. */
    public String formatWith(final String colourId, final String message) {
        return colours.formatWith(colourId, message);
    }

    public List<ColourDef> catalogue() {
        return colours.catalogue();
    }
}
