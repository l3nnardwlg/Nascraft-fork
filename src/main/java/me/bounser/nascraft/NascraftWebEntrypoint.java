package me.bounser.nascraft;

import me.bounser.nascraft.scheduler.FoliaScheduler;
import me.bounser.nascraft.web.WebConfig;
import me.bounser.nascraft.web.WebServerManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;

/**
 * Nascraft entrypoint that restores the bundled web frontend when the bundled
 * web version changes, then starts the self-hosted web server.
 */
public class NascraftWebEntrypoint extends Nascraft {

    private static final String WEB_BUNDLE_VERSION = "1.9.5-web-account-linking";
    private static final List<String> WEB_RESOURCES = List.of(
            "web/index.html",
            "web/style.css",
            "web/script.js",
            "images/logo.png",
            "images/logo-color.png",
            "images/fire.png"
    );

    private WebServerManager webServerManager;

    @Override
    public void onEnable() {
        super.onEnable();

        WebConfig webConfig = new WebConfig(this);
        if (!webConfig.enabled()) return;

        restoreBundledWebFrontend();

        webServerManager = new WebServerManager(this, webConfig);
        FoliaScheduler.runAsync(this, webServerManager::startServer);
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
                    && Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim().equals(WEB_BUNDLE_VERSION)) {
                getLogger().info("Nascraft web frontend " + WEB_BUNDLE_VERSION + " is present at " + webDirectory.getAbsolutePath());
                return;
            }
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Could not read web frontend version marker; restoring bundled frontend.", exception);
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

            if (!webDirectory.exists() && !webDirectory.mkdirs()) {
                throw new IOException("Could not create web directory " + webDirectory);
            }
            Files.writeString(marker.toPath(), WEB_BUNDLE_VERSION + System.lineSeparator(), StandardCharsets.UTF_8);
            getLogger().info("Nascraft web frontend restored to " + webDirectory.getAbsolutePath());
        } catch (IOException | IllegalArgumentException exception) {
            getLogger().log(Level.SEVERE, "Failed to restore bundled Nascraft web frontend.", exception);
        }
    }
}
