package net.coremc.coreban.model;

import java.util.UUID;

/**
 * A single staff-only internal note attached to a player.
 * Keyed by the target's UUID so name changes do not break retrieval.
 */
public final class Note {

    private int id;
    private final UUID target;
    private final String playerName;
    private final String content;
    private final String staff;
    private final long createdAt; // epoch seconds

    public Note(final int id, final UUID target, final String playerName,
                final String content, final String staff, final long createdAt) {
        this.id = id;
        this.target = target;
        this.playerName = playerName;
        this.content = content;
        this.staff = staff;
        this.createdAt = createdAt;
    }

    public int id() { return id; }
    public void setId(final int id) { this.id = id; }
    public UUID target() { return target; }
    public String playerName() { return playerName; }
    public String content() { return content; }
    public String staff() { return staff; }
    public long createdAt() { return createdAt; }
}
