package de.craftplay.shop.integrations;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class MultiServerSyncService {
    private final CraftplayShopPlugin plugin;
    private BukkitTask task;

    public MultiServerSyncService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        stop();
        if (!plugin.getConfig().getBoolean("multiServerSync.enabled", false)) {
            return;
        }
        writeSnapshot();
        long intervalTicks = Math.max(5L, plugin.getConfig().getLong("multiServerSync.intervalSeconds", 30L)) * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::writeSnapshot, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void writeSnapshot() {
        File folder = new File(plugin.getDataFolder(), plugin.getConfig().getString("multiServerSync.folder", "sync"));
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getPluginLogService().warn("Could not create multi-server sync folder: " + folder.getPath());
            return;
        }
        String serverId = plugin.getConfig().getString("multiServerSync.serverId", plugin.getConfig().getString("panel.serverId", "server-1"));
        File file = new File(folder, sanitize(serverId) + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("serverId", serverId);
        yaml.set("pluginVersion", plugin.getDescription().getVersion());
        yaml.set("updatedAt", System.currentTimeMillis());
        yaml.set("onlinePlayers", Bukkit.getOnlinePlayers().size());
        yaml.set("serverShop.categories", plugin.getServerShopRegistry() == null ? 0 : plugin.getServerShopRegistry().categories().size());
        yaml.set("serverShop.items", plugin.getServerShopRegistry() == null ? 0 : plugin.getServerShopRegistry().categories().stream()
                .filter(Objects::nonNull)
                .mapToInt(category -> category.items().size())
                .sum());
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getPluginLogService().error("Could not write multi-server sync snapshot.", exception);
        }
    }

    private String sanitize(String input) {
        return (input == null || input.isBlank() ? "server-1" : input).replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
