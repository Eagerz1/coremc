package net.coremc.coreban.listener;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.coreban.model.Punishment;
import net.coremc.coreban.model.PunishmentType;
import net.coremc.coreban.storage.PunishmentStorage;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

/**
 * Blocks chat from muted players; expires mutes automatically.
 */
public final class ChatListener implements Listener {

    private final JavaPlugin plugin;
    private final PunishmentStorage storage;

    public ChatListener(final JavaPlugin plugin, final PunishmentStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @EventHandler
    public void onChat(final AsyncPlayerChatEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        Punishment active = CoreMC.getInstance().ban().muted().get(uuid);
        final long now = System.currentTimeMillis() / 1000;
        if (active == null) {
            active = storage.getActive(uuid, PunishmentType.MUTE);
            if (active != null) {
                CoreMC.getInstance().ban().muted().put(uuid, active);
            }
        }
        if (active == null) {
            return;
        }
        // Check expiry.
        if (active.durationSeconds() != -1 && active.expiredAt() <= now) {
            CoreMC.getInstance().ban().muted().remove(uuid);
            storage.setExpired(active.id(), now);
            return;
        }
        event.setCancelled(true);
        CoreFoundation.getInstance().messages().sendRaw(event.getPlayer(),
                CoreFoundation.getInstance().messages().getPrefix()
                + " <red>You are muted (" + net.coremc.coreban.TierLadder.formatDuration(active.durationSeconds()) + ").");
    }
}
