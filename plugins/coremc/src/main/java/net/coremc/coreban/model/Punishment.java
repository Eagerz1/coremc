package net.coremc.coreban.model;

import java.util.UUID;

/**
 * A single punishment record.
 */
public final class Punishment {

    private int id;
    private final UUID target;
    private final PunishmentType type;
    private final int tier;
    private final int offenceNumber;
    private final long durationSeconds; // 0 = permanent, -1 = none (warn/kick)
    private final String reason;
    private final String staff;
    private final long issuedAt;
    private boolean appealed;
    private long expiredAt;

    public Punishment(final int id, final UUID target, final PunishmentType type, final int tier,
                      final int offenceNumber, final long durationSeconds, final String reason,
                      final String staff, final long issuedAt, final boolean appealed, final long expiredAt) {
        this.id = id;
        this.target = target;
        this.type = type;
        this.tier = tier;
        this.offenceNumber = offenceNumber;
        this.durationSeconds = durationSeconds;
        this.reason = reason;
        this.staff = staff;
        this.issuedAt = issuedAt;
        this.appealed = appealed;
        this.expiredAt = expiredAt;
    }

    public int id() { return id; }
    public void setId(final int id) { this.id = id; }
    public UUID target() { return target; }
    public PunishmentType type() { return type; }
    public int tier() { return tier; }
    public int offenceNumber() { return offenceNumber; }
    public long durationSeconds() { return durationSeconds; }
    public String reason() { return reason; }
    public String staff() { return staff; }
    public long issuedAt() { return issuedAt; }
    public boolean appealed() { return appealed; }
    public void setAppealed(final boolean appealed) { this.appealed = appealed; }
    public long expiredAt() { return expiredAt; }
    public void setExpiredAt(final long expiredAt) { this.expiredAt = expiredAt; }
}
