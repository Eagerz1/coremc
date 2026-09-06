package net.coremc.chatcolor.manager;

/** A single colour or gradient option shown in the GUI. */
public final class ColourDef {

    public enum Kind { SOLID, GRADIENT }

    private final String id;
    private final Kind kind;
    private final String display;          // MiniMessage shown on the item
    private final String value;            // MiniMessage applied to a message
    private final String permission;       // may be empty -> always unlocked
    private final String requiredNote;     // shown when locked
    private final int slot;
    private final org.bukkit.Material material; // GUI item material (dye/concrete)
    private final boolean bold;           // render the chat message in bold

    public ColourDef(final String id, final Kind kind, final String display,
                     final String value, final String permission,
                     final String requiredNote, final int slot,
                     final org.bukkit.Material material, final boolean bold) {
        this.id = id;
        this.kind = kind;
        this.display = display;
        this.value = value;
        this.permission = permission;
        this.requiredNote = requiredNote;
        this.slot = slot;
        this.material = material;
        this.bold = bold;
    }

    public String id() { return id; }
    public Kind kind() { return kind; }
    public String display() { return display; }
    public String value() { return value; }
    public String permission() { return permission; }
    public String requiredNote() { return requiredNote; }
    public int slot() { return slot; }
    public org.bukkit.Material material() { return material; }
    public boolean bold() { return bold; }
}
