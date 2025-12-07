package me.cheese.discsword.abilities;

import me.cheese.discsword.DiscSwordPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class Abilities {

    // ----------------------------------------------------------
    // VULCAN BYPASS HELPER
    // ----------------------------------------------------------
    private static void applyVulcanBypass(Player player) {
        DiscSwordPlugin plugin = DiscSwordPlugin.getInstance();
        if (plugin == null) return;

        player.setMetadata("discsword_ability_active",
                new FixedMetadataValue(plugin, true));

        // 18 ticks (~0.9s) of exemption to match Vulcan config
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                        player.removeMetadata("discsword_ability_active", plugin),
                18L);
    }

    // ----------------------------------------------------------
    // SAFE VECTOR HELPER (GLOBAL PROTECTION)
    // ----------------------------------------------------------
    private static Vector safe(Vector v) {
        if (v == null) {
            return new Vector(0, 0, 0);
        }

        double x = v.getX();
        double y = v.getY();
        double z = v.getZ();

        if (Double.isNaN(x) || Double.isInfinite(x)) x = 0;
        if (Double.isNaN(y) || Double.isInfinite(y)) y = 0;
        if (Double.isNaN(z) || Double.isInfinite(z)) z = 0;

        // If vector becomes zero-length, give a tiny upward nudge to keep it valid
        if (x == 0 && y == 0 && z == 0) {
            return new Vector(0, 0.1, 0);
        }

        return new Vector(x, y, z);
    }

    // ----------------------------------------------------------
    // MOVEMENT ABILITIES
    // ----------------------------------------------------------

    /** Lightning forward dash + speed boost */
    public static void lightningDash(Player player) {
        applyVulcanBypass(player);
        DiscSwordPlugin plugin = DiscSwordPlugin.getInstance();
        if (plugin == null) return;

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        Vector dash = player.getEyeLocation().getDirection().normalize().multiply(1.9);
        dash.setY(0.25);
        player.setVelocity(safe(dash));

        world.playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 1f, 1.7f);
        world.spawnParticle(Particle.CLOUD, loc, 25, 0.3, 0.15, 0.3, 0.02);
        world.spawnParticle(Particle.SWEEP_ATTACK, loc.add(0, 1, 0), 6, 0.1, 0.1, 0.1, 0);

        PotionEffect effect = new PotionEffect(PotionEffectType.SPEED, 40, 1, true, true, true);
        player.addPotionEffect(effect);
        plugin.markAbilityMovementEffect(player, PotionEffectType.SPEED);
    }

    /** Wind dash with amplifier scaling */
    public static void windDash(DiscSwordPlugin plugin, Player player, int amplifier) {
        applyVulcanBypass(player);
        if (plugin == null) return;

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        Vector direction = loc.getDirection().normalize();

        double dashSpeed = 1.0 + (0.2 * amplifier);
        Vector velocity = direction.clone().multiply(dashSpeed);
        velocity.setY(0.2 + 0.05 * amplifier);
        player.setVelocity(safe(velocity));

        // Apply & mark SPEED as ability movement effect
        int duration = 40;
        int speedAmp = Math.min(3, amplifier);
        PotionEffect effect = new PotionEffect(PotionEffectType.SPEED, duration, speedAmp, true, true, true);
        player.addPotionEffect(effect);
        plugin.markAbilityMovementEffect(player, PotionEffectType.SPEED);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t > 10 || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location pl = player.getLocation();
                World w = pl.getWorld();
                if (w == null) {
                    cancel();
                    return;
                }
                w.spawnParticle(Particle.CLOUD, pl, 12, 0.3, 0.3, 0.3, 0.05);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        world.playSound(loc, Sound.ENTITY_PLAYER_SPLASH, 1f, 1.5f);
        player.sendMessage("§b§lWIND DASH! §7Amplifier: §f" + amplifier);
    }

    /** Prism Burst: rainbow dash with short ability cooldown */
    public static void prismBurstDash(DiscSwordPlugin plugin,
                                      Player player,
                                      Map<UUID, Long> prismCooldown) {

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (prismCooldown.containsKey(id) && now - prismCooldown.get(id) < 6000) {
            long remaining = (6000 - (now - prismCooldown.get(id))) / 1000;
            player.sendMessage("§cAbility on cooldown! §7" + remaining + "s remaining.");
            return;
        }
        prismCooldown.put(id, now);

        applyVulcanBypass(player);

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.FIREWORK, loc, 45, 1, 1, 1, 0.05);
        world.spawnParticle(Particle.END_ROD, loc, 60, 0.6, 1, 0.6, 0.01);
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1.4f);

        Vector dash = player.getLocation().getDirection().normalize().multiply(1.8);
        dash.setY(0.2);
        player.setVelocity(safe(dash));

        // Mark as movement ability (keeps anti-cheat compatibility)
        plugin.markAbilityMovementEffect(player, PotionEffectType.SPEED);

        // ✨ Turn invisible for 10 seconds after dash
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                200,   // 10 seconds
                0,
                false,
                false,
                false
        ));

        double radius = 6;
        for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (entity instanceof LivingEntity target && entity != player) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                world.spawnParticle(Particle.GLOW, target.getLocation(), 20, 0.3, 0.4, 0.3, 0.01);
            }
        }

        player.sendMessage("§d§lPRISM BURST! §7You dash forward in a blinding rainbow flash and vanish!");
    }

    /** Teleports behind the nearest mob/player in front of the player */
    public static void teleportToMob(Player player, double range) {
        applyVulcanBypass(player);
        DiscSwordPlugin plugin = DiscSwordPlugin.getInstance();
        if (plugin == null) return;

        World world = player.getWorld();

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity e : world.getNearbyEntities(player.getLocation(), range, range, range)) {
            if (!(e instanceof LivingEntity target)) continue;
            if (target == player) continue;

            Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (direction.dot(toTarget.normalize()) < 0.5) continue;

            double dist = target.getLocation().distance(player.getLocation());
            if (dist < closestDist) {
                closestDist = dist;
                closest = target;
            }
        }

        if (closest == null) {
            player.sendMessage("§cNo target found in front of you!");
            return;
        }

        Location tLoc = closest.getLocation();
        Vector backDir = tLoc.getDirection().normalize().multiply(-1);

        Location teleportPos = tLoc.clone().add(backDir.multiply(1.3));
        teleportPos.setY(world.getHighestBlockYAt(teleportPos) + 1);

        player.teleport(teleportPos);

        world.playSound(teleportPos, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        world.spawnParticle(Particle.PORTAL, teleportPos, 40, 0.4, 1, 0.4, 0.1);

        // Mark as movement ability
        plugin.markAbilityMovementEffect(player, PotionEffectType.SPEED);

        player.sendMessage("§d§lBLINK STRIKE! §7Teleported to your target.");
    }

    /** Sonic beam that damages the first entity hit */
    public static void sonicBoom(DiscSwordPlugin plugin, Player player) {
        applyVulcanBypass(player);
        if (plugin == null) return;

        Location origin = player.getEyeLocation();
        World world = origin.getWorld();
        if (world == null) return;

        world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f);
        world.spawnParticle(Particle.SONIC_BOOM, origin, 1);

        double maxDistance = 30;
        double step = 0.5;

        Vector direction = origin.getDirection().normalize();

        for (double d = 0; d < maxDistance; d += step) {
            Location point = origin.clone().add(direction.clone().multiply(d));
            World pw = point.getWorld();
            if (pw == null) break;

            pw.spawnParticle(Particle.SONIC_BOOM, point, 0);
            pw.spawnParticle(Particle.END_ROD, point, 1);

            if (!point.getBlock().isPassable()) break;

            for (Entity entity : pw.getNearbyEntities(point, 1, 1, 1)) {
                if (entity instanceof LivingEntity target && !target.equals(player)) {
                    target.damage(8.0, player);

                    Vector knock = direction.clone().multiply(2).setY(0.4);
                    target.setVelocity(safe(knock));

                    pw.playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);
                    return;
                }
            }
        }

        player.sendMessage("§b§lSONIC BOOM! §7A shock beam erupts forward.");
    }

    // ----------------------------------------------------------
    // OFFENSIVE ABILITIES
    // ----------------------------------------------------------

    /** Rapid multi-hit flurry + final explosion */
    public static void rapidFlurryStrikes(DiscSwordPlugin plugin, Player player) {
        applyVulcanBypass(player);
        if (plugin == null) return;

        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) return;

        player.sendMessage("§c§lRAPID FLURRY! §7You unleash a cascade of strikes!");

        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        world.spawnParticle(Particle.CRIT, center, 40, 1, 1, 1, 0.2);
        world.spawnParticle(Particle.SWEEP_ATTACK, center, 20, 0.3, 0.2, 0.3, 0.01);

        double radius = 6.0;
        List<LivingEntity> targets = new ArrayList<>();

        for (Entity e : world.getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity le && le != player) {
                targets.add(le);
            }
        }

        if (targets.isEmpty()) return;

        int totalHits = 6;
        long delayBetweenHits = 4L;

        // Multiple quick hits
        for (int i = 0; i < totalHits; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (LivingEntity target : targets) {
                    if (target.isDead()) continue;

                    Location tLoc = target.getLocation().clone().add(
                            (Math.random() - 0.5) * 0.6,
                            0.2,
                            (Math.random() - 0.5) * 0.6
                    );
                    World tw = tLoc.getWorld();
                    if (tw == null) continue;

                    tw.spawnParticle(Particle.CRIT, tLoc, 15, 0.2, 0.2, 0.2, 0.01);
                    tw.spawnParticle(Particle.SWEEP_ATTACK, tLoc, 1);

                    Vector kb = target.getLocation().toVector()
                            .subtract(player.getLocation().toVector())
                            .normalize()
                            .multiply(0.2);
                    kb.setY(0.05);
                    target.setVelocity(safe(kb));

                    target.damage(1.8, player);
                    tw.playSound(tLoc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.2f);
                }

            }, delayBetweenHits * i);
        }

        // Final explosion hit
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (LivingEntity target : targets) {
                if (target.isDead()) continue;

                Location tLoc = target.getLocation();
                World tw = tLoc.getWorld();
                if (tw == null) continue;

                tw.spawnParticle(Particle.EXPLOSION, tLoc, 25, 0.4, 0.4, 0.4, 0.1);
                tw.playSound(tLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);

                Vector launch = target.getLocation().toVector()
                        .subtract(player.getLocation().toVector())
                        .normalize()
                        .multiply(1.1);
                launch.setY(0.6);
                target.setVelocity(safe(launch));

                target.damage(12, player);
            }

        }, totalHits * delayBetweenHits + 6L);
    }

    /** Void rift implosion / explosion ability */
    public static void voidRiftImplosion(
            DiscSwordPlugin plugin,
            Player player,
            Map<UUID, Long> voidRiftCooldown
    ) {
        if (plugin == null) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (voidRiftCooldown.containsKey(id) && now - voidRiftCooldown.get(id) < 30_000L) {
            long remaining = (30_000L - (now - voidRiftCooldown.get(id))) / 1000;
            player.sendMessage("§5§lAbility on cooldown! §7" + remaining + "s remaining.");
            return;
        }
        voidRiftCooldown.put(id, now);

        applyVulcanBypass(player);

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.PORTAL, loc, 150, 1.3, 1.3, 1.3, 0.1);
        world.spawnParticle(Particle.REVERSE_PORTAL, loc, 80, 1.4, 1.4, 1.4, 0.05);
        world.playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 1f, 0.5f);
        world.playSound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.5f);

        player.sendMessage("§5§lVOID RIFT! §dThe shadows begin to collapse inward…");

        // Explosion after delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World w = loc.getWorld();
            if (w == null) return;

            w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
            w.spawnParticle(Particle.DRAGON_BREATH, loc, 120, 1.5, 1.5, 1.5, 0.05);
            w.playSound(loc, Sound.ENTITY_WITHER_BREAK_BLOCK, 1f, 0.7f);

            double radius = 12;
            double damage = 16;

            for (Entity entity : w.getNearbyEntities(loc, radius, radius, radius)) {
                if (entity instanceof LivingEntity target && entity != player) {

                    Vector pull = loc.toVector().subtract(target.getLocation().toVector())
                            .normalize().multiply(1.4);
                    pull.setY(0.4);
                    target.setVelocity(safe(pull));

                    target.damage(damage, player);
                    w.spawnParticle(Particle.SMOKE, target.getLocation(), 20);
                }
            }

            player.sendMessage("§d§lVOID DETONATION! §7The rift collapses with explosive force.");
        }, 20L);
    }

    // ----------------------------------------------------------
    // DEFENSIVE / SUPPORT ABILITIES
    // ----------------------------------------------------------

    /** Creates a protective barrier that pushes enemies + deletes projectiles */
    public static void barrierShield(
            DiscSwordPlugin plugin,
            Player player,
            Set<UUID> activeBarriers
    ) {
        applyVulcanBypass(player);
        if (plugin == null) return;

        World world = player.getWorld();

        activeBarriers.add(player.getUniqueId());
        player.sendMessage("§b§lBARRIER SHIELD! §7A protective invisible dome surrounds you.");

        double radius = 2.5;

        new BukkitRunnable() {
            int ticks = 0;
            final int duration = 60; // 3 seconds

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= duration) {
                    activeBarriers.remove(player.getUniqueId());
                    player.sendMessage("§b§lBARRIER SHIELD §7has faded.");
                    cancel();
                    return;
                }

                Location center = player.getLocation().add(0, 1, 0);
                World w = center.getWorld();
                if (w == null) {
                    cancel();
                    return;
                }

                // Push mobs away
                for (Entity e : w.getNearbyEntities(center, radius, radius, radius)) {
                    if (e instanceof LivingEntity target && target != player) {
                        Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                        push.setY(0.3);
                        target.setVelocity(safe(push.multiply(1.3)));
                    }
                }

                // Destroy incoming projectiles
                for (Entity e : w.getNearbyEntities(center, radius, radius, radius)) {
                    if (e instanceof Projectile proj) {
                        ProjectileSource shooter = proj.getShooter();
                        if (!(shooter instanceof Player) || !shooter.equals(player)) {
                            proj.remove();
                            w.playSound(center, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 0.5f, 1.2f);
                        }
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** FREEZE ability: slows + freezes + applies mining fatigue */
    public static void freezeAbility(
            DiscSwordPlugin plugin,
            Player player,
            double radius,
            int durationTicks,
            Set<UUID> frozenEntities
    ) {
        applyVulcanBypass(player);
        if (plugin == null) return;

        World world = player.getWorld();

        Location center = player.getLocation();

        player.sendMessage("§b§lFREEZE! §7You freeze all nearby enemies.");

        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
        world.spawnParticle(Particle.SNOWFLAKE, center, 80, 1, 1, 1, 0.1);

        for (Entity e : world.getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity target && target != player) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 10));
                target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, durationTicks, 5));
                target.setVelocity(safe(new Vector(0, 0, 0)));

                world.spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 20);

                frozenEntities.add(target.getUniqueId());
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID id : new HashSet<>(frozenEntities)) {
                    Entity ent = Bukkit.getEntity(id);
                    if (ent instanceof LivingEntity living) {
                        living.removePotionEffect(PotionEffectType.SLOWNESS);
                        living.removePotionEffect(PotionEffectType.MINING_FATIGUE);
                    }
                    frozenEntities.remove(id);
                }
            }
        }.runTaskLater(plugin, durationTicks);
    }

    /** Glow burst: reveals all mobs/players nearby */
    public static void areaGlow(Player player, int power) {
        applyVulcanBypass(player);

        World world = player.getWorld();

        Location loc = player.getLocation();

        double radius = 15 + (power * 2);
        int duration = (5 + power) * 20;

        world.spawnParticle(Particle.END_ROD, loc, 80 + (power * 20), 1.5, 1.5, 1.5, 0.01);
        world.playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.0f + (power * 0.1f));

        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof LivingEntity living && living != player) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0, true, false));
            }
        }

        player.sendMessage("§e§lGLOW BURST! §7Power: " + power +
                " §8| §7Radius: " + radius +
                " §8| §7Duration: " + (duration / 20) + "s");
    }

    /** Nether flames: burning aura + fire damage waves */
    public static void netherFlames(DiscSwordPlugin plugin, Player player) {
        applyVulcanBypass(player);
        if (plugin == null) return;

        World world = player.getWorld();

        Location center = player.getLocation();

        player.sendMessage("§6§lNETHER FLAMES! §7Infernal fire surrounds you.");

        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
        world.spawnParticle(Particle.LAVA, center.clone().add(0, 1, 0),
                40, 0.4, 0.4, 0.4, 0.02);

        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 1));

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= 20) {
                    player.sendMessage("§6§lNETHER FLAMES §7have faded.");
                    cancel();
                    return;
                }

                Location loc = player.getLocation().add(0, 1, 0);
                World w = loc.getWorld();
                if (w == null) {
                    cancel();
                    return;
                }

                double radius = 3.5;

                for (double angle = 0; angle < 2 * Math.PI; angle += Math.PI / 8) {
                    Location ring = loc.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    w.spawnParticle(Particle.FLAME, ring, 2);
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, ring, 1);
                }

                for (Entity e : w.getNearbyEntities(loc, radius, 2, radius)) {
                    if (e instanceof LivingEntity target && target != player) {
                        target.setFireTicks(40);
                        target.damage(5, player);

                        Vector kb = target.getLocation().toVector().subtract(player.getLocation().toVector())
                                .normalize().multiply(0.4).setY(0.25);
                        target.setVelocity(safe(kb));

                        w.spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1, 0), 8);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // ----------------------------------------------------------
    // UTILITY ABILITIES
    // ----------------------------------------------------------

    /** Launches another player straight up if in crosshair */
    public static void launchOtherPlayer(Player player) {
        applyVulcanBypass(player);

        World world = player.getWorld();

        Location eye = player.getEyeLocation();
        double maxDistance = 20;

        Player target = null;

        for (Entity e : world.getNearbyEntities(eye, maxDistance, maxDistance, maxDistance)) {
            if (!(e instanceof Player other)) continue;
            if (other == player) continue;

            Vector toOther = other.getLocation().toVector().subtract(eye.toVector());
            if (toOther.length() > maxDistance) continue;

            Vector dir = eye.getDirection().normalize();
            if (dir.dot(toOther.normalize()) > 0.9) target = other;
        }

        if (target == null) {
            player.sendMessage("§cNo player in front of you to launch!");
            return;
        }

        Vector added = target.getVelocity().add(new Vector(0, 1.5, 0));
        target.setVelocity(safe(added));

        Location tLoc = target.getLocation();
        World tw = tLoc.getWorld();
        if (tw != null) {
            tw.playSound(tLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
            tw.spawnParticle(Particle.CLOUD, tLoc.clone().add(0, 1, 0), 40);
        }

        player.sendMessage("§b§lLAUNCH! §7You blasted §f" + target.getName());
        target.sendMessage("§cYou were launched by §f" + player.getName() + "§c!");
    }

    /** Damage immunity for X seconds */
    public static void damageImmunity(
            DiscSwordPlugin plugin,
            Player player,
            Set<UUID> damageImmune,
            int durationSeconds
    ) {
        applyVulcanBypass(player);
        if (plugin == null) return;

        damageImmune.add(player.getUniqueId());

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.2f);
        world.spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0),
                20
        );

        player.sendMessage("§b§lIMMUNE! §7You are protected for " + durationSeconds + " seconds.");

        // Convert seconds → ticks
        int durationTicks = durationSeconds * 20;

        new BukkitRunnable() {
            @Override
            public void run() {
                damageImmune.remove(player.getUniqueId());
                player.sendMessage("§c§lIMMUNITY ENDED§7.");
            }
        }.runTaskLater(plugin, durationTicks);
    }

    /** Lifesteal: steal 3 hearts (6 HP) */
    public static void lifesteal(DiscSwordPlugin plugin, Player player) {
        applyVulcanBypass(player);
        if (plugin == null) return;

        World world = player.getWorld();

        Location center = player.getLocation().add(0, 1, 0);

        double radius = 6;
        double stealAmount = 6;

        LivingEntity target = null;

        for (Entity e : world.getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity le && le != player && !(le instanceof ArmorStand)) {
                target = le;
                break;
            }
        }

        if (target == null) {
            player.sendMessage("§7No target found to drain.");
            return;
        }

        target.damage(stealAmount, player);

        player.setHealth(Math.min(
                Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue(),
                player.getHealth() + stealAmount
        ));

        world.spawnParticle(Particle.HEART, target.getLocation().add(0, 1, 0), 10);
        world.playSound(center, Sound.ENTITY_GENERIC_DRINK, 1f, 0.6f);

        player.playSound(center, Sound.MUSIC_DISC_LAVA_CHICKEN, 1f, 1f);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> player.stopSound(Sound.MUSIC_DISC_LAVA_CHICKEN),
                100);

        player.sendMessage("§aYou drained §c❤❤❤ §afrom your target!");
    }

    // ----------------------------------------------------------
    // SPECIAL & STATUS ABILITIES
    // ----------------------------------------------------------

    /** Status shift: self buffs + enemy debuffs */
    public static void statusShift(Player player) {
        applyVulcanBypass(player);

        int duration = 5 * 30; // 5 seconds (150 ticks)

        Player target = null;
        double range = 12;

        for (Entity e : player.getNearbyEntities(range, range, range)) {
            if (e instanceof Player p && p != player) {
                target = p;
                break;
            }
        }

        if (target == null) {
            player.sendMessage("§cNo opponent nearby!");
            return;
        }

        PotionEffectType[] positive = {
                PotionEffectType.REGENERATION,
                PotionEffectType.STRENGTH,
                PotionEffectType.SPEED,
                PotionEffectType.RESISTANCE,
        };

        PotionEffectType[] negative = {
                PotionEffectType.WEAKNESS,
                PotionEffectType.SLOWNESS,
                PotionEffectType.MINING_FATIGUE,
                PotionEffectType.BLINDNESS,
        };

        for (PotionEffectType type : positive) {
            PotionEffect effect = new PotionEffect(type, duration, 1, true, true, true);
            player.addPotionEffect(effect, true);
        }

        for (PotionEffectType type : negative) {
            PotionEffect effect = new PotionEffect(type, duration, 1, true, true, true);
            target.addPotionEffect(effect, true);
        }

        player.sendMessage("§a§lSTATUS SHIFT! §7You gained powerful buffs!");
        target.sendMessage("§c§lSTATUS SHIFTED! §7You were weakened!");
    }

    /** Water empowered strike */
    public static void waterEmpoweredStrike(Player player, double power) {
        applyVulcanBypass(player);

        World world = player.getWorld();

        boolean inWater = player.getLocation().getBlock().isLiquid();
        boolean underwater = player.getEyeLocation().getBlock().isLiquid();

        double damage = 6 * power;
        double radius = 4 * power;
        int particles = (int) (30 * power);

        if (inWater || underwater) {
            damage *= 2.8;
            radius *= 1.5;
            particles *= 2;
            player.sendMessage("§b§lWATER EMPOWERED! §7Boosted effect!");
        }

        world.spawnParticle(
                Particle.SPLASH,
                player.getLocation(),
                particles,
                1, 1, 1,
                0.1
        );

        for (Entity e : world.getNearbyEntities(player.getLocation(), radius, radius, radius)) {
            if (e instanceof LivingEntity target && target != player) {
                target.damage(damage, player);
            }
        }
    }

    /** Invert breathing (drown on air, breathe underwater) */
    public static void invertBreathing(DiscSwordPlugin plugin, Player player) {
        applyVulcanBypass(player);
        if (plugin == null) return;

        final double radius = 8; // how far the drowning aura reaches

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {

                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                if (ticks >= 200) { // 10 seconds
                    cancel();
                    player.sendMessage("§b§lBREATH SHIFT §7has faded.");
                    return;
                }

                ticks++;

                World world = player.getWorld();

                for (Entity e : world.getNearbyEntities(player.getLocation(), radius, radius, radius)) {
                    if (e instanceof Player target && target != player) {

                        boolean inWater = target.getEyeLocation().getBlock().isLiquid();

                        // If they're in water, replenish air (inverted)
                        if (inWater) {
                            target.setRemainingAir(target.getMaximumAir());
                        } else {
                            int air = target.getRemainingAir();

                            if (air > 0) {
                                target.setRemainingAir(air - 10);
                            } else {
                                target.damage(2.0, player); // attribute to ability owner
                                world.spawnParticle(Particle.BUBBLE_POP, target.getEyeLocation(), 10);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    /** Tears ability: AoE slow + weakness + healing */
    public static void tearsAbility(DiscSwordPlugin plugin, Player player) {
        applyVulcanBypass(player);
        if (plugin == null) return;

        World world = player.getWorld();

        Location center = player.getLocation();

        int durationSec = 5;
        int durationTicks = durationSec * 20;

        player.sendMessage("§b§lTEARS! §7Sorrowful energy is released.");

        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 0.7f);
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);

        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 2));

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || elapsed >= durationTicks) {
                    cancel();
                    return;
                }

                Location loc = player.getLocation().add(0, 1.2, 0);
                World w = loc.getWorld();
                if (w == null) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 8; i++) {
                    w.spawnParticle(
                            Particle.DRIPPING_WATER,
                            loc.clone().add((Math.random() - 0.5) * 2, 1.5, (Math.random() - 0.5) * 2),
                            3
                    );
                    w.spawnParticle(
                            Particle.FALLING_OBSIDIAN_TEAR,
                            loc.clone().add((Math.random() - 0.5) * 2, 1.8, (Math.random() - 0.5) * 2),
                            5
                    );
                }

                double radius = 6;

                for (Entity e : w.getNearbyEntities(center, radius, radius, radius)) {
                    if (e instanceof LivingEntity target && target != player) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 1));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 10, 0));

                        w.spawnParticle(
                                Particle.SPLASH,
                                target.getLocation().add(0, 1, 0),
                                10
                        );
                    }
                }

                elapsed += 2; // runs every 2 ticks
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    /** Sound wave: AoE knockback pulse */
    public static void soundKnockbackPulse(Player player) {
        applyVulcanBypass(player);

        World world = player.getWorld();

        Location center = player.getLocation();

        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1f);
        world.playSound(center, Sound.BLOCK_NOTE_BLOCK_BASS, 1.5f, 0.5f);
        world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0, 1, 0), 1);
        world.spawnParticle(Particle.NOTE, center.clone().add(0, 1, 0), 40);

        double radius = 8;
        double strength = 1.4;

        player.sendMessage("§b§lSOUND WAVE! §7A concussive shock radiates outward.");

        for (Entity e : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity target)) continue;
            if (target == player) continue;

            Vector dir = target.getLocation().toVector().subtract(center.toVector());
            if (dir.lengthSquared() == 0) continue;

            dir.normalize().multiply(strength).setY(0.4);
            target.setVelocity(safe(dir));

            world.spawnParticle(Particle.SONIC_BOOM, target.getLocation().add(0, 1, 0), 1);
        }
    }

    /** Creative flight boost for X seconds */
    public static void creativeFlightBoost(
            DiscSwordPlugin plugin,
            Player player,
            int durationSec
    ) {
        applyVulcanBypass(player);
        if (plugin == null) return;

        int durationTicks = durationSec * 20;

        player.setAllowFlight(true);
        player.setFlying(true);

        player.sendMessage("§b§lFLIGHT BOOST! §7You can fly for " + durationSec + " seconds.");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            player.setFlying(false);
            player.setAllowFlight(false);

            player.sendMessage("§c§lFLIGHT BOOST ENDED§7.");
        }, durationTicks);
    }
}
