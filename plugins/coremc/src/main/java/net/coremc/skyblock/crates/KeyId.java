package net.coremc.skyblock.crates;

/**
 * The seven CoreMC keys, in ascending tier order.
 *  Vote < River < Sky < Crimson ;  Boost ~ Crimson ;  Event / Monthly special.
 */
public enum KeyId {
    VOTE("vote", "Vote Key", 1),
    RIVER("river", "River Key", 2),
    SKY("sky", "Sky Key", 3),
    CRIMSON("crimson", "Crimson Key", 4),
    BOOST("boost", "Boost Key", 4),
    EVENT("event", "Event Key", 3),
    MONTHLY("monthly", "Monthly Key", 3);

    private final String id;
    private final String defaultName;
    private final int tier;

    KeyId(final String id, final String defaultName, final int tier) {
        this.id = id;
        this.defaultName = defaultName;
        this.tier = tier;
    }

    public String id() { return id; }
    public String defaultName() { return defaultName; }
    public int tier() { return tier; }

    public static KeyId byId(final String id) {
        if (id == null) return null;
        for (final KeyId k : values()) {
            if (k.id.equalsIgnoreCase(id)) return k;
        }
        return null;
    }
}
