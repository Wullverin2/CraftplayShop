package de.craftplay.shop.integrations;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FloodgateHook {
    private final CraftplayShopPlugin plugin;

    public FloodgateHook(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getConfigService().floodgateEnabled()
                && plugin.getServer().getPluginManager().isPluginEnabled("floodgate");
    }

    public boolean isFloodgatePlayer(Player player) {
        if (!isAvailable()) {
            return false;
        }
        try {
            return FloodgateApi.getInstance() != null && FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable throwable) {
            plugin.getPluginLogService().debug("general", "Floodgate player detection failed: " + throwable.getMessage());
            return false;
        }
    }

    public boolean openMainForm(Player player) {
        if (!isFloodgatePlayer(player) || !plugin.getConfigService().floodgateFormsEnabled()) {
            return false;
        }
        YamlConfiguration gui = gui(player);
        if (gui == null) {
            plugin.getPluginLogService().warn("Missing Bedrock GUI file for Floodgate main form.");
            return false;
        }
        List<BedrockButton> buttons = buttons(player, gui.getConfigurationSection("buttons"));
        if (buttons.isEmpty()) {
            return false;
        }
        Map<String, String> placeholders = plugin.getGuiPlaceholderService().placeholders(player);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(apply(gui.getString("title", "&8CraftplayShop"), placeholders))
                .content(apply(gui.getString("content", "&7Waehle einen Bereich."), placeholders));
        for (BedrockButton button : buttons) {
            builder.button(button.text());
        }
        builder.validResultHandler((form, response) -> {
            int clicked = response.getClickedButtonId();
            if (clicked < 0 || clicked >= buttons.size()) {
                return;
            }
            BedrockButton button = buttons.get(clicked);
            plugin.getTaskService().runSync(() -> {
                if (!button.permission().isBlank() && !player.hasPermission(button.permission())) {
                    plugin.getLanguageService().send(player, "general.noPermission");
                    return;
                }
                plugin.getGuiService().open(player, button.guiId());
            });
        });
        try {
            return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder);
        } catch (Throwable throwable) {
            plugin.getPluginLogService().error("Could not send Floodgate form.", throwable);
            return false;
        }
    }

    private List<BedrockButton> buttons(Player player, ConfigurationSection section) {
        List<BedrockButton> result = new ArrayList<>();
        if (section == null) {
            return result;
        }
        Map<String, String> placeholders = plugin.getGuiPlaceholderService().placeholders(player);
        for (String key : section.getKeys(false)) {
            ConfigurationSection button = section.getConfigurationSection(key);
            if (button == null || !button.getBoolean("enabled", true)) {
                continue;
            }
            String guiId = button.getString("openGui", "");
            if (guiId.isBlank()) {
                continue;
            }
            String label = apply(button.getString("label", key), placeholders);
            List<String> lore = button.getStringList("lore").stream().map(line -> apply(line, placeholders)).toList();
            String text = lore.isEmpty() ? label : label + "\n" + String.join("\n", lore);
            result.add(new BedrockButton(guiId, text, button.getString("permission", "")));
        }
        return result;
    }

    private YamlConfiguration gui(Player player) {
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        File exact = new File(plugin.getDataFolder(), "gui/" + language + "/bedrock_main.yml");
        if (exact.exists()) {
            return YamlConfiguration.loadConfiguration(exact);
        }
        File fallback = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/bedrock_main.yml");
        if (fallback.exists()) {
            return YamlConfiguration.loadConfiguration(fallback);
        }
        return null;
    }

    private String apply(String text, Map<String, String> placeholders) {
        return TextUtil.color(PlaceholderUtil.apply(text == null ? "" : text, placeholders));
    }

    private record BedrockButton(String guiId, String text, String permission) {
    }
}
