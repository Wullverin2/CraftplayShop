package de.craftplay.shop;

import de.craftplay.shop.auctionhouse.AuctionHouseService;
import de.craftplay.shop.autosell.AutoSellChestService;
import de.craftplay.shop.core.command.MainCommand;
import de.craftplay.shop.core.command.ShopCommand;
import de.craftplay.shop.core.command.TradeCommand;
import de.craftplay.shop.core.config.ConfigService;
import de.craftplay.shop.core.database.DatabaseService;
import de.craftplay.shop.core.database.MySqlDatabaseService;
import de.craftplay.shop.core.database.SQLiteDatabaseService;
import de.craftplay.shop.core.database.TableCreator;
import de.craftplay.shop.core.economy.EconomyService;
import de.craftplay.shop.core.economy.VaultEconomyService;
import de.craftplay.shop.core.gui.GuiActionExecutor;
import de.craftplay.shop.core.gui.GuiListener;
import de.craftplay.shop.core.gui.GuiPlaceholderService;
import de.craftplay.shop.core.gui.GuiService;
import de.craftplay.shop.core.item.ItemMatcher;
import de.craftplay.shop.core.item.ItemSerializer;
import de.craftplay.shop.core.language.LanguageService;
import de.craftplay.shop.core.language.PlayerLanguageService;
import de.craftplay.shop.core.logging.PluginLogService;
import de.craftplay.shop.core.player.PlayerSettingsService;
import de.craftplay.shop.core.scheduler.TaskService;
import de.craftplay.shop.core.transaction.TransactionRollbackService;
import de.craftplay.shop.core.transaction.TransactionService;
import de.craftplay.shop.importers.ImporterService;
import de.craftplay.shop.integrations.HeadDatabaseHook;
import de.craftplay.shop.integrations.PlaceholderApiHook;
import de.craftplay.shop.permissionshop.PermissionProductService;
import de.craftplay.shop.playershop.PlayerShopService;
import de.craftplay.shop.protection.ProtectionService;
import de.craftplay.shop.rankshop.RankShopService;
import de.craftplay.shop.referral.ReferralService;
import de.craftplay.shop.servershop.SellCommandService;
import de.craftplay.shop.servershop.ServerShopCategoryGui;
import de.craftplay.shop.servershop.ServerShopFavoriteService;
import de.craftplay.shop.servershop.ServerShopGui;
import de.craftplay.shop.servershop.ServerShopListGui;
import de.craftplay.shop.servershop.ServerShopRegistry;
import de.craftplay.shop.servershop.ServerShopService;
import de.craftplay.shop.servershop.ServerShopTransactionService;
import de.craftplay.shop.servershop.admin.ServerShopAdminEditor;
import de.craftplay.shop.trade.DirectTradeService;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class CraftplayShopPlugin extends JavaPlugin implements Listener {
    private PluginLogService pluginLogService;
    private ConfigService configService;
    private TaskService taskService;
    private LanguageService languageService;
    private PlayerLanguageService playerLanguageService;
    private PlayerSettingsService playerSettingsService;
    private DatabaseService databaseService;
    private EconomyService economyService;
    private GuiService guiService;
    private GuiActionExecutor guiActionExecutor;
    private GuiPlaceholderService guiPlaceholderService;
    private ServerShopRegistry serverShopRegistry;
    private ServerShopService serverShopService;
    private ServerShopGui serverShopGui;
    private ServerShopCategoryGui serverShopCategoryGui;
    private ServerShopListGui serverShopListGui;
    private ServerShopFavoriteService serverShopFavoriteService;
    private ServerShopTransactionService serverShopTransactionService;
    private ServerShopAdminEditor serverShopAdminEditor;
    private SellCommandService sellCommandService;
    private TransactionService transactionService;
    private TransactionRollbackService transactionRollbackService;
    private ItemSerializer itemSerializer;
    private ItemMatcher itemMatcher;
    private DirectTradeService directTradeService;
    private AuctionHouseService auctionHouseService;
    private HeadDatabaseHook headDatabaseHook;
    private PlaceholderApiHook placeholderApiHook;
    private ProtectionService protectionService;
    private PlayerShopService playerShopService;
    private AutoSellChestService autoSellChestService;
    private PermissionProductService permissionProductService;
    private RankShopService rankShopService;
    private ReferralService referralService;

    @Override
    public void onEnable() {
        pluginLogService = new PluginLogService(this);
        taskService = new TaskService(this);
        configService = new ConfigService(this);
        configService.load();

        languageService = new LanguageService(this);
        playerLanguageService = new PlayerLanguageService(this);
        itemSerializer = new ItemSerializer();
        itemMatcher = new ItemMatcher();
        headDatabaseHook = new HeadDatabaseHook(this);
        placeholderApiHook = new PlaceholderApiHook(this);
        protectionService = new ProtectionService(this);
        if (!setupDatabase()) {
            return;
        }
        if (!setupEconomy()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        playerSettingsService = new PlayerSettingsService(this);
        transactionService = new TransactionService(this);
        transactionRollbackService = new TransactionRollbackService();
        guiPlaceholderService = new GuiPlaceholderService(this);
        guiActionExecutor = new GuiActionExecutor(this);
        guiService = new GuiService(this);
        serverShopFavoriteService = new ServerShopFavoriteService(this);
        serverShopRegistry = new ServerShopRegistry(this);
        serverShopService = new ServerShopService(this);
        serverShopGui = new ServerShopGui(this);
        serverShopCategoryGui = new ServerShopCategoryGui(this);
        serverShopListGui = new ServerShopListGui(this);
        serverShopTransactionService = new ServerShopTransactionService(this);
        serverShopAdminEditor = new ServerShopAdminEditor(this);
        sellCommandService = new SellCommandService(this);
        directTradeService = new DirectTradeService(this);

        playerShopService = new PlayerShopService(this);
        autoSellChestService = new AutoSellChestService(this);
        auctionHouseService = new AuctionHouseService(this);
        referralService = new ReferralService(this);
        rankShopService = new RankShopService(this);
        permissionProductService = new PermissionProductService(this);
        new ImporterService(this);

        reloadAll();
        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        for (Player player : getServer().getOnlinePlayers()) {
            playerSettingsService.loadAsync(player);
            serverShopFavoriteService.loadAsync(player);
            if (referralService != null) {
                referralService.onJoin(player);
            }
        }
        pluginLogService.info("CraftplayShop " + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll((org.bukkit.plugin.Plugin) this);
        if (playerSettingsService != null) {
            playerSettingsService.saveAllSync();
            playerSettingsService.clear();
        }
        if (serverShopFavoriteService != null) {
            serverShopFavoriteService.clear();
        }
        if (serverShopRegistry != null) {
            serverShopRegistry.cancelStockFlushTask();
        }
        if (playerShopService != null) {
            playerShopService.shutdown();
        }
        if (autoSellChestService != null) {
            autoSellChestService.shutdown();
        }
        if (taskService != null) {
            taskService.cancelAll();
        }
        if (databaseService != null) {
            databaseService.close();
        }
    }

    public void reloadAll() {
        configService.reload();
        languageService.load();
        if (guiService != null) {
            guiService.clearCache();
            guiService.load();
        }
        if (serverShopRegistry != null) {
            serverShopRegistry.load();
        }
        if (headDatabaseHook != null) {
            headDatabaseHook.load();
        }
        if (placeholderApiHook != null) {
            placeholderApiHook.load();
        }
        if (protectionService != null) {
            protectionService.loadHooks();
        }
        if (playerShopService != null) {
            playerShopService.load();
        }
        if (autoSellChestService != null) {
            autoSellChestService.load();
        }
        if (auctionHouseService != null) {
            auctionHouseService.load();
        }
        if (rankShopService != null) {
            rankShopService.load();
        }
        if (permissionProductService != null) {
            permissionProductService.load();
        }
        if (referralService != null) {
            referralService.load();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (playerSettingsService != null) {
            playerSettingsService.loadAsync(event.getPlayer());
        }
        if (serverShopFavoriteService != null) {
            serverShopFavoriteService.loadAsync(event.getPlayer());
        }
        if (referralService != null) {
            referralService.onJoin(event.getPlayer());
        }
    }

    private boolean setupDatabase() {
        databaseService = switch (configService.databaseType()) {
            case SQLITE -> new SQLiteDatabaseService(this);
            case MYSQL -> new MySqlDatabaseService(this);
        };
        try {
            databaseService.connect();
            new TableCreator(this).createTables();
            return true;
        } catch (SQLException exception) {
            pluginLogService.error("Database initialization failed.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private boolean setupEconomy() {
        economyService = new VaultEconomyService(this);
        if (!economyService.setup()) {
            if (configService.requireVault()) {
                languageService.load();
                getServer().getConsoleSender().sendMessage(languageService.get(configService.defaultLanguage(), "general.vaultMissing", java.util.Map.of()));
                return false;
            }
        }
        return true;
    }

    private void registerCommands() {
        MainCommand mainCommand = new MainCommand(this);
        register("shop", mainCommand);
        PluginCommand shopCommand = getCommand("shop");
        if (shopCommand != null) {
            shopCommand.setTabCompleter(mainCommand);
        }
        register("cshop", mainCommand);
        PluginCommand cshopCommand = getCommand("cshop");
        if (cshopCommand != null) {
            cshopCommand.setTabCompleter(mainCommand);
        }
        register("servershop", new ShopCommand(this));
        register("sellhand", mainCommand);
        register("sellall", mainCommand);
        register("sellgui", mainCommand);
        register("trade", new TradeCommand(this));
        register("ah", mainCommand);
        PluginCommand ahCommand = getCommand("ah");
        if (ahCommand != null) {
            ahCommand.setTabCompleter(mainCommand);
        }
        register("asc", autoSellChestService);
        PluginCommand ascCommand = getCommand("asc");
        if (ascCommand != null) {
            ascCommand.setTabCompleter(autoSellChestService);
        }
    }

    private void register(String command, org.bukkit.command.CommandExecutor executor) {
        PluginCommand pluginCommand = getCommand(command);
        if (pluginCommand != null) {
            pluginCommand.setExecutor(executor);
        }
    }

    public PluginLogService getPluginLogService() {
        return pluginLogService;
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public TaskService getTaskService() {
        return taskService;
    }

    public LanguageService getLanguageService() {
        return languageService;
    }

    public PlayerLanguageService getPlayerLanguageService() {
        return playerLanguageService;
    }

    public PlayerSettingsService getPlayerSettingsService() {
        return playerSettingsService;
    }

    public DatabaseService getDatabaseService() {
        return databaseService;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public GuiService getGuiService() {
        return guiService;
    }

    public GuiActionExecutor getGuiActionExecutor() {
        return guiActionExecutor;
    }

    public GuiPlaceholderService getGuiPlaceholderService() {
        return guiPlaceholderService;
    }

    public ServerShopRegistry getServerShopRegistry() {
        return serverShopRegistry;
    }

    public ServerShopService getServerShopService() {
        return serverShopService;
    }

    public ServerShopGui getServerShopGui() {
        return serverShopGui;
    }

    public ServerShopCategoryGui getServerShopCategoryGui() {
        return serverShopCategoryGui;
    }

    public ServerShopListGui getServerShopListGui() {
        return serverShopListGui;
    }

    public ServerShopFavoriteService getServerShopFavoriteService() {
        return serverShopFavoriteService;
    }

    public ServerShopTransactionService getServerShopTransactionService() {
        return serverShopTransactionService;
    }

    public ServerShopAdminEditor getServerShopAdminEditor() {
        return serverShopAdminEditor;
    }

    public SellCommandService getSellCommandService() {
        return sellCommandService;
    }

    public TransactionService getTransactionService() {
        return transactionService;
    }

    public TransactionRollbackService getTransactionRollbackService() {
        return transactionRollbackService;
    }

    public ItemSerializer getItemSerializer() {
        return itemSerializer;
    }

    public ItemMatcher getItemMatcher() {
        return itemMatcher;
    }

    public DirectTradeService getDirectTradeService() {
        return directTradeService;
    }

    public HeadDatabaseHook getHeadDatabaseHook() {
        return headDatabaseHook;
    }

    public PlaceholderApiHook getPlaceholderApiHook() {
        return placeholderApiHook;
    }

    public ProtectionService getProtectionService() {
        return protectionService;
    }

    public PlayerShopService getPlayerShopService() {
        return playerShopService;
    }

    public AutoSellChestService getAutoSellChestService() {
        return autoSellChestService;
    }

    public AuctionHouseService getAuctionHouseService() {
        return auctionHouseService;
    }

    public PermissionProductService getPermissionProductService() {
        return permissionProductService;
    }

    public RankShopService getRankShopService() {
        return rankShopService;
    }

    public ReferralService getReferralService() {
        return referralService;
    }
}
