package net.coremc.tags.storage;

import java.util.Set;
import java.util.UUID;

/**
 * Persistence boundary for tag ownership. Designed so a MySQL/MariaDB
 * implementation can replace {@link SqliteTagStorage} without touching the manager.
 *
 * <p>Implementations MUST perform all I/O off the main thread.</p>
 */
public interface TagStorage {

    /** Initialise the backing store. Called on enable. */
    boolean init();

    /** Tear down (close connections). */
    void shutdown();

    /** Load a player's owned tags + active tag. Returns null on error. */
    PlayerTags load(UUID uuid);

    /** Record a newly unlocked tag for a player. */
    void unlock(UUID uuid, String tagId, String source, long timestamp);

    /** Remove a tag from a player. */
    void remove(UUID uuid, String tagId);

    /** Persist the player's active (equipped) tag id (may be null). */
    void setActive(UUID uuid, String tagId);

    /** Snapshot of a player's tag ownership. */
    final class PlayerTags {
        public final Set<String> owned;
        public final String active;
        public PlayerTags(final Set<String> owned, final String active) {
            this.owned = owned;
            this.active = active;
        }
    }
}
