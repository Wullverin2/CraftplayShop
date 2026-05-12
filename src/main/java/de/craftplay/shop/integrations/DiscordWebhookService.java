package de.craftplay.shop.integrations;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.transaction.TransactionType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public class DiscordWebhookService {
    private final CraftplayShopPlugin plugin;
    private boolean enabled;
    private String url;
    private Set<TransactionType> transactionTypes = EnumSet.noneOf(TransactionType.class);

    public DiscordWebhookService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        enabled = plugin.getConfig().getBoolean("integrations.discord.enabled", false);
        url = plugin.getConfig().getString("integrations.discord.webhookUrl", "");
        transactionTypes = EnumSet.noneOf(TransactionType.class);
        for (String value : plugin.getConfig().getStringList("integrations.discord.transactionTypes")) {
            try {
                transactionTypes.add(TransactionType.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (transactionTypes.isEmpty()) {
            transactionTypes = EnumSet.allOf(TransactionType.class);
        }
    }

    public void sendTransaction(TransactionType type, Player player, String source, ItemStack itemStack, int amount, double priceEach, double totalPrice) {
        if (!enabled || url == null || url.isBlank() || !transactionTypes.contains(type)) {
            return;
        }
        String material = itemStack == null ? "-" : itemStack.getType().name();
        String template = plugin.getConfig().getString("integrations.discord.transactionMessage",
                "**%type%** `%player%` `%amount%x %material%` total `%total%` source `%source%`");
        String content = template
                .replace("%type%", type.name())
                .replace("%player%", player == null ? "-" : player.getName())
                .replace("%source%", source == null ? "-" : source)
                .replace("%material%", material)
                .replace("%amount%", Integer.toString(amount))
                .replace("%price_each%", plugin.getEconomyService().format(priceEach))
                .replace("%total%", plugin.getEconomyService().format(totalPrice));
        plugin.getTaskService().runAsync(() -> post(content));
    }

    private void post(String content) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] payload = ("{\"content\":\"" + escape(content) + "\"}").getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(payload);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                plugin.getPluginLogService().debug("general", "Discord webhook returned HTTP " + code + ".");
            }
            connection.disconnect();
        } catch (Exception exception) {
            plugin.getPluginLogService().debug("general", "Discord webhook failed: " + exception.getMessage());
        }
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
