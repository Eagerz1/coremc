package net.coremc.skyblock.islandcore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reads the {@code island-core} configuration section (defaults applied on first
 * load). All Core-tunable values live under this section so operators have one
 * place to balance the system. Every accessor falls back to a safe default so a
 * partial config never breaks the foundation.
 */
public final class CoreConfig {

    private final YamlConfiguration cfg;

    public CoreConfig(final JavaPlugin plugin) {
        this.cfg = (YamlConfiguration) plugin.getConfig();
    }

    private double d(final String path, final double def) {
        return cfg.getDouble("island-core." + path, def);
    }

    // ---- generic action contributions (mining / farming / logging / fishing) ----
    public double moneyPerMining()  { return d("contributions.mining.money", 0.1); }
    public double tokensPerMining()  { return d("contributions.mining.tokens", 0.1); }
    public double moneyPerFarming()  { return d("contributions.farming.money", 0.1); }
    public double tokensPerFarming()  { return d("contributions.farming.tokens", 0.1); }
    public double moneyPerLogging()  { return d("contributions.logging.money", 0.1); }
    public double tokensPerLogging()  { return d("contributions.logging.tokens", 0.1); }
    public double moneyPerFishing()  { return d("contributions.fishing.money", 0.1); }
    public double tokensPerFishing()  { return d("contributions.fishing.tokens", 0.1); }

    // ---- island-top weighting ----
    public double topMoneyWeight() { return d("island-top.money-weight", 1.0); }
    public double topTokenWeight() { return d("island-top.token-weight", 1.0); }

    // ---- island-wide progression caps where relevant ----
    /** Hard floor each island's Core starts with (0 by default). */
    public double islandStartMoney() { return d("island.start-money", 0.0); }

    /**
     * Resolve a configured value for a generic action (used by the activity
     * listener). Returns null fields when the action is unknown.
     */
    public static final class ActionValues {
        public final double money;
        public final double tokens;
        public ActionValues(final double money, final double tokens) {
            this.money = money; this.tokens = tokens;
        }
    }

    public ActionValues actionValues(final CoreAction action) {
        return switch (action) {
            case MINING  -> new ActionValues(moneyPerMining(), tokensPerMining());
            case FARMING -> new ActionValues(moneyPerFarming(), tokensPerFarming());
            case LOGGING -> new ActionValues(moneyPerLogging(), tokensPerLogging());
            case FISHING -> new ActionValues(moneyPerFishing(), tokensPerFishing());
            default      -> new ActionValues(0, 0);
        };
    }
}
