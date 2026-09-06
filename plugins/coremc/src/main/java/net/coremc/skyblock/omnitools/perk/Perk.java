package net.coremc.skyblock.omnitools.perk;

import net.coremc.skyblock.omnitools.stat.Stat;

/**
 * A perk unlocked at a configured Omnitool level.
 *
 * <p>Perks are intentionally <b>data-driven</b>: the bulk of a perk (unlock level,
 * description, and the stat bonuses it grants) lives in {@code omnitools.perks.<id>}
 * in config, so designers tune the kit without touching code. The {@link PerkManager}
 * loads every perk from config into a {@link Perk} instance at startup.</p>
 *
 * <p>For behaviour that genuinely needs code (an ability trigger, a special effect),
 * extend {@link AbstractPerk} and register a {@link PerkFactory} so future,
 * non-data-driven perks slot in without editing this class.</p>
 */
public interface Perk {

    /** Stable id (matches the config key under {@code omnitools.perks}). */
    String id();

    /** Display name shown in the GUI / lore. */
    String name();

    /** Short description shown in the GUI. */
    String description();

    /** Omnitool level at which this perk unlocks. */
    int unlockLevel();

    /** Whether this perk is for the given role (null/empty = all roles). */
    boolean appliesTo(String role);

    /** Stat bonuses this perk contributes to the modifier snapshot (may be empty). */
    java.util.List<net.coremc.skyblock.omnitools.stat.ModifierEngine.Modifier> modifiers();

    /** One-line summary of the effect (for GUI / lore), e.g. "+15% Mining Drops". */
    default String effectLine() {
        final var sb = new StringBuilder();
        for (final var m : modifiers()) {
            if (sb.length() > 0) sb.append(", ");
            final int pct = (int) Math.round(m.value() * 100);
            sb.append("+").append(pct).append("% ").append(m.stat().display());
        }
        return sb.toString();
    }

    /** Hook for code-driven perks (default: nothing). Called once when unlocked. */
    default void onUnlock(org.bukkit.entity.Player player, String role) {}
}
