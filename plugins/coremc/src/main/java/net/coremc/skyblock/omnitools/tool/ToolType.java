package net.coremc.skyblock.omnitools.tool;

/**
 * Tool categories. Each has its own set of upgrade keys (see config).
 */
public enum ToolType {
    PICKAXE, AXE, HOE, SWORD, ROD;

    public String display() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
