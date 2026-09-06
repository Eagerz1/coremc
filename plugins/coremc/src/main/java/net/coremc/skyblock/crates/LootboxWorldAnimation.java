package net.coremc.skyblock.crates;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.crates.config.LootboxConfig;
import net.coremc.skyblock.crates.config.LootboxConfig.AnimationConfig;
import net.coremc.skyblock.crates.config.LootboxConfig.Reward;
import net.coremc.skyblock.crates.rarity.Rarity;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * World-space (non-GUI) lootbox opening animation.
 *
 * <p>Replaces the old inventory-GUI opener. The lootbox is shown as an invisible
 * armour stand wearing the lootbox item, floating in front of the player. It rises
 * slowly, spins (accelerating then decelerating), emits particles throughout
 * (rising / spiral / burst / final burst), and reveals up to 8 reward items as
 * floating stands that appear gradually. Legendary/Mythic wins add an energy-build,
 * lightning strike, thunder and a huge particle burst before the reveal.</p>
 *
 * <p>NO inventory is ever opened. All state is server-side; anti-spam is enforced by
 * the manager's {@code activeOpenings} map before this is constructed.</p>
 */
public final class LootboxWorldAnimation {

    private static final int REWARD_COUNT = 8;

    private final JavaPlugin plugin;
    private final LootboxManager manager;
    private final LootboxManager.LootboxSession session;
    private final Player player;
    private final LootboxConfig box;
    private final AnimationConfig anim;
    private final Reward winner;

    private final Location origin;      // base location in front of the player (ground)
    private final Location center;      // current lootbox location (moves during rise)
    private final org.bukkit.Color particleColor;
    private final Particle particle;

    private ArmorStand lootboxStand;
    private final List<ArmorStand> rewardStands = new ArrayList<>();
    private final List<Location> rewardSlots = new ArrayList<>();

    private BukkitTask task;
    private int tick = 0;
    private int revealed = 0;
    private boolean finished = false;
    private boolean legendaryResolved = false;

    // Stage boundaries (ticks) derived from config.
    private final int riseEnd;
    private final int spinEnd;
    private final int slowEnd;
    private final int pauseEnd;
    private final int legendaryPhase; // extra ticks after pause for legendary build+strike

    public LootboxWorldAnimation(final JavaPlugin plugin, final LootboxManager manager,
                                 final LootboxManager.LootboxSession session) {
        this.plugin = plugin;
        this.manager = manager;
        this.session = session;
        this.player = plugin.getServer().getPlayer(session.playerId());
        this.box = session.box();
        this.anim = box.animation();
        this.winner = session.winningReward();

        this.riseEnd = anim.riseTicks;
        this.spinEnd = riseEnd + anim.spinTicks;
        this.slowEnd = spinEnd + anim.spinSlowdownTicks;
        this.pauseEnd = slowEnd + anim.revealPauseTicks;
        this.legendaryPhase = (winner != null && (winner.rarity() == Rarity.LEGENDARY
                || winner.rarity() == Rarity.MYTHIC)) ? 50 : 0;

        this.particleColor = parseColor(anim.particleColour, box.iconColour());
        this.particle = parseParticle(anim.particleType);

        // Position the animation ~2.2 blocks in front of the player, on the ground.
        final Location pLoc = player.getLocation().clone();
        final Vector dir = pLoc.getDirection().setY(0).normalize();
        this.origin = pLoc.add(dir.multiply(2.2)).setDirection(pLoc.getDirection());
        this.origin.setY(Math.floor(pLoc.getY()) + 1.0);
        this.center = origin.clone();
    }

    /** Begin the world animation. */
    public void start() {
        if (player == null || !player.isOnline()) return;

        // Spawn the lootbox armour stand at the origin.
        lootboxStand = spawnStand(origin.clone(), buildLootboxItem(false), true);
        // Pre-compute the 8 reward ring slots around the lootbox.
        for (int i = 0; i < REWARD_COUNT; i++) {
            final double ang = Math.PI * (0.15 + 0.7 * ((double) i / (REWARD_COUNT - 1)));
            final double r = 2.6;
            final Location slot = origin.clone().add(Math.cos(ang) * r, 1.6, Math.sin(ang) * r);
            rewardSlots.add(slot);
        }

        if (anim.playSounds) player.playSound(origin, parseSound(anim.soundOpen), 1.0f, 1.0f);

        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (player == null || !player.isOnline()) { cleanup(); cancel(); return; }
                tick++;
                step();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void step() {
        // Rise stage.
        if (tick <= riseEnd) { runRise(); return; }
        // Spin (accelerating) stage.
        final int spinTick = tick - riseEnd;
        if (spinTick <= anim.spinTicks) { runSpin(spinTick, anim.spinTicks); revealRolling(spinTick); return; }
        // Spin-down (decelerating) stage.
        final int slowTick = spinTick - anim.spinTicks;
        if (slowTick <= anim.spinSlowdownTicks) { runSpinSlow(slowTick, anim.spinSlowdownTicks); return; }
        // Pause + (legendary build/lightning) + reveal.
        final int pauseTick = slowTick - anim.spinSlowdownTicks;
        runPause(pauseTick);
    }

    private void runRise() {
        final double progress = Math.min(1.0, (double) tick / riseEnd);
        // Eased slow rise.
        final double eased = 1 - Math.pow(1 - progress, 3);
        final double height = anim.riseHeight * eased;
        center.setY(origin.getY() + height);
        lootboxStand.teleport(center);
        lootboxStand.setRotation((tick * 4) % 360, 0);
        spawnRiseParticles(progress);
        if (tick == 1 && anim.playSounds) player.playSound(origin, parseSound(anim.soundSpinStart), 0.6f, 0.9f);
    }

    private void runSpin(final int spinTick, final int total) {
        final double progress = (double) spinTick / total;
        // Accelerate in, hold fast, then this stage ends; deceleration is the next stage.
        final float rot = (float) (tick * (2 + progress * 26)); // grows fast
        lootboxStand.setRotation(rot % 360, 0);
        spawnSpiralParticles(progress, false);
        if (spinTick == 1 && anim.playSounds) player.playSound(origin, parseSound(anim.soundSpinLoop), 0.5f, 1.0f);
        if (spinTick % 20 == 0 && anim.playSounds) player.playSound(origin, parseSound(anim.soundSpinLoop), 0.25f, 1.0f);
    }

    private void runSpinSlow(final int slowTick, final int total) {
        final double progress = 1.0 - (double) slowTick / total; // 1 -> 0
        final float rot = (float) (tick * (2 + progress * 8));
        lootboxStand.setRotation(rot % 360, 0);
        spawnSpiralParticles(progress, true);
    }

    private void runPause(final int pauseTick) {
        final Rarity rarity = winner != null ? winner.rarity() : Rarity.COMMON;
        final boolean legendary = rarity == Rarity.LEGENDARY || rarity == Rarity.MYTHIC;

        if (legendary) {
            runLegendary(pauseTick);
            return;
        }

        // Regular: small hover + glow pulse, then reveal at end of pause.
        lootboxStand.setRotation((float) (tick * 3) % 360, 0);
        if (pauseTick % 6 == 0) spawnRarityParticles(rarity, 6);
        if (pauseTick >= anim.revealPauseTicks && !finished) {
            doFinalReveal(false);
        }
    }

    private void runLegendary(final int pauseTick) {
        final Rarity rarity = winner.rarity();
        final int buildEnd = 30; // energy build window
        if (pauseTick < buildEnd) {
            // Energy converges toward the lootbox.
            spawnConvergeParticles(rarity, pauseTick / (double) buildEnd);
            if (pauseTick % 8 == 0 && anim.playSounds) player.playSound(origin, Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.0f);
            return;
        }
        if (pauseTick == buildEnd) {
            // Lightning strike — genuine visual + thunder.
            final int strikes = rarity == Rarity.MYTHIC ? anim.lightningStrikes + 2 : anim.lightningStrikes;
            for (int i = 0; i < strikes; i++) {
                final int si = i;
                new BukkitRunnable() {
                    @Override public void run() {
                        if (player == null || !player.isOnline()) return;
                        final Location bolt = center.clone().add(
                                ThreadLocalRandom.current().nextDouble(-1.5, 1.5), 1.5,
                                ThreadLocalRandom.current().nextDouble(-1.5, 1.5));
                        if (anim.lightningEnabled) center.getWorld().strikeLightningEffect(bolt);
                        spawnLightningParticles(rarity);
                        player.playSound(bolt, parseSound(anim.lightningSound), 3.0f, 1.0f);
                        player.playSound(bolt, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.0f);
                        player.playSound(bolt, Sound.AMBIENT_SOUL_SAND_VALLEY_LOOP, 1.0f, 0.8f + si * 0.1f);
                    }
                }.runTaskLater(plugin, i * 12L);
            }
            // Huge burst + reveal shortly after the final strike.
            new BukkitRunnable() {
                @Override public void run() {
                    if (player == null || !player.isOnline()) return;
                    spawnRarityParticles(rarity, 120);
                    spawnFinalBurst();
                    if (anim.playSounds) player.playSound(origin, parseSound(anim.soundReveal), 1.5f, 1.0f);
                    doFinalReveal(true);
                }
            }.runTaskLater(plugin, strikes * 12L + 10L);
            return;
        }
        // After the strike sequence is scheduled, idle until the reveal task fires.
        lootboxStand.setRotation((float) (tick * 2) % 360, 0);
    }

    /** Reveal reward items one-by-one across the spin phase (up to 8). */
    private void revealRolling(final int spinTick) {
        // Spread the 8 reveals evenly across the spin.
        final int target = (int) ((double) (spinTick) / anim.spinTicks * REWARD_COUNT);
        while (revealed < target && revealed < REWARD_COUNT) {
            revealNextReward();
        }
    }

    private void revealNextReward() {
        if (revealed >= REWARD_COUNT) return;
        final int idx = revealed;
        revealed++;
        final List<Reward> pool = session.shuffledDisplayRewards();
        final Location slot = rewardSlots.get(idx);
        final ItemStack disp;
        if (pool.isEmpty() || idx >= pool.size()) {
            disp = ItemUtil.create(box.iconMaterial(), "<gray>???</gray>", List.of());
        } else {
            disp = pool.get(idx).displayItem(plugin, manager.rewards());
        }
        final ArmorStand stand = spawnStand(slot, disp, true);
        rewardStands.add(stand);
        if (anim.playSounds) player.playSound(slot, Sound.UI_BUTTON_CLICK, 0.3f, 1.0f);
        spawnRarityParticles(pool.isEmpty() || idx >= pool.size() ? Rarity.COMMON : pool.get(idx).rarity(), 8);
    }

    private void doFinalReveal(final boolean legendary) {
        if (finished) return;
        finished = true;

        // Clear the showcase items.
        for (final ArmorStand s : rewardStands) s.remove();
        rewardStands.clear();

        // Put the winning item on the lootbox stand.
        if (winner != null) {
            lootboxStand.setHelmet(winner.displayItem(plugin, manager.rewards()));
        }
        spawnFinalBurst();
        if (!legendary && anim.playSounds) player.playSound(origin, parseSound(anim.soundReveal), 1.0f, 1.0f);

        // Grant + message, then clean up after a short beat.
        new BukkitRunnable() {
            @Override public void run() { sendWinMessage(); finish(); }
        }.runTaskLater(plugin, 24L);
    }

    private void sendWinMessage() {
        if (player == null || !player.isOnline()) return;
        final Rarity rarity = winner != null ? winner.rarity() : Rarity.COMMON;
        final String colour = rarity.hex();
        final String display = winner != null ? winner.display() : "Unknown";
        if (rarity == Rarity.LEGENDARY || rarity == Rarity.MYTHIC) {
            final String label = rarity == Rarity.MYTHIC ? "MYTHIC" : "LEGENDARY";
            player.sendMessage(CoreFoundation.getInstance().messages().parse(
                    "<prefix> <bold><" + colour + ">" + label + "</" + colour + "> " + display + "</" + colour + "></bold>",
                    plugin.getConfig().getString("prefix")));
        } else {
            player.sendMessage(CoreFoundation.getInstance().messages().parse(
                    "<prefix> <dark_purple>You won</dark_purple> <bold><dark_purple>" + display + "</dark_purple></bold>",
                    plugin.getConfig().getString("prefix")));
        }
    }

    private void finish() {
        manager.grantReward(player, session);
        cleanup();
        if (task != null) task.cancel();
    }

    private void cleanup() {
        if (lootboxStand != null && !lootboxStand.isDead()) lootboxStand.remove();
        for (final ArmorStand s : rewardStands) if (s != null && !s.isDead()) s.remove();
        rewardStands.clear();
    }

    // ---- Particles ----

    private void spawnRiseParticles(final double progress) {
        final World w = center.getWorld();
        final Location l = center.clone();
        // Rising dust columns.
        for (int i = 0; i < 4; i++) {
            final double off = ThreadLocalRandom.current().nextDouble(-0.4, 0.4);
            final Location p = l.clone().add(off, ThreadLocalRandom.current().nextDouble(0, 1.2),
                    ThreadLocalRandom.current().nextDouble(-0.4, 0.4));
            w.spawnParticle(Particle.DUST, p, 1, 0.05, 0.05, 0.05, 0,
                    new Particle.DustOptions(particleColor, 1.1f + (float) progress));
        }
        w.spawnParticle(Particle.PORTAL, l.clone().add(0, 0.6, 0), 3, 0.3, 0.3, 0.3, 0.02);
    }

    private void spawnSpiralParticles(final double intensity, final boolean slowing) {
        final World w = center.getWorld();
        final int arms = 3;
        final int perArm = slowing ? 3 : 5;
        final double baseAng = tick * (slowing ? 0.4 : 0.9);
        final double radius = 0.9 + intensity * 0.6;
        for (int a = 0; a < arms; a++) {
            final double ang = baseAng + (Math.PI * 2 * a / arms);
            for (int j = 0; j < perArm; j++) {
                final double y = j * 0.18;
                final Location p = center.clone().add(Math.cos(ang) * radius, y, Math.sin(ang) * radius);
                w.spawnParticle(Particle.DUST, p, 1, 0.02, 0.02, 0.02, 0,
                        new Particle.DustOptions(particleColor, 1.0f));
            }
        }
        w.spawnParticle(Particle.END_ROD, center.clone().add(0, 0.8, 0), slowing ? 1 : 2, 0.2, 0.2, 0.2, 0.02);
    }

    private void spawnConvergeParticles(final Rarity rarity, final double build) {
        final World w = center.getWorld();
        final Color c = rarity.particleColor();
        final int n = 6 + (int) (build * 10);
        for (int i = 0; i < n; i++) {
            final double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            final double dist = 2.5 - build * 1.8;
            final Location p = center.clone().add(Math.cos(ang) * dist, 0.4 + build * 1.4, Math.sin(ang) * dist);
            w.spawnParticle(Particle.DUST, p, 1, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(c, 1.4f));
        }
    }

    private void spawnLightningParticles(final Rarity rarity) {
        final World w = center.getWorld();
        final Color c = rarity.particleColor();
        w.spawnParticle(Particle.DUST, center.clone().add(0, 1.5, 0), 50, 1.2, 1.2, 1.2, 0, new Particle.DustOptions(c, 1.6f));
        w.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.5, 0), 30, 1.0, 1.0, 1.0, 0.3);
        w.spawnParticle(Particle.FIREWORK, center.clone().add(0, 1.5, 0), 24, 1.0, 1.0, 1.0, 0.4);
    }

    private void spawnRarityParticles(final Rarity rarity, final int count) {
        final World w = center.getWorld();
        final Color c = rarity.particleColor();
        w.spawnParticle(Particle.DUST, center.clone().add(0, 0.8, 0), count, 1.0, 1.0, 1.0, 0, new Particle.DustOptions(c, 1.3f));
        w.spawnParticle(Particle.FIREWORK, center.clone().add(0, 0.8, 0), count / 2, 0.8, 0.8, 0.8, 0.3);
    }

    private void spawnFinalBurst() {
        final World w = center.getWorld();
        final Color c = winner != null ? winner.rarity().particleColor() : particleColor;
        // Large expanding ring burst.
        w.spawnParticle(Particle.DUST, center.clone().add(0, 0.8, 0), 80, 2.2, 1.5, 2.2, 0, new Particle.DustOptions(c, 1.8f));
        w.spawnParticle(Particle.END_ROD, center.clone().add(0, 0.8, 0), 40, 2.0, 2.0, 2.0, 0.3);
        w.spawnParticle(Particle.FIREWORK, center.clone().add(0, 0.8, 0), 40, 1.5, 1.5, 1.5, 0.5);
        w.spawnParticle(Particle.PORTAL, center.clone().add(0, 0.8, 0), 30, 1.5, 1.5, 1.5, 0.5);
    }

    // ---- Helpers ----

    private ArmorStand spawnStand(final Location loc, final ItemStack helmet, final boolean small) {
        final ArmorStand s = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        s.setVisible(false);
        s.setGravity(false);
        s.setInvulnerable(true);
        s.setMarker(false);
        s.setSmall(small);
        s.setBasePlate(false);
        s.setArms(false);
        s.setHelmet(helmet);
        s.setCustomNameVisible(false);
        return s;
    }

    private ItemStack buildLootboxItem(final boolean glowing) {
        final String name = "<bold><" + box.iconColour() + ">" + box.displayName() + "</" + box.iconColour() + "></bold>";
        final List<String> lore = new java.util.ArrayList<>();
        lore.add("<gray>Opening...</gray>");
        final ItemStack it = ItemUtil.create(box.iconMaterial(), name, lore);
        if (glowing) {
            final var meta = it.getItemMeta();
            if (meta != null) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                it.setItemMeta(meta);
            }
        }
        return it;
    }

    private static Sound parseSound(final String name) {
        try { return Sound.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (final IllegalArgumentException | NullPointerException e) { return Sound.BLOCK_ENDER_CHEST_OPEN; }
    }

    private static Particle parseParticle(final String name) {
        try { return Particle.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (final IllegalArgumentException | NullPointerException e) { return Particle.PORTAL; }
    }

    private static org.bukkit.Color parseColor(final String hex, final String fallbackName) {
        if (hex != null && !hex.isBlank()) {
            try {
                final String h = hex.startsWith("#") ? hex.substring(1) : hex;
                return org.bukkit.Color.fromRGB(
                        Integer.parseInt(h.substring(0, 2), 16),
                        Integer.parseInt(h.substring(2, 4), 16),
                        Integer.parseInt(h.substring(4, 6), 16));
            } catch (final Exception ignored) {}
        }
        return switch (fallbackName.toLowerCase(Locale.ROOT)) {
            case "dark_purple" -> org.bukkit.Color.fromRGB(0x6a0dad);
            case "gold" -> org.bukkit.Color.fromRGB(0xffd700);
            case "light_purple", "pink" -> org.bukkit.Color.fromRGB(0xc95ce6);
            case "aqua" -> org.bukkit.Color.fromRGB(0x2ee6e6);
            case "blue" -> org.bukkit.Color.fromRGB(0x3366ff);
            case "red" -> org.bukkit.Color.fromRGB(0xff2020);
            case "green" -> org.bukkit.Color.fromRGB(0x2ecc40);
            case "yellow" -> org.bukkit.Color.fromRGB(0xffee55);
            default -> org.bukkit.Color.PURPLE;
        };
    }
}
