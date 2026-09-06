package net.coremc.skyblock.events;

import net.kyori.adventure.bossbar.BossBar.Color;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * A configured event type.
 *
 * <p>Pure data holder derived from {@code events.<id>} in config. The framework reads
 * every definition at load time; the rotation simply references these by id. Nothing
 * here knows about scheduling — it only describes <i>what</i> an event is and <i>how</i>
 * it renders.</p>
 *
 * <p>Colours are stored as Adventure/MiniMessage colour names (e.g. {@code BLUE},
 * {@code #00eaff}) resolved from each of the four 15-minute sections. They back the
 * boss bar colour transition and the announcement text.</p>
 */
public final class EventDefinition {

    private final String id;
    private GameEventType type;
    private String displayName;
    private double multiplier;
    private int durationSeconds;
    private final List<String> sectionColours = new ArrayList<>();

    private EventDefinition(final String id) {
        this.id = id;
        this.type = GameEventType.OTHER;
        this.displayName = id;
        this.multiplier = 1.0;
        this.durationSeconds = 3600;
    }

    /** Build a definition from its config section. Missing values fall back to safe defaults. */
    public static EventDefinition fromConfig(final String id, final ConfigurationSection sec) {
        final EventDefinition d = new EventDefinition(id);
        if (sec == null) return d;

        d.type = GameEventType.from(sec.getString("type", "OTHER"));
        d.displayName = sec.getString("display-name", id);
        d.multiplier = sec.getDouble("multiplier", 2.0);
        d.durationSeconds = sec.getInt("duration", 3600);

        // Four 15-minute sections. Each may be a named colour or a #hex string.
        // If fewer than four are configured we repeat the last one / fall back to WHITE.
        final ConfigurationSection sections = sec.getConfigurationSection("sections");
        final List<String> raw = new ArrayList<>();
        if (sections != null) {
            for (int i = 1; i <= 4; i++) {
                raw.add(sections.getString(String.valueOf(i) + ".colour", null));
            }
        }
        while (raw.size() < 4) {
            raw.add(raw.isEmpty() ? "WHITE" : raw.get(raw.size() - 1));
        }
        d.sectionColours.addAll(raw);
        return d;
    }

    public String id() { return id; }
    public GameEventType type() { return type; }
    public String displayName() { return displayName; }
    public double multiplier() { return multiplier; }
    public int durationSeconds() { return durationSeconds; }

    /** Colour name/hex for the given 15-minute section (1..4). */
    public String sectionColour(final int section) {
        final int idx = Math.max(0, Math.min(3, section - 1));
        return sectionColours.get(idx);
    }

    /**
     * Resolve a section colour to an Adventure boss-bar {@link Color}.
     *
     * <p>Adventure's boss bar only supports 7 colours (PINK, BLUE, RED, GREEN,
     * YELLOW, PURPLE, WHITE), so any configured name/hex is mapped to the closest
     * supported bar colour. The full-fidelity colour (any Adventure name or hex) is
     * still used for the boss-bar title text via {@link #sectionColour(int)}.</p>
     */
    public Color barColor(final int section) {
        return switch (sectionColour(section).toUpperCase(java.util.Locale.ROOT).replace("#", "")) {
            case "BLUE", "AQUA", "DARK_AQUA", "CYAN" -> Color.BLUE;
            case "RED", "DARK_RED", "CRIMSON" -> Color.RED;
            case "GREEN", "DARK_GREEN" -> Color.GREEN;
            case "YELLOW", "GOLD", "LIGHT_PURPLE" -> Color.YELLOW;
            case "PURPLE", "DARK_PURPLE", "MAGENTA" -> Color.PURPLE;
            case "PINK", "LIGHT_PINK" -> Color.PINK;
            default -> Color.WHITE;
        };
    }

    @Override
    public String toString() {
        return "EventDefinition{id=" + id + ",type=" + type + ",x" + multiplier + "}";
    }
}
