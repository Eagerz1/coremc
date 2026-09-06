package net.coremc.tags.model;

import java.util.List;

/** A single configurable tag definition (loaded from tags.yml). */
public final class TagDef {

    private final String id;
    private final String display;          // literal chat text, e.g. "[BETA]" or "★ W"
    private final List<String> gradient;   // list of hex colours for the tag-only gradient
    private final String source;           // how it is obtained: crate | chatcolor | store | admin
    private final double price;            // store price in Credits (0 = not purchasable)
    private final String permission;      // optional permission gating the tag
    private final String description;      // shown in GUI / /tags info
    private final String obtain;           // short "how to get" hint

    public TagDef(final String id, final String display, final List<String> gradient,
                  final String source, final double price, final String permission,
                  final String description, final String obtain) {
        this.id = id;
        this.display = display;
        this.gradient = gradient;
        this.source = source;
        this.price = price;
        this.permission = permission;
        this.description = description;
        this.obtain = obtain;
    }

    public String id() { return id; }
    public String display() { return display; }
    public List<String> gradient() { return gradient; }
    public String source() { return source; }
    public double price() { return price; }
    public String permission() { return permission; }
    public String description() { return description; }
    public String obtain() { return obtain; }

    /** Whether this tag can be bought through the store/chatcolor menu. */
    public boolean purchasable() {
        return price > 0 && ("store".equalsIgnoreCase(source) || "chatcolor".equalsIgnoreCase(source));
    }

    /** Build the MiniMessage gradient string for this tag, e.g. {@code <gradient:#a:#b><bold>[BETA]</bold></gradient>}. */
    public String gradientMiniMessage() {
        if (gradient == null || gradient.isEmpty()) {
            return "<bold>" + display + "</bold>";
        }
        final String stops = String.join(":", gradient);
        return "<gradient:" + stops + "><bold>" + display + "</bold></gradient>";
    }
}
