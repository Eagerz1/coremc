package net.coremc.skyblock.progression.role;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player's role level increases (after XP is credited and milestones/perks
 * are applied). This is the clean, read-only hook the future Quest system (and any other
 * module) uses to react to role progression without touching the award logic.
 *
 * <p>Example quest checks enabled by this event + the {@link RoleQuery} API:</p>
 * <ul>
 *   <li>Reach Miner Level X — {@code RoleQuery.level(player, Role.MINING) >= X}</li>
 *   <li>Reach Slayer Level X — {@code RoleQuery.level(player, Role.SLAYING) >= X}</li>
 *   <li>Mine X blocks — {@code RoleQuery.stat(player, Role.MINING, "blocks") >= X}</li>
 *   <li>Kill X mobs — {@code RoleQuery.stat(player, Role.SLAYING, "mobs") >= X}</li>
 *   <li>Earn X role XP — {@code RoleQuery.xp(player, Role.FARMING) >= X}</li>
 * </ul>
 */
public final class RoleLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final RoleManager.Role role;
    private final int fromLevel;
    private final int toLevel;

    public RoleLevelUpEvent(final Player player, final RoleManager.Role role,
                            final int fromLevel, final int toLevel) {
        this.player = player;
        this.role = role;
        this.fromLevel = fromLevel;
        this.toLevel = toLevel;
    }

    public Player getPlayer() { return player; }
    public RoleManager.Role getRole() { return role; }
    public int getFromLevel() { return fromLevel; }
    public int getToLevel() { return toLevel; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
