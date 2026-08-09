package me.bounser.nascraft.commands.market;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.commands.Command;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.inventorygui.*;
import me.bounser.nascraft.inventorygui.admin.MarketAdminListener;
import me.bounser.nascraft.inventorygui.admin.MarketAdminMenu;
import me.bounser.nascraft.market.MarketAvailabilityListener;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.market.resources.Category;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.web.WebAuthManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MarketCommand extends Command {

    public MarketCommand() {
        super(
                "market",
                new String[]{Config.getInstance().getCommandAlias("market")},
                "Direct access to the market",
                "nascraft.market"
        );
        Bukkit.getPluginManager().registerEvents(new MarketAvailabilityListener(), Nascraft.getInstance());
    }

    @Override
    public void execute(CommandSender sender, String[] args) {

        if(sender instanceof Player) {

            Player player = (Player) sender;

            if (args.length == 1 && args[0].equalsIgnoreCase("admin")) {
                if (!player.hasPermission(MarketAdminListener.PERMISSION)) {
                    Lang.get().message(player, Message.NO_PERMISSION);
                    return;
                }
                MarketAdminMenu.open(player);
                return;
            }

            if (!player.hasPermission("nascraft.market") && Config.getInstance().getMarketPermissionRequirement()) {
                Lang.get().message(player, Message.NO_PERMISSION);
                return;
            }

            if (args.length == 0 && player.hasPermission("nascraft.market.gui")) {
                MarketMenuManager.getInstance().openMenu(player);
                return;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("category") && player.hasPermission("nascraft.market.gui")) {

                Category category = MarketManager.getInstance().getCategoryFromIdentifier(args[1]);

                if (category == null) {
                    Lang.get().message(player, Message.MARKET_CMD_INVALID_CATEGORY);
                    return;
                }

                MarketMenuManager.getInstance().setMenuOfPlayer(player, new CategoryMenu(player, category));
                return;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("item") && player.hasPermission("nascraft.market.gui")) {

                Item item = MarketManager.getInstance().getItem(args[1].toLowerCase());

                if (item == null) {
                    Lang.get().message(player, Message.MARKET_CMD_INVALID_ITEM);
                    return;
                }

                MarketMenuManager.getInstance().setMenuOfPlayer(player, new BuySellMenu(player, item));
                return;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("webcode")) {
                WebAuthManager auth = WebAuthManager.getInstance();
                if (auth.isPlayerCodeRateLimited(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Too many web login attempts. Please wait a minute and try again.");
                    Nascraft.getInstance().getLogger().warning("[Nascraft Web] Rate-limited webcode attempts for " + player.getName() + ".");
                    return;
                }

                String code = args[1];
                if (!code.matches("\\d{6}")) {
                    player.sendMessage(ChatColor.RED + "Invalid code format. The code must be exactly 6 digits.");
                    Nascraft.getInstance().getLogger().info("[Nascraft Web] Invalid webcode format from " + player.getName() + ".");
                    return;
                }

                WebAuthManager.LoginRequest req = auth.getRequestByCode(code);
                if (req == null || req.isExpired()) {
                    player.sendMessage(ChatColor.RED + "This login code is invalid or has expired.");
                    Nascraft.getInstance().getLogger().info("[Nascraft Web] Invalid or expired webcode attempt from " + player.getName() + ".");
                    return;
                }

                req.playerUuid = player.getUniqueId();
                req.username = player.getName();
                req.status = "player_found";

                auth.consumeCode(code);

                player.sendMessage(ChatColor.GREEN + "Web login request linked.");
                player.sendMessage(ChatColor.GREEN + "Please return to your browser and confirm that \"" + player.getName() + "\" is your account.");

                Nascraft.getInstance().getLogger().info("[Nascraft Web] Login code linked to " + player.getName() + ".");
                return;
            }

            if (args.length != 3) {
                Lang.get().message(player, Message.MARKET_CMD_INVALID_USE);
                return;
            }

            int quantity;

            try {
                quantity = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                Lang.get().message(player, Message.MARKET_CMD_INVALID_QUANTITY);
                return;
            }

            if (quantity <= 0) {
                Lang.get().message(player, Message.MARKET_CMD_INVALID_QUANTITY);
                return;
            }

            if (quantity > 64) {
                Lang.get().message(player, Message.MARKET_CMD_MAX_QUANTITY_REACHED);
                return;
            }

            Item item = MarketManager.getInstance().getItem(args[1]);

            if (item == null) {
                Lang.get().message(player, Message.MARKET_CMD_INVALID_IDENTIFIER);
                return;
            }

            switch (args[0].toLowerCase()){
                case "buy":
                    item.buy(quantity, player.getUniqueId(), true);
                    break;
                case "sell":
                    item.sell(quantity, player.getUniqueId(), true);
                    break;
                default:
                    Lang.get().message(player, Message.MARKET_CMD_INVALID_OPTION);
            }

        } else {

            if (args.length == 1) {

                Player player = Bukkit.getPlayer(args[0]);

                if (player == null) {
                    Nascraft.getInstance().getLogger().info(ChatColor.RED + "Invalid player");
                    return;
                }

                MarketMenuManager.getInstance().setMenuOfPlayer(player, new MainMenu(player));
                return;
            }

            if (args.length == 3 && args[0].toLowerCase().equals("category")) {

                Player player = Bukkit.getPlayer(args[2]);

                if (player == null) {
                    Nascraft.getInstance().getLogger().info(ChatColor.RED + "Invalid player");
                    return;
                }

                Category category = MarketManager.getInstance().getCategoryFromIdentifier(args[1]);

                if (category == null) {
                    Nascraft.getInstance().getLogger().info(ChatColor.RED + "Invalid category");
                    return;
                }

                MarketMenuManager.getInstance().setMenuOfPlayer(player, new CategoryMenu(player, category));
                return;
            }

            if (args.length != 4) {
                Nascraft.getInstance().getLogger().info(ChatColor.RED  + "Invalid use of command. \n(CONSOLE) /market <Buy/Sell> <Material> <Quantity> <Player>\n(CONSOLE) /market category <category-identifier> <Player>\n(CONSOLE) /market <Player>");
                return;
            }

            Player player = Bukkit.getPlayer(args[3]);

            if (player == null) {
                Nascraft.getInstance().getLogger().info(ChatColor.RED + "Invalid player");
                return;
            }

            Item item = MarketManager.getInstance().getItem(args[1]);
            switch (args[0]){
                case "buy":
                    item.buy(Integer.parseInt(args[2]), player.getUniqueId(), true);
                    break;
                case "sell":
                    item.sell(Integer.parseInt(args[2]), player.getUniqueId(), true);
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Wrong option: buy / sell");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {

        switch (args.length) {
            case 1:
                List<String> options = new ArrayList<>(Arrays.asList("buy", "sell", "webcode", "category", "item"));
                if (sender.hasPermission(MarketAdminListener.PERMISSION)) options.add("admin");
                return StringUtil.copyPartialMatches(args[0], options, new ArrayList<>());
            case 2:
                if (args[0].equalsIgnoreCase("webcode")) return Collections.singletonList("123456");
                if (args[0].equalsIgnoreCase("admin")) return Collections.emptyList();
                return StringUtil.copyPartialMatches(args[1], MarketManager.getInstance().getAllItemsAndChildsIdentifiers(), new ArrayList<>());
            case 3:
                return Collections.singletonList("quantity");
        }

        return null;
    }
}
