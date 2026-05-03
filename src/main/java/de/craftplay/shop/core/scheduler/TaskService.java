package de.craftplay.shop.core.scheduler;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class TaskService {
    private final CraftplayShopPlugin plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public TaskService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void runAsync(Runnable runnable) {
        BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        tasks.add(task);
    }

    public void runSync(Runnable runnable) {
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, runnable);
        tasks.add(task);
    }

    public void cancelAll() {
        for (BukkitTask task : new ArrayList<>(tasks)) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        tasks.clear();
    }
}
