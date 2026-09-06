package net.coremc.skyblock.islandcore;

import net.coremc.coremc.CoreMC;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.storage.Island;
import net.coremc.skyblock.playerdata.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * The Core service — the single source of truth for every island's shared Core.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Persist per-island Core totals (money + Sky Tokens + total progression)
 *       through the existing island database ({@code island_stats}).</li>
 *   <li>Persist per-player Core contribution data through the existing player
 *       data store.</li>
 *   <li>Apply island-buff and live-event multipliers (via {@link CoreBuffApi} /
 *       {@link CoreEventApi}) to every contribution.</li>
 *   <li>Expose read access used by Island Top and future /core commands.</li>
 * </ul>
 *
 * <p>The service never owns a buff or event system — it only multiplies
 * configured base values through whatever provider is registered (defaulting to
 * identity). This keeps the foundation self-contained yet cleanly extensible.</p>
 */
public final class CoreService {

    private final JavaPlugin plugin;
    private final CoreConfig config;

    private CoreBuffApi buffApi = new CoreBuffApi() {};      // identity by default
    private CoreEventApi eventApi = new CoreEventApi() {};   // identity by default

    // island_stats keys the Core writes under. Island stats are stored as whole
    // numbers, so fractional Core money/tokens are kept as fixed-point CENTS
    // (×100) — this lets the intentionally-tiny per-action values still accumulate
    // instead of being floored to 0 on every single contribution.
    private static final String STAT_MONEY = "core_money_c";
    private static final String STAT_TOKENS = "core_tokens_c";
    private static final String STAT_PROGRESSION = "core_progression";
    private static final double CENTS = 100.0;

    public CoreService(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = new CoreConfig(plugin);
        // Seed the per-island floor on first touch is unnecessary; reads default to 0
        // plus the configured start-money which we add lazily to the stat on add.
    }

    // ---- provider registration (called by future buff/event systems) ----

    public void setBuffApi(final @NotNull CoreBuffApi api) { this.buffApi = api; }
    public void setEventApi(final @NotNull CoreEventApi api) { this.eventApi = api; }
    public CoreConfig config() { return config; }

    // ---- persistence helpers ----

    private PlayerDataManager pd() {
        return CoreMC.getInstance().playerDataManager();
    }

    private IslandApi api() {
        return CoreMC.getInstance().islands().api();
    }

    // ---- read access (island totals) ----

    public double islandMoney(final int islandId) {
        final double stored = api().getStatistic(islandId, STAT_MONEY) / CENTS;
        return stored + config.islandStartMoney();
    }

    public double islandTokens(final int islandId) {
        return api().getStatistic(islandId, STAT_TOKENS) / CENTS;
    }

    /** Total progression (kills) accumulated for the island across all mobs. */
    public long islandProgression(final int islandId) {
        return api().getStatistic(islandId, STAT_PROGRESSION);
    }

    // ---- read access (per-player) ----

    public double playerMoney(final @NotNull UUID uuid) {
        return pd().get(uuid).coreMoney();
    }

    public long playerTokens(final @NotNull UUID uuid) {
        return pd().get(uuid).coreTokens();
    }

    public long playerProgression(final @NotNull UUID uuid) {
        return pd().get(uuid).coreProgression();
    }

    // ---- the contribution entry point ----

    /**
     * Apply a Core contribution. Returns the (already buff/event-scaled) amounts
     * that were actually applied, or null if the contribution was cancelled.
     *
     * <p>Order of operations:</p>
     * <ol>
     *   <li>Fire {@link CoreContributeEvent} (cancellable).</li>
     *   <li>Apply island buff multipliers then live-event multipliers to money,
     *       tokens and progression (kills).</li>
     *   <li>Persist to the shared island Core + the contributing player.</li>
     * </ol>
     */
    public @Nullable AppliedContribution contribute(final @NotNull CoreContributionContext ctx) {
        // 1. pre-hook
        final CoreContributeEvent ev = new CoreContributeEvent(ctx);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return null;
        }

        // 2. buffs
        final double moneyBuff = buffApi.coreMoneyMultiplier(ctx.islandId);
        final double tokenBuff = buffApi.skyTokenMultiplier(ctx.islandId);

        // 3. events
        final double eventContrib = ctx.applyEventMultipliers ? eventApi.contributionMultiplier() : 1.0;
        final double eventProg = ctx.applyEventMultipliers ? eventApi.progressionMultiplier() : 1.0;
        final double eventToken = ctx.applyEventMultipliers ? eventApi.skyTokenMultiplier() : 1.0;

        final double money = ctx.baseMoney * moneyBuff * eventContrib;
        final double tokens = ctx.baseTokens * tokenBuff * eventToken;
        final long progression = Math.round(ctx.kills * eventProg); // kills already variant-scaled

        // 4. persist to island (store as fixed-point cents so tiny values accumulate)
        if (money > 0) api().addStatistic(ctx.islandId, STAT_MONEY, (long) Math.round(money * CENTS));
        if (tokens > 0) api().addStatistic(ctx.islandId, STAT_TOKENS, (long) Math.round(tokens * CENTS));
        if (progression > 0) api().addStatistic(ctx.islandId, STAT_PROGRESSION, progression);

        // 5. persist to player (player data keeps true doubles)
        final var pd = pd().get(ctx.playerId);
        if (money > 0) pd.addCoreMoney(money);
        if (tokens > 0) pd.addCoreTokens((long) Math.round(tokens));
        if (progression > 0) pd.addCoreProgression(progression);

        // Mark player data dirty so the autosave picks it up (sqlite island stats
        // are written synchronously above, player data via autosave).
        pd().markDirty(ctx.playerId);

        return new AppliedContribution(money, tokens, progression);
    }

    /** Result of a contribution (post-multiplier values actually applied). */
    public record AppliedContribution(double money, double tokens, long progression) {}

    // ---- island-top helper ----

    /**
     * Combined competitive value of an island's Core, used by Island Top.
     * Money and tokens are weighted (configurable) and summed.
     */
    public double islandCoreScore(final int islandId) {
        return islandMoney(islandId) * config.topMoneyWeight()
                + islandTokens(islandId) * config.topTokenWeight();
    }

    /** Convenience: resolve an island from a player for callers that only have a UUID. */
    public @Nullable Island islandOf(final @NotNull UUID player) {
        return api().getIsland(player);
    }

    static String statKeyMoney() { return STAT_MONEY; }
    static String statKeyTokens() { return STAT_TOKENS; }
    static String statKeyProgression() { return STAT_PROGRESSION; }

    // kept for parity with existing style; not used externally yet.
    @SuppressWarnings("unused")
    private static String lower(final String s) { return s.toLowerCase(Locale.ROOT); }
}
