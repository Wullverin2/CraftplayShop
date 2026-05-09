package de.craftplay.shop.autosell;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.util.TextUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AutoSellChestDisplayService {
    private static final String TAG_PREFIX = "craftplayshop_autosell_display_";

    private final CraftplayShopPlugin plugin;
    private final AutoSellChestRegistry registry;
    private final AutoSellChestUpgradeService upgradeService;
    private final AutoSellChestProcessor processor;
    private final Map<Long, UUID> displays = new HashMap<>();
    private BukkitTask refreshTask;

    public AutoSellChestDisplayService(CraftplayShopPlugin plugin,
                                       AutoSellChestRegistry registry,
                                       AutoSellChestUpgradeService upgradeService,
                                       AutoSellChestProcessor processor) {
        this.plugin = plugin;
        this.registry = registry;
        this.upgradeService = upgradeService;
        this.processor = processor;
    }

    public void start() {
        stop();
        if (!enabled()) {
            return;
        }
        cleanupWorldDisplays();
        refreshAll();
        long interval = Math.max(10L, plugin.getConfig().getLong("autoSellChest.display.refreshIntervalTicks", 40L));
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, interval, interval);
    }

    public void stop() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            refreshTask.cancel();
        }
        refreshTask = null;
        removeAll();
    }

    public void refreshAll() {
        if (!enabled()) {
            removeAll();
            return;
        }
        for (AutoSellChest chest : registry.all()) {
            refresh(chest);
        }
        displays.entrySet().removeIf(entry -> registry.find(entry.getKey()) == null || removeIfInvalid(entry.getValue()));
    }

    public void remove(AutoSellChest chest) {
        UUID uuid = displays.remove(chest.id());
        if (uuid == null) {
            return;
        }
        Entity entity = findEntity(uuid);
        if (entity != null) {
            entity.remove();
        }
    }

    public void removeAll() {
        for (UUID uuid : displays.values()) {
            Entity entity = findEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        displays.clear();
        cleanupWorldDisplays();
    }

    private void refresh(AutoSellChest chest) {
        Location base = chest.location();
        if (base == null || base.getWorld() == null || !base.getWorld().isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) {
            return;
        }
        TextDisplay display = display(chest);
        if (display == null || display.isDead()) {
            display = spawn(chest, base);
        }
        if (display == null) {
            return;
        }
        Location target = displayLocation(base);
        if (display.getWorld().equals(target.getWorld()) && display.getLocation().distanceSquared(target) > 0.05D) {
            display.teleport(target);
        }
        display.text(LegacyComponentSerializer.legacySection().deserialize(TextUtil.color(String.join("\n", lines(chest)))));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(plugin.getConfig().getBoolean("autoSellChest.display.seeThrough", false));
        display.setShadowed(plugin.getConfig().getBoolean("autoSellChest.display.shadow", true));
    }

    private TextDisplay spawn(AutoSellChest chest, Location base) {
        TextDisplay display = (TextDisplay) base.getWorld().spawnEntity(displayLocation(base), EntityType.TEXT_DISPLAY);
        display.addScoreboardTag(TAG_PREFIX + chest.id());
        displays.put(chest.id(), display.getUniqueId());
        return display;
    }

    private TextDisplay display(AutoSellChest chest) {
        UUID uuid = displays.get(chest.id());
        Entity entity = uuid == null ? null : findEntity(uuid);
        if (entity instanceof TextDisplay textDisplay && !textDisplay.isDead()) {
            return textDisplay;
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entityInWorld : world.getEntitiesByClass(TextDisplay.class)) {
                if (entityInWorld.getScoreboardTags().contains(TAG_PREFIX + chest.id())) {
                    displays.put(chest.id(), entityInWorld.getUniqueId());
                    return (TextDisplay) entityInWorld;
                }
            }
        }
        return null;
    }

    private Entity findEntity(UUID uuid) {
        for (World world : plugin.getServer().getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private boolean removeIfInvalid(UUID uuid) {
        Entity entity = findEntity(uuid);
        if (entity == null || entity.isDead()) {
            return true;
        }
        return false;
    }

    private void cleanupWorldDisplays() {
        for (World world : plugin.getServer().getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getScoreboardTags().stream().anyMatch(tag -> tag.startsWith(TAG_PREFIX))) {
                    display.remove();
                }
            }
        }
    }

    private Location displayLocation(Location base) {
        double offsetX = plugin.getConfig().getDouble("autoSellChest.display.offset.x", 0.5D);
        double offsetY = plugin.getConfig().getDouble("autoSellChest.display.offset.y", 1.45D);
        double offsetZ = plugin.getConfig().getDouble("autoSellChest.display.offset.z", 0.5D);
        return base.clone().add(offsetX, offsetY, offsetZ);
    }

    private java.util.List<String> lines(AutoSellChest chest) {
        Map<String, String> placeholders = placeholders(chest);
        java.util.List<String> configured = plugin.getConfig().getStringList("autoSellChest.display.lines");
        if (configured.isEmpty()) {
            configured = java.util.List.of("&c%name%", "&7Status: %status%", "&7Nächster Verkauf: &f%next_sell%");
        }
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String line : configured) {
            String updated = line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                updated = updated.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            result.add(updated);
        }
        return result;
    }

    private Map<String, String> placeholders(AutoSellChest chest) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", Long.toString(chest.id()));
        placeholders.put("name", chest.name());
        placeholders.put("owner", chest.ownerName());
        placeholders.put("owner_uuid", chest.ownerUuid().toString());
        placeholders.put("status", chest.active() ? "&aaktiv" : "&cinaktiv");
        placeholders.put("world", chest.world());
        placeholders.put("x", Integer.toString(chest.x()));
        placeholders.put("y", Integer.toString(chest.y()));
        placeholders.put("z", Integer.toString(chest.z()));
        placeholders.put("location", chest.world() + " " + chest.x() + " " + chest.y() + " " + chest.z());
        placeholders.put("interval", Long.toString(upgradeService.intervalSeconds(chest)));
        placeholders.put("next_sell", nextSell(chest));
        placeholders.put("next_sell_seconds", Long.toString(Math.max(0L, processor.secondsUntilNextSale(chest))));
        placeholders.put("multiplier", Double.toString(upgradeService.multiplier(chest)));
        placeholders.put("total_items", Long.toString(chest.totalItemsSold()));
        placeholders.put("total_money", plugin.getEconomyService().format(chest.totalMoneyEarned()));
        placeholders.put("debug", processor.lastDebugReason(chest));
        placeholders.put("notify_status", chest.notifyOwner() ? "&aaktiv" : "&cinaktiv");
        placeholders.put("notify_processor_status", processor.ownerNotifyStatus(chest));
        placeholders.put("owner_online", org.bukkit.Bukkit.getPlayer(chest.ownerUuid()) != null ? "&aonline" : "&coffline");
        return placeholders;
    }

    private String nextSell(AutoSellChest chest) {
        long seconds = processor.secondsUntilNextSale(chest);
        if (seconds < 0L) {
            return "inaktiv";
        }
        if (seconds <= 0L) {
            return "jetzt";
        }
        long minutes = seconds / 60L;
        long restSeconds = seconds % 60L;
        if (minutes <= 0L) {
            return seconds + "s";
        }
        return minutes + "m " + restSeconds + "s";
    }

    private boolean enabled() {
        return plugin.getConfigService().autoSellChestEnabled()
                && plugin.getConfig().getBoolean("autoSellChest.display.enabled", true);
    }
}
