package net.coremc.skyblock.progression.token;

import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Backwards-compatible façade over the persistent player-data system.
 *
 * <p>Historically Sky Tokens lived in their own {@code tokens.yml}. That created
 * a second source of truth for a player currency, which the persistent-data spec
 * forbids. This class is now a thin delegate to
 * {@link net.coremc.skyblock.playerdata.PlayerDataManager} — the single
 * authoritative store for all player-owned currencies, cosmetics and keys.</p>
 *
 * <p>All existing call sites ({@code crates}, {@code missions}, {@code /skytokens},
 * GUIs) keep working unchanged; their reads/writes now flow through the one
 * canonical store under {@code data/players/<uuid>.yml}.</p>
 *
 * @deprecated Use {@code CoreMC.getInstance().playerDataManager()} directly.
 */
@Deprecated
public final class TokenManager {

    public TokenManager(final JavaPlugin plugin) {
        // No own file/state: the PlayerDataManager owns persistence.
    }

    private net.coremc.skyblock.playerdata.PlayerDataManager pdm() {
        return CoreMC.getInstance().playerDataManager();
    }

    public long get(final UUID uuid) {
        return pdm().getSkyTokens(uuid);
    }

    public long add(final UUID uuid, final long amount) {
        return pdm().addSkyTokens(uuid, amount);
    }

    public boolean take(final UUID uuid, final long amount) {
        return pdm().removeSkyTokens(uuid, amount) >= amount;
    }

    public long add(final UUID uuid, final long amount, final String reason, final String executor) {
        return pdm().addSkyTokens(uuid, amount);
    }

    public long remove(final UUID uuid, final long amount, final String reason, final String executor) {
        final long taken = pdm().removeSkyTokens(uuid, amount);
        return taken == 0 && amount > 0 ? -1 : taken;
    }

    public long set(final UUID uuid, final long amount, final String reason, final String executor) {
        pdm().setSkyTokens(uuid, amount);
        return amount;
    }

    /** Transaction history is no longer retained per-token; balances live in PlayerData. */
    public List<String> history(final UUID uuid, final int limit) {
        return new ArrayList<>();
    }

    public void saveAll() {
        pdm().saveAll();
    }

    public void save() {
        pdm().saveAll();
    }
}
