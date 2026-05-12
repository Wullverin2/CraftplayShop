package de.craftplay.shop.integrations;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.lang.reflect.Method;

public class CitizensHook implements Listener {
    private final CraftplayShopPlugin plugin;
    private boolean registered;
    private boolean available;

    public CitizensHook(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        available = plugin.getConfig().getBoolean("integrations.citizens.enabled", true)
                && plugin.getServer().getPluginManager().isPluginEnabled("Citizens");
        if (available && !registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpcClick(PlayerInteractEntityEvent event) {
        if (!available) {
            return;
        }
        NpcData npc = npcData(event.getRightClicked());
        if (npc == null) {
            return;
        }
        ConfigurationSection section = npcSection(npc);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        Player player = event.getPlayer();
        String permission = section.getString("permission", "");
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        String action = section.getString("action", section.getString("openGui", "OPEN_GUI:main"));
        if (!action.contains(":") && !action.isBlank()) {
            action = "OPEN_GUI:" + action;
        }
        plugin.getGuiActionExecutor().execute(player, action);
    }

    private ConfigurationSection npcSection(NpcData npc) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("integrations.citizens.npcs");
        if (root == null) {
            return null;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            int id = section.getInt("id", Integer.MIN_VALUE);
            String name = section.getString("name", "");
            if ((id != Integer.MIN_VALUE && id == npc.id())
                    || (!name.isBlank() && name.equalsIgnoreCase(npc.name()))) {
                return section;
            }
        }
        return null;
    }

    private NpcData npcData(Entity entity) {
        try {
            Class<?> apiClass = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = apiClass.getMethod("getNPCRegistry").invoke(null);
            Method getNpc = registry.getClass().getMethod("getNPC", Entity.class);
            Object npc = getNpc.invoke(registry, entity);
            if (npc == null) {
                return null;
            }
            int id = (Integer) npc.getClass().getMethod("getId").invoke(npc);
            String name = String.valueOf(npc.getClass().getMethod("getName").invoke(npc));
            return new NpcData(id, name);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private record NpcData(int id, String name) {
    }
}
