package net.coremc.coreban.storage;

import net.coremc.coreban.model.Punishment;
import net.coremc.coreban.model.PunishmentType;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for punishments.
 */
public interface PunishmentStorage {

    void init();

    /** Record a new punishment (assigns id). */
    Punishment insert(Punishment p);

    /** Most recent active punishment of a type for a player (ban/mute), or null. */
    Punishment getActive(UUID target, PunishmentType type);

    /** All punishments for a player, newest first. */
    List<Punishment> history(UUID target);

    /** Count of past punishments of (type, tier) for a player -> next offence number. */
    int offenceCount(UUID target, PunishmentType type, int tier);

    void setExpired(int id, long expiredAt);
    void setAppealed(int id, boolean appealed);
    void close();
}
