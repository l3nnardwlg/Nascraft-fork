package me.bounser.nascraft;

import me.bounser.nascraft.commands.orders.OrdersCommand;
import me.bounser.nascraft.commands.pay.PayCommand;
import me.bounser.nascraft.inventorygui.InfoMenuReturnListener;
import me.bounser.nascraft.market.playerorders.PlayerOrdersManager;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import me.bounser.nascraft.web.WebConfig;
import me.bounser.nascraft.web.WebServerManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;

public class NascraftWebEntrypoint extends Nascraft {
    // Bump whenever bundled frontend files change. Existing installations keep an
    // external copy under plugins/Nascraft/web, so an unchanged marker otherwise
    // leaves stale HTML/JS in place forever.
    private static final String WEB_BUNDLE_VERSION = "1.9.9-market-web-polish-r3";
    private static final List<String> WEB_RESOURCES = List.of(
            "web/index.html",
            "web/style.css",
            "web/script.js",
            "web/production-hardening.js",
            "images/logo.png",
            "images/logo-color.png",
            "images/fire.png"
    );

    private WebServerManager webServerManager;

    @Override
    public void onEnable() {
        super.onEnable();

        ensureCustomFeatureDefaults();
        getServer().getPluginManager().registerEvents(new InfoMenuReturnListener(), this);

        if (getConfig().getBoolean("custom-features.orders", true)) {
            PlayerOrdersManager playerOrders = PlayerOrdersManager.getInstance();
            getServer().getPluginManager().registerEvents(playerOrders, this);
            new OrdersCommand();
        } else {
            getLogger().info("AGF player orders are disabled by custom-features.orders.");
        }

        if (getConfig().getBoolean("custom-features.pay", true)
                && !getConfig().getBoolean("commands.market.enabled", true)) {
            new PayCommand();
        }

        if (!getConfig().getBoolean("custom-features.pay", true)) {
            getLogger().info("AGF pay system is disabled by custom-features.pay.");
        }
        if (!getConfig().getBoolean("custom-features.auction-house", true)) {
            getLogger().info("AGF auction house is disabled by custom-features.auction-house.");
        }

        WebConfig webConfig = new WebConfig(this);
        if (!webConfig.enabled()) return;

        restoreBundledWebFrontend();
        webServerManager = new WebServerManager(this, webConfig);
        FoliaScheduler.runAsync(this, webServerManager::startServer);
    }

    private void ensureCustomFeatureDefaults() {
        getConfig().addDefault("custom-features.pay", true);
        getConfig().addDefault("custom-features.orders", true);
        getConfig().addDefault("custom-features.auction-house", true);
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    @Override
    public void onDisable() {
        if (webServerManager != null && webServerManager.isRunning()) {
            getLogger().info("Stopping web server...");
            webServerManager.stopServer();
        }
        super.onDisable();
    }

    private void restoreBundledWebFrontend() {
        File webDirectory = new File(getDataFolder(), "web");
        File marker = new File(webDirectory, ".nascraft-web-version");

        try {
            if (marker.isFile()
                    && Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim().equals(WEB_BUNDLE_VERSION)
                    && frontendLooksCurrent(webDirectory)) {
                getLogger().info("Nascraft web frontend " + WEB_BUNDLE_VERSION + " is present at " + webDirectory.getAbsolutePath());
                return;
            }
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Could not validate web frontend; restoring bundled frontend.", exception);
        }

        try {
            for (String resource : WEB_RESOURCES) {
                File destination = new File(getDataFolder(), resource);
                File parent = destination.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Could not create directory " + parent);
                }
                saveResource(resource, true);
                getLogger().info("Restored bundled web resource: " + resource);
            }

            File indexFile = new File(webDirectory, "index.html");
            ensureProductionHardeningScript(indexFile);
            makeProductionHardeningCompatible(new File(webDirectory, "production-hardening.js"));

            if (!frontendLooksCurrent(webDirectory)) {
                throw new IOException("Restored frontend failed integrity check; required market DOM elements are missing.");
            }

            Files.writeString(marker.toPath(), WEB_BUNDLE_VERSION, StandardCharsets.UTF_8);
            getLogger().info("Restored Nascraft web frontend " + WEB_BUNDLE_VERSION + " to " + webDirectory.getAbsolutePath());
        } catch (IOException | IllegalArgumentException exception) {
            getLogger().log(Level.SEVERE, "Could not restore bundled Nascraft web frontend.", exception);
        }
    }

    private boolean frontendLooksCurrent(File webDirectory) throws IOException {
        File indexFile = new File(webDirectory, "index.html");
        File scriptFile = new File(webDirectory, "script.js");
        if (!indexFile.isFile() || !scriptFile.isFile()) return false;

        String html = Files.readString(indexFile.toPath(), StandardCharsets.UTF_8);
        return html.contains("id=\"current-price\"")
                && html.contains("id=\"sell-price-display\"")
                && html.contains("id=\"buy-price-display\"")
                && html.contains("id=\"item-price-chart-container\"")
                && html.contains("script.js");
    }

    private void ensureProductionHardeningScript(File indexFile) throws IOException {
        if (!indexFile.isFile()) return;

        String html = Files.readString(indexFile.toPath(), StandardCharsets.UTF_8);
        if (html.contains("production-hardening.js")) return;

        String script = "    <script src=\"production-hardening.js\" defer></script>\n";
        if (html.contains("</body>")) html = html.replace("</body>", script + "</body>");
        else html = html + System.lineSeparator() + script;
        Files.writeString(indexFile.toPath(), html, StandardCharsets.UTF_8);
    }

    /**
     * Lightweight Charts 4.x can expose its namespace as a frozen/read-only
     * object. The optional hardening layer used to monkey-patch createChart,
     * which throws before the rest of that file can initialize. Guard that
     * optional hook so the core web application always continues to boot.
     */
    private void makeProductionHardeningCompatible(File hardeningFile) throws IOException {
        if (!hardeningFile.isFile()) return;

        String javascript = Files.readString(hardeningFile.toPath(), StandardCharsets.UTF_8);
        String unsafeCall = "    hookLightweightChart();";
        if (!javascript.contains(unsafeCall)) return;

        String guardedCall = "    try {\n"
                + "        hookLightweightChart();\n"
                + "    } catch (error) {\n"
                + "        console.debug('[Nascraft] Optional chart range hook unavailable:', error);\n"
                + "    }";

        javascript = javascript.replace(unsafeCall, guardedCall);
        Files.writeString(hardeningFile.toPath(), javascript, StandardCharsets.UTF_8);
    }
}
