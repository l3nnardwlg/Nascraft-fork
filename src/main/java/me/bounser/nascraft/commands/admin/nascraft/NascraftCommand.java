package me.bounser.nascraft.commands.admin.nascraft;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.commands.Command;
import me.bounser.nascraft.commands.admin.marketeditor.overview.MarketEditorManager;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.config.Messages;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.database.BaseDatabase;
import me.bounser.nascraft.database.Database;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.database.DatabaseMigrator;
import me.bounser.nascraft.database.mysql.MySQL;
import me.bounser.nascraft.database.mysql.MysqlDialect;
import me.bounser.nascraft.database.sqlite.SqliteDatabase;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Style;
import me.bounser.nascraft.managers.DebtManager;
import me.bounser.nascraft.managers.currencies.CurrenciesManager;
import me.bounser.nascraft.managers.currencies.Currency;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.StringUtil;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class NascraftCommand extends Command {

    private final List<String> arguments = Arrays.asList("reload", "edit", "stop", "resume", "info", "save", "logs", "forgivedebt", "migrate");
    private final List<String> tradesArguments = Arrays.asList("<player nick or uuid>", "<item>", "global");

    public NascraftCommand() {
        super("nascraft", new String[]{Config.getInstance().getCommandAlias("nascraft")}, "Admin command", "nascraft.admin");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Messages messages = Messages.get();

        if (sender instanceof Player && !sender.hasPermission("nascraft.admin")) {
            messages.command(sender, "admin.no-permission");
            return;
        }

        if (args.length == 0) {
            sendSyntax(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "save" -> {
                DatabaseManager.get().getDatabase().saveEverything();
                messages.command(sender, "admin.data-saved");
            }
            case "logs" -> handleLogs(sender, args);
            case "info" -> handleInfo(sender);
            case "stop" -> {
                if (MarketManager.getInstance().getActive()) {
                    MarketManager.getInstance().stop();
                    messages.command(sender, "admin.shop-stopped");
                } else {
                    messages.command(sender, "admin.shop-already-stopped");
                }
            }
            case "resume" -> {
                if (!MarketManager.getInstance().getActive()) {
                    MarketManager.getInstance().resume();
                    messages.command(sender, "admin.shop-resumed");
                } else {
                    messages.command(sender, "admin.shop-already-active");
                }
            }
            case "reload" -> handleReload(sender);
            case "edit" -> {
                if (sender instanceof Player player) MarketEditorManager.getInstance().startEditing(player);
                else messages.command(sender, "admin.player-only");
            }
            case "forgivedebt" -> handleForgiveDebt(sender, args);
            case "migrate" -> handleMigrate(sender, args);
            default -> sendSyntax(sender);
        }
    }

    private void sendSyntax(CommandSender sender) {
        Messages.get().command(sender, "admin.syntax", "[ARGS]", String.join(" | ", arguments));
    }

    private void handleLogs(CommandSender sender, String[] args) {
        Messages messages = Messages.get();
        if (args.length != 2) {
            messages.command(sender, "admin.logs-syntax");
            return;
        }
        if (!(sender instanceof Player playerLog)) {
            messages.command(sender, "admin.player-only");
            return;
        }

        if (args[1].equalsIgnoreCase("global")) {
            playerLog.setMetadata("NascraftLogInventory", new FixedMetadataValue(Nascraft.getInstance(), "global"));
            playerLog.setMetadata("NascraftLogInventoryPage", new FixedMetadataValue(Nascraft.getInstance(), 0));
            NascraftLogListener.createTradePage(playerLog, null, null);
            return;
        }

        Item item = MarketManager.getInstance().getItem(args[1].toLowerCase());
        if (item != null) {
            playerLog.setMetadata("NascraftLogInventory", new FixedMetadataValue(Nascraft.getInstance(), "item-" + item.getIdentifier()));
            playerLog.setMetadata("NascraftLogInventoryPage", new FixedMetadataValue(Nascraft.getInstance(), 0));
            NascraftLogListener.createTradePage(playerLog, item, null);
            return;
        }

        Player player = Bukkit.getPlayer(args[1]);
        UUID uuid;
        if (player != null) {
            uuid = player.getUniqueId();
        } else if (isValidUUID(args[1])) {
            uuid = UUID.fromString(args[1]);
        } else {
            messages.command(sender, "admin.argument-not-identified");
            return;
        }

        playerLog.setMetadata("NascraftLogInventory", new FixedMetadataValue(Nascraft.getInstance(), "uuid-" + uuid));
        playerLog.setMetadata("NascraftLogInventoryPage", new FixedMetadataValue(Nascraft.getInstance(), 0));
        NascraftLogListener.createTradePage(playerLog, null, uuid);
    }

    private void handleInfo(CommandSender sender) {
        Currency currency = CurrenciesManager.getInstance().getDefaultCurrency();
        Messages.get().command(sender, "admin.info",
                "[INFLATION]", String.valueOf(Formatter.roundToDecimals(MarketManager.getInstance().getConsumerPriceIndex() - 100, 3)),
                "[DEBT]", Formatter.format(currency, DatabaseManager.get().getDatabase().getAllOutstandingDebt(), Style.ROUND_BASIC),
                "[DEBTORS]", String.valueOf(DatabaseManager.get().getDatabase().getUUIDAndDebt().keySet().size()),
                "[INTERESTS]", Formatter.format(currency, DatabaseManager.get().getDatabase().getAllInterestsPaid(), Style.ROUND_BASIC),
                "[TAXES]", Formatter.format(currency, Math.abs(DatabaseManager.get().getDatabase().getAllTaxesCollected()), Style.ROUND_BASIC));
    }

    private void handleReload(CommandSender sender) {
        Messages messages = Messages.get();
        messages.command(sender, "admin.reload-start");
        Config.getInstance().reload();
        Lang.get().reload();
        messages.reload();
        messages.command(sender, "admin.reload-language", "[LANG]", Config.getInstance().getSelectedLanguage());
        int items = MarketManager.getInstance().getAllItems().size();
        int parents = MarketManager.getInstance().getAllParentItems().size();
        messages.command(sender, "admin.reload-done",
                "[ITEMS]", String.valueOf(items),
                "[PARENTS]", String.valueOf(parents),
                "[CHILDS]", String.valueOf(items - parents),
                "[CATEGORIES]", String.valueOf(Config.getInstance().getCategories().size()));
    }

    private void handleForgiveDebt(CommandSender sender, String[] args) {
        Messages messages = Messages.get();
        if (args.length != 3) {
            messages.command(sender, "admin.forgive-usage");
            return;
        }

        Player player = Bukkit.getPlayer(args[1]);
        if (player == null) {
            messages.command(sender, "admin.player-not-found");
            return;
        }

        double playerDebt = DebtManager.getInstance().getDebtOfPlayer(player.getUniqueId());
        final double debt;
        if (args[2].equalsIgnoreCase("all")) {
            debt = playerDebt;
        } else {
            try {
                debt = Double.parseDouble(args[2]);
            } catch (NumberFormatException exception) {
                messages.command(sender, "admin.invalid-amount");
                return;
            }
        }

        if (!Double.isFinite(debt) || debt <= 0) {
            messages.command(sender, "admin.invalid-amount");
            return;
        }

        double amount = Math.min(debt, playerDebt);
        DebtManager.getInstance().decreaseDebt(player.getUniqueId(), amount);
        messages.command(sender, "admin.forgive-success",
                "[AMOUNT]", Formatter.format(CurrenciesManager.getInstance().getDefaultCurrency(), amount, Style.ROUND_BASIC),
                "[PLAYER]", player.getName(),
                "[REMAINING]", Formatter.format(CurrenciesManager.getInstance().getDefaultCurrency(), DebtManager.getInstance().getDebtOfPlayer(player.getUniqueId()), Style.ROUND_BASIC));
    }

    private void handleMigrate(CommandSender sender, String[] args) {
        Messages messages = Messages.get();
        if (args.length != 2 || !args[1].equalsIgnoreCase("mysql")) {
            messages.command(sender, "admin.migrate-usage");
            return;
        }

        Database current = DatabaseManager.get().getDatabase();
        if (!(current instanceof SqliteDatabase)) {
            messages.command(sender, "admin.migrate-source");
            return;
        }

        messages.command(sender, "admin.migrate-start");
        final BaseDatabase source = (BaseDatabase) current;
        FoliaScheduler.runAsync(Nascraft.getInstance(), () -> {
            Config c = Config.getInstance();
            MySQL target = new MySQL(c.getHost(), c.getPort(), c.getDatabase(), c.getUser(), c.getPassword());
            try {
                target.connect();
                int rows;
                try (Connection s = source.getConnection(); Connection d = target.getConnection()) {
                    d.setAutoCommit(false);
                    rows = DatabaseMigrator.copyAll(s, d, new MysqlDialect());
                    d.commit();
                }
                Nascraft.getInstance().getLogger().info("[Migrate] Done - " + rows + " row(s) copied to MySQL. Set database.type: MySQL and restart.");
            } catch (Exception e) {
                Nascraft.getInstance().getLogger().log(Level.WARNING, "[Migrate] Failed: " + e.getMessage(), e);
            } finally {
                target.close();
            }
        });
    }

    public static boolean isValidUUID(String uuidString) {
        try {
            UUID.fromString(uuidString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length > 1) {
            if (args[0].equalsIgnoreCase("logs"))
                return StringUtil.copyPartialMatches(args[1], tradesArguments, new ArrayList<>());

            if (args[0].equalsIgnoreCase("forgivedebt")) {
                if (args.length == 3) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) return List.of("Invalid player");
                    return Arrays.asList("all", String.valueOf(Formatter.roundToDecimals(
                            DebtManager.getInstance().getDebtOfPlayer(player.getUniqueId()),
                            CurrenciesManager.getInstance().getDefaultCurrency().getDecimalPrecission())));
                }

                List<String> playerNames = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) playerNames.add(player.getName());
                return StringUtil.copyPartialMatches(args[1], playerNames, new ArrayList<>());
            }
        }

        return StringUtil.copyPartialMatches(args[0], arguments, new ArrayList<>());
    }
}
