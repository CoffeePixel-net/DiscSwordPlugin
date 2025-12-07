package me.cheese.discsword.wolf;

import me.cheese.discsword.DiscSwordPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuardianWolfManager implements Listener {

    // ===================================================
    // ENUMS & DATA CLASS
    // ===================================================

    public enum GuardianMode {
        AGGRESSIVE,
        DEFENSIVE
    }

    public static class GuardianWolfData {
        public final UUID ownerId;
        public Wolf wolf;
        public GuardianMode mode;
        public int level;
        public int xp;

        public long lastLeap = 0;
        public long lastDash = 0;
        public long lastBite = 0;
        public long lastHowl = 0;
        public long lastRoar = 0;

        public GuardianWolfData(UUID ownerId, Wolf wolf, GuardianMode mode, int level, int xp) {
            this.ownerId = ownerId;
            this.wolf = wolf;
            this.mode = mode;
            this.level = level;
            this.xp = xp;
        }
    }

    // ===================================================
    // FIELDS
    // ===================================================

    private final DiscSwordPlugin plugin;
    private final Map<UUID, GuardianWolfData> guardianWolves = new HashMap<>();
    private final Map<UUID, Long> playerLastActive = new ConcurrentHashMap<>();
    private final Set<UUID> autoTpEnabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Map<UUID, Long>> wolfThreatMap = new HashMap<>();
    private final Random rng = new Random();

    // ===================================================
    // CONSTRUCTOR
    // ===================================================

    public GuardianWolfManager(DiscSwordPlugin plugin) {
        this.plugin = plugin;
    }

    // ===================================================
    // PUBLIC API
    // ===================================================

    /** Toggle auto-teleport and persist it to players.yml */
    public boolean toggleAutoTeleport(Player player) {
        UUID id = player.getUniqueId();
        boolean enabled;

        if (autoTpEnabled.remove(id)) {
            enabled = false;
        } else {
            autoTpEnabled.add(id);
            enabled = true;
        }

        GuardianWolfData data = guardianWolves.get(id);
        if (data != null) {
            saveWolfData(data);
        } else {
            FileConfiguration cfg = plugin.getPlayersConfig();
            cfg.set("players." + id + ".autoTeleport", enabled);
            plugin.savePlayersConfig();
        }

        return enabled;
    }

    public boolean isAutoTeleportEnabled(Player player) {
        return autoTpEnabled.contains(player.getUniqueId());
    }

    public void handleWolfCommand(Player player, String[] args) {
        String sub = (args.length >= 2) ? args[1].toLowerCase() : "menu";

        switch (sub) {
            case "mode" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /discsword wolf mode <agg|def>");
                    return;
                }

                GuardianWolfData data = guardianWolves.get(player.getUniqueId());
                if (data == null || data.wolf == null || !data.wolf.isValid()) {
                    player.sendMessage("§cYour Guardian Wolf is not active!");
                    return;
                }

                switch (args[2].toLowerCase()) {
                    case "agg", "aggressive" -> data.mode = GuardianMode.AGGRESSIVE;
                    case "def", "defensive" -> data.mode = GuardianMode.DEFENSIVE;
                    default -> {
                        player.sendMessage("§cUsage: /discsword wolf mode <agg|def>");
                        return;
                    }
                }

                saveWolfData(data);
                updateGuardianNameTag(player, data);
                player.sendMessage("§aWolf mode → " + data.mode);
            }

            case "recall" -> recallWolf(player);

            case "stats" -> sendStats(player);

            default -> openWolfMenu(player);
        }
    }

    // ===================================================
    // SUMMON / RECALL / STATS
    // ===================================================

    public void summonGuardian(Player player) {
        UUID id = player.getUniqueId();
        GuardianWolfData data = guardianWolves.get(id);

        if (data != null && data.wolf != null && data.wolf.isValid()) {
            player.sendMessage("§cYour Guardian Wolf is already summoned!");
            return;
        }

        if (data == null) {
            data = loadWolfData(id);
        }

        if (data == null) {
            data = new GuardianWolfData(id, null, GuardianMode.AGGRESSIVE, 1, 0);
        }

        spawnGuardianWolf(player, data.level, data.xp, data.mode, false);

        GuardianWolfData current = guardianWolves.get(id);
        if (current != null) {
            saveWolfData(current);
        }

        player.sendMessage("§b§lGUARDIAN SUMMONED! §7Progress intact.");
    }

    private void recallWolf(Player player) {
        GuardianWolfData data = guardianWolves.get(player.getUniqueId());
        if (data == null || data.wolf == null) {
            player.sendMessage("§cNo wolf to recall.");
            return;
        }

        if (!data.wolf.isDead()) {
            data.wolf.remove();
        }
        data.wolf = null;
        setWolfActive(player.getUniqueId(), false);
        saveWolfData(data);
        player.sendMessage("§cWolf recalled (progress saved).");
    }

    private void sendStats(Player player) {
        GuardianWolfData data = guardianWolves.get(player.getUniqueId());
        if (data == null) {
            player.sendMessage("§cYour Guardian Wolf has not been summoned yet.");
            return;
        }

        double hp = 0;
        double dmg = 0;
        double armor = 0;

        if (data.wolf != null && data.wolf.isValid()) {
            hp = Objects.requireNonNull(data.wolf.getAttribute(Attribute.MAX_HEALTH)).getValue();
            dmg = Objects.requireNonNull(data.wolf.getAttribute(Attribute.ATTACK_DAMAGE)).getValue();
            armor = Objects.requireNonNull(data.wolf.getAttribute(Attribute.ARMOR)).getValue();
        }

        player.sendMessage("§b§lGUARDIAN STATS");
        player.sendMessage("§7Level: §e" + data.level);
        player.sendMessage("§7XP: §e" + data.xp);
        player.sendMessage("§7Mode: §e" + data.mode);
        player.sendMessage("§7Max HP: §e" + (int) hp);
        player.sendMessage("§7Damage: §e" + (int) dmg);
        player.sendMessage("§7Armor: §e" + (int) armor);
    }

    // ===================================================
    // WOLF CREATION / BASE STATS
    // ===================================================

    private void spawnGuardianWolf(Player player, int level, int xp, GuardianMode mode, boolean respawned) {
        Location loc = player.getLocation()
                .add(player.getLocation().getDirection().multiply(2))
                .add(0, 1, 0);

        player.getWorld().spawnParticle(Particle.HEART, loc, 20);
        player.getWorld().playSound(loc, Sound.ENTITY_WOLF_AMBIENT, 1, 1);

        Wolf wolf = (Wolf) player.getWorld().spawnEntity(loc, EntityType.WOLF);
        wolf.setOwner(player);
        wolf.setAdult();
        wolf.setCustomNameVisible(true);

        GuardianWolfData data = new GuardianWolfData(player.getUniqueId(), wolf, mode, level, xp);
        guardianWolves.put(player.getUniqueId(), data);

        applyGuardianStats(data);
        applyBaseEffects(wolf);
        updateGuardianNameTag(player, data);
        startGuardianAI(player, data);
        setWolfActive(player.getUniqueId(), true);

        if (respawned) {
            player.sendMessage("§bYour Guardian Wolf has returned!");
        }
    }

    /**
     * Vanilla+ balance (Option 2):
     * - HP: starts 25, max 60
     * - Damage: starts 4, max 8
     * - Armor: up to 8
     * - Speed: slightly above vanilla
     */
    private void applyGuardianStats(GuardianWolfData data) {
        if (data.wolf == null) return;

        int L = data.level;
        Wolf w = data.wolf;

        // HEALTH: 25 base, +1.75 per level, capped at 60
        double maxHp = Math.min(25 + (L * 1.75), 60);

        // DAMAGE: 4 base, +0.2 per level, capped at 8
        double dmg = Math.min(4.0 + (L * 0.2), 8.0);

        // ARMOR: +0.4 per level, capped at 8
        double armor = Math.min(L * 0.4, 8.0);

        // SPEED: slightly above normal wolf
        double speed = 0.25;

        Objects.requireNonNull(w.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(maxHp);
        w.setHealth(maxHp);

        Objects.requireNonNull(w.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(dmg);
        Objects.requireNonNull(w.getAttribute(Attribute.ARMOR)).setBaseValue(armor);
        Objects.requireNonNull(w.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(speed);
    }

    private void applyBaseEffects(Wolf wolf) {
        // Small permanent Speed I, no Resistance II to stay fair
        wolf.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false));
    }

    // ===================================================
    // NAME TAG
    // ===================================================

    private void updateGuardianNameTag(Player owner, GuardianWolfData data) {
        if (data.wolf == null || !data.wolf.isValid()) return;

        Wolf wolf = data.wolf;

        // Make 100% sure the name is always visible
        wolf.setCustomNameVisible(true);

        int hp = (int) wolf.getHealth();
        int maxHp = (int) Objects.requireNonNull(
                wolf.getAttribute(Attribute.MAX_HEALTH)
        ).getValue();

        String mode = (data.mode == GuardianMode.AGGRESSIVE) ? "§cAGG" : "§aDEF";

        wolf.setCustomName(
                "§bGuardian Wolf §7[L" + data.level + "] " + mode +
                        " §f[§a" + hp + "§f/§a" + maxHp + "§f]"
        );
    }

    // ===================================================
    // AI LOOP
    // ===================================================

    private void startGuardianAI(Player owner, GuardianWolfData data) {
        Wolf wolf = data.wolf;
        UUID ownerId = owner.getUniqueId();
        playerLastActive.put(ownerId, System.currentTimeMillis());

        new BukkitRunnable() {
            @Override
            public void run() {
                if (wolf == null || wolf.isDead() || !wolf.isValid()) {
                    guardianWolves.remove(ownerId);
                    setWolfActive(ownerId, false);
                    cancel();
                    return;
                }

                Player p = Bukkit.getPlayer(ownerId);
                if (p == null || !p.isOnline()) {
                    wolf.remove();
                    data.wolf = null;
                    setWolfActive(ownerId, false);
                    cancel();
                    return;
                }

                // AFK check (2 minutes)
                long last = playerLastActive.getOrDefault(ownerId, System.currentTimeMillis());
                if (System.currentTimeMillis() - last > 120_000) {
                    p.sendMessage("§eYour wolf returned to storage because you went AFK.");
                    wolf.remove();
                    data.wolf = null;
                    setWolfActive(ownerId, false);
                    saveWolfData(data);
                    cancel();
                    return;
                }

                updateGuardianNameTag(p, data);

                // Auto-teleport if too far
                if (isAutoTeleportEnabled(p) && wolf.getLocation().distance(p.getLocation()) > 18) {
                    Location tp = p.getLocation().add(1, 0, 1);
                    wolf.teleport(tp);
                    wolf.getWorld().spawnParticle(Particle.CLOUD, tp, 15, 0.4, 0.4, 0.4, 0.05);
                    wolf.getWorld().playSound(tp, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                }

                // Targeting & abilities
                LivingEntity target = findTarget(p, wolf);
                if (target != null) {
                    wolf.setTarget(target);
                    runAbilities(p, data, target);
                } else {
                    wolf.setTarget(null);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ===================================================
    // SAVE / LOAD
    // ===================================================

    public void saveWolfData(GuardianWolfData data) {
        FileConfiguration cfg = plugin.getPlayersConfig();
        String basePath = "players." + data.ownerId + ".";

        cfg.set(basePath + "level", data.level);
        cfg.set(basePath + "xp", data.xp);
        cfg.set(basePath + "mode", data.mode.name());

        Player owner = Bukkit.getPlayer(data.ownerId);
        boolean autoTp = owner != null && isAutoTeleportEnabled(owner);
        cfg.set(basePath + "autoTeleport", autoTp);

        plugin.savePlayersConfig();
    }

    private void setWolfActive(UUID ownerId, boolean active) {
        FileConfiguration cfg = plugin.getPlayersConfig();
        cfg.set("players." + ownerId + ".wolfActive", active);
        plugin.savePlayersConfig();
    }

    public GuardianWolfData loadWolfData(UUID uuid) {
        FileConfiguration cfg = plugin.getPlayersConfig();
        String basePath = "players." + uuid + ".";

        if (!cfg.contains(basePath + "level")) {
            return null;
        }

        int level = cfg.getInt(basePath + "level", 1);
        int xp = cfg.getInt(basePath + "xp", 0);
        GuardianMode mode = GuardianMode.valueOf(cfg.getString(basePath + "mode", "AGGRESSIVE"));
        boolean autoTp = cfg.getBoolean(basePath + "autoTeleport", true);

        Player owner = Bukkit.getPlayer(uuid);
        if (owner != null && autoTp) {
            autoTpEnabled.add(uuid);
        }

        return new GuardianWolfData(uuid, null, mode, level, xp);
    }

    public void saveAll() {
        for (GuardianWolfData d : guardianWolves.values()) {
            saveWolfData(d);
        }
    }

    // ===================================================
    // TARGETING & THREAT SYSTEM
    // ===================================================

    /** Wolf only targets mobs or entities that recently attacked the owner/wolf, or nearby monsters. */
    private LivingEntity findTarget(Player owner, Wolf wolf) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity e : wolf.getNearbyEntities(16, 16, 16)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le.equals(owner) || le.equals(wolf)) continue;

            // Threats first (recent attackers)
            if (isThreat(owner.getUniqueId(), le.getUniqueId())) {
                double d = le.getLocation().distanceSquared(wolf.getLocation());
                if (d < bestDist) {
                    best = le;
                    bestDist = d;
                }
                continue;
            }

            // Then generic monsters
            if (le instanceof Monster) {
                double d = le.getLocation().distanceSquared(wolf.getLocation());
                if (d < bestDist) {
                    best = le;
                    bestDist = d;
                }
            }
        }

        return best;
    }

    private void addThreat(UUID ownerId, UUID attacker) {
        wolfThreatMap.putIfAbsent(ownerId, new HashMap<>());
        wolfThreatMap.get(ownerId).put(attacker, System.currentTimeMillis());
    }

    private boolean isThreat(UUID ownerId, UUID target) {
        Long t = wolfThreatMap
                .getOrDefault(ownerId, Collections.emptyMap())
                .get(target);

        return t != null && System.currentTimeMillis() - t < 10_000;
    }

    // ===================================================
    // GUARDIAN ABILITIES (BALANCED COOLDOWNS)
    // ===================================================

    private void runAbilities(Player owner, GuardianWolfData data, LivingEntity target) {
        long now = System.currentTimeMillis();
        Wolf wolf = data.wolf;
        if (wolf == null) return;

        double distSq = wolf.getLocation().distanceSquared(target.getLocation());

        // Leap: gap closer, every 20s, medium range
        if (now - data.lastLeap > 20_000 && distSq > 9 && distSq < 80) {
            data.lastLeap = now;
            leapAttack(wolf, target);
        }

        // Dash: close target reposition, every 25s
        if (now - data.lastDash > 25_000 && distSq <= 9) {
            data.lastDash = now;
            dashAttack(wolf, target);
        }

        // Bite: small bonus hit, every 15s
        if (now - data.lastBite > 15_000 && distSq <= 4) {
            data.lastBite = now;
            biteAttack(wolf, target);
        }

        // Howl: small buff every 45s
        if (now - data.lastHowl > 45_000) {
            data.lastHowl = now;
            howl(owner, data);
        }

        // Roar/Taunt: every 60s
        if (now - data.lastRoar > 60_000) {
            data.lastRoar = now;
            roarTaunt(wolf);
        }
    }

    private void leapAttack(Wolf wolf, LivingEntity target) {
        Vector dir = target.getLocation().toVector()
                .subtract(wolf.getLocation().toVector())
                .normalize();

        // tiny damage + gentle leap
        target.damage(0.5, wolf); // quarter heart
        Vector vel = dir.multiply(0.7);
        vel.setY(0.4);
        wolf.setVelocity(vel);

        wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_GROWL, 0.7f, 1.0f);
    }

    private void dashAttack(Wolf wolf, LivingEntity target) {
        Vector dir = target.getLocation().toVector()
                .subtract(wolf.getLocation().toVector())
                .normalize();

        target.damage(0.5, wolf);
        Vector vel = dir.multiply(0.5);
        vel.setY(0.2);
        wolf.setVelocity(vel);

        wolf.getWorld().spawnParticle(Particle.CLOUD, wolf.getLocation(), 8, 0.2, 0.2, 0.2, 0.01);
    }

    private void biteAttack(Wolf wolf, LivingEntity target) {
        target.damage(1.0, wolf); // half heart
        wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_BIG_GROWL, 0.8f, 1.2f);
    }

    private void howl(Player owner, GuardianWolfData data) {
        if (data.wolf == null) return;

        Wolf wolf = data.wolf;
        wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_BIG_AMBIENT, 1.0f, 1.0f);
        wolf.getWorld().spawnParticle(Particle.SONIC_BOOM, wolf.getLocation().add(0, 1, 0), 1, 0.3, 0.3, 0.3, 0.0);

        // Small Strength I buff for owner and wolf (6 seconds)
        if (owner != null && owner.isOnline()) {
            owner.addPotionEffect(new PotionEffect(
                    PotionEffectType.STRENGTH, 20 * 6, 0, false, true
            ));
            owner.sendMessage("§bYour Guardian Wolf lets out a powerful howl! §7(Strength I for 6s)");
        }

        wolf.addPotionEffect(new PotionEffect(
                PotionEffectType.STRENGTH, 20 * 6, 0, false, false
        ));
    }

    private void roarTaunt(Wolf wolf) {
        // Taunt nearby hostile mobs in an 8-block radius
        for (Entity e : wolf.getNearbyEntities(8, 8, 8)) {
            if (e instanceof Monster mob) {
                mob.setTarget(wolf);
            }
        }
        wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.6f, 0.8f);
    }

    // ===================================================
    // XP / LEVELING (BALANCED)
    // ===================================================

    private GuardianWolfData getWolfCredit(LivingEntity dead) {
        EntityDamageEvent cause = dead.getLastDamageCause();
        if (!(cause instanceof EntityDamageByEntityEvent hit)) {
            return null;
        }

        Entity damager = hit.getDamager();

        if (damager instanceof Projectile proj && proj.getShooter() instanceof Wolf w1) {
            return guardianWolves.values().stream()
                    .filter(d -> d.wolf != null && d.wolf.equals(w1))
                    .findFirst()
                    .orElse(null);
        }

        if (damager instanceof Wolf w2) {
            return guardianWolves.values().stream()
                    .filter(d -> d.wolf != null && d.wolf.equals(w2))
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    /**
     * XP rewards: small, vanilla-friendly.
     * Most monsters: 2–4 XP
     * Bosses: more, but still reasonable.
     */
    private int xpReward(LivingEntity dead, GuardianWolfData data) {
        // Players
        if (dead instanceof Player) return 10;

        // Boss-style mobs (using fqcn to avoid imports if desired)
        if (dead instanceof org.bukkit.entity.Warden) return 40;
        if (dead instanceof org.bukkit.entity.EnderDragon) return 50;
        if (dead instanceof org.bukkit.entity.Wither) return 30;

        // General monsters
        if (dead instanceof Monster) {
            if (dead instanceof org.bukkit.entity.Creeper) return 4;
            if (dead instanceof org.bukkit.entity.Enderman) return 5;
            if (dead instanceof org.bukkit.entity.Hoglin) return 5;
            if (dead instanceof org.bukkit.entity.PiglinBrute) return 12;
            if (dead instanceof org.bukkit.entity.Ravager) return 10;
            if (dead instanceof org.bukkit.entity.Blaze) return 4;
            if (dead instanceof org.bukkit.entity.Pillager) return 3;
            if (dead instanceof org.bukkit.entity.Evoker) return 6;
            if (dead instanceof org.bukkit.entity.Vindicator) return 5;

            // Default monster XP
            return 3;
        }

        // Passive mobs – minimal XP so it's not abusable

        // Fallback
        return 1;
    }

    // Slower XP curve: wolf levels, but not insanely fast.
    private int xpNeeded(int level) {
        return 100 + (level * 50);
    }

    private void addXp(GuardianWolfData data, int amount) {
        Player owner = Bukkit.getPlayer(data.ownerId);

        data.xp += amount;
        int needed = xpNeeded(data.level);

        // Level up
        while (data.xp >= needed) {
            data.xp -= needed;
            data.level++;

            // Stats are capped inside applyGuardianStats, so high levels don't break balance
            applyGuardianStats(data);
            needed = xpNeeded(data.level);

            if (owner != null) {
                owner.sendMessage("§bYour Guardian Wolf reached §eLevel " + data.level + "§b!");
            }
        }

        if (owner != null) {
            owner.sendMessage("§7Wolf gained §a" + amount + " XP §7(" +
                    data.xp + "/" + needed + ")");
        }

        saveWolfData(data);
    }

    // Combined death handler:
    // - Owner dies → reset wolf stats, no respawn
    // - Wolf dies → reset stats
    // - Other mobs killed by wolf → give XP
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();

        // OWNER DIES → RESET WOLF STATS
        if (dead instanceof Player player) {
            GuardianWolfData data = guardianWolves.get(player.getUniqueId());
            if (data != null) {
                data.wolf = null;
                data.level = 1;
                data.xp = 0;
                data.mode = GuardianMode.AGGRESSIVE;
                setWolfActive(player.getUniqueId(), false);
                saveWolfData(data);

                player.sendMessage("§c§lYou died! §7Your Guardian Wolf’s stats have been reset.");
            }
            return;
        }

        // WOLF DIES → RESET STATS
        if (dead instanceof Wolf deadWolf) {
            GuardianWolfData data = guardianWolves.values().stream()
                    .filter(d -> d.wolf != null && d.wolf.getUniqueId().equals(deadWolf.getUniqueId()))
                    .findFirst()
                    .orElse(null);

            if (data != null) {
                data.wolf = null;
                data.level = 1;
                data.xp = 0;
                data.mode = GuardianMode.AGGRESSIVE;
                setWolfActive(data.ownerId, false);
                saveWolfData(data);

                Player owner = Bukkit.getPlayer(data.ownerId);
                if (owner != null) {
                    owner.sendMessage("§c§lYour Guardian Wolf has died! §7Stats have been reset.");
                }
            }
            return;
        }

        // NORMAL MOB DEATH → XP FOR GUARDIAN WOLF
        GuardianWolfData credit = getWolfCredit(dead);
        if (credit == null) return;

        int amount = xpReward(dead, credit);
        addXp(credit, amount);
    }

    // ===================================================
    // EVENTS: THREATS & ACTIVITY
    // ===================================================

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }

        // If player is hit, mark attacker as threat for that player's wolf
        if (victim instanceof Player p) {
            addThreat(p.getUniqueId(), damager.getUniqueId());
        }

        // If wolf is hit, mark attacker as threat for that wolf's owner
        if (victim instanceof Wolf w) {
            Entity finalDamager = damager;
            guardianWolves.values().stream()
                    .filter(d -> d.wolf != null && d.wolf.equals(w))
                    .findFirst()
                    .ifPresent(d -> addThreat(d.ownerId, finalDamager.getUniqueId()));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        playerLastActive.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        playerLastActive.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            playerLastActive.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    // ===================================================
    // GUI
    // ===================================================

    private void openWolfMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§9Guardian Wolf Menu");

        inv.setItem(11, makeItem(Material.WOLF_SPAWN_EGG, "§eChange Mode"));
        inv.setItem(13, makeItem(Material.LEAD, "§cRecall Wolf"));
        inv.setItem(15, makeItem(Material.BOOK, "§bView Stats"));

        player.openInventory(inv);
    }

    private ItemStack makeItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onGUI(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!"§9Guardian Wolf Menu".equals(event.getView().getTitle())) return;

        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        GuardianWolfData data = guardianWolves.get(player.getUniqueId());
        String name = Objects.requireNonNull(item.getItemMeta()).getDisplayName();

        if (name.contains("Change Mode")) {
            if (data == null || data.wolf == null || !data.wolf.isValid()) {
                player.sendMessage("§cWolf not active.");
                return;
            }

            data.mode = (data.mode == GuardianMode.AGGRESSIVE)
                    ? GuardianMode.DEFENSIVE
                    : GuardianMode.AGGRESSIVE;

            saveWolfData(data);
            updateGuardianNameTag(player, data);
            player.sendMessage("§aWolf mode → " + data.mode);
        } else if (name.contains("Recall")) {
            recallWolf(player);
        } else if (name.contains("Stats")) {
            player.performCommand("discsword wolf stats");
        }
    }

    // ===================================================
    // JOIN / QUIT
    // ===================================================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        GuardianWolfData data = guardianWolves.get(id);

        if (data == null) return;

        boolean wasActive = data.wolf != null && data.wolf.isValid();
        setWolfActive(id, wasActive);

        if (data.wolf != null) {
            if (!data.wolf.isDead()) {
                data.wolf.remove();
            }
            data.wolf = null;
        }

        saveWolfData(data);
        // Keep data in guardianWolves so join can decide about respawn
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();

        GuardianWolfData loaded = loadWolfData(id);
        if (loaded == null) return;

        guardianWolves.put(id, loaded);

        FileConfiguration cfg = plugin.getPlayersConfig();
        boolean wolfActive = cfg.getBoolean("players." + id + ".wolfActive", false);

        if (!wolfActive) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline()) return;

                GuardianWolfData data = guardianWolves.get(id);
                if (data == null) return;

                spawnGuardianWolf(p, data.level, data.xp, data.mode, true);
            }
        }.runTaskLater(plugin, 40L);
    }
}
