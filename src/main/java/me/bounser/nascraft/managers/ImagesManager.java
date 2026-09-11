package me.bounser.nascraft.managers;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.images.ItemTextureProvider;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class ImagesManager {

    private static ImagesManager instance;

    public static ImagesManager getInstance() { return instance == null ? instance = new ImagesManager() : instance; }


    public BufferedImage getImage(String identifier) {

        BufferedImage override = loadOverride(identifier);
        if (override != null) return override;

        Material material = resolveMaterial(identifier);
        if (material != null) {
            BufferedImage image = ItemTextureProvider.getImage(material);
            if (image == null) {
                Material fallback = getTextureFallback(material);
                if (fallback != null) image = ItemTextureProvider.getImage(fallback);
            }
            if (image != null) return image;

            Nascraft.getInstance().getLogger().warning(
                    "Unable to render texture for material " + material.name().toLowerCase()
                            + " (market item " + identifier + "). Using a fallback icon instead."
            );
        } else {
            Nascraft.getInstance().getLogger().warning(
                    "Unable to resolve a material for market item " + identifier
                            + ". Using a fallback icon instead."
            );
        }

        // Item images are presentation data. A missing/unsupported Minecraft model
        // must never prevent an otherwise valid market item from being created.
        BufferedImage fallback = getSafeFallbackTexture();
        return fallback != null ? fallback : createPlaceholderImage();
    }

    private Material getTextureFallback(Material material) {
        String name = material.name();

        if (name.endsWith("_CARPET")) {
            String woolName = name.substring(0, name.length() - "_CARPET".length()) + "_WOOL";
            return Material.matchMaterial(woolName);
        }

        return null;
    }

    private BufferedImage getSafeFallbackTexture() {
        // Use ordinary item textures which are available on practically every
        // supported Minecraft version before falling back to a generated image.
        for (Material material : new Material[]{Material.PAPER, Material.STONE, Material.BARRIER}) {
            BufferedImage image = ItemTextureProvider.getImage(material);
            if (image != null) return image;
        }
        return null;
    }

    private BufferedImage createPlaceholderImage() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(55, 65, 81, 255));
            graphics.fillRect(0, 0, 16, 16);
            graphics.setColor(new Color(156, 163, 175, 255));
            graphics.fillRect(3, 3, 10, 10);
            graphics.setColor(new Color(31, 41, 55, 255));
            graphics.fillRect(5, 5, 6, 6);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage loadOverride(String identifier) {
        File file = new File(Nascraft.getInstance().getDataFolder(), "images/" + identifier + ".png");
        if (!file.isFile()) return null;

        try (InputStream input = Files.newInputStream(file.toPath())) {
            return ImageIO.read(input);
        } catch (IOException ignored) {
            return null;
        } catch (IllegalArgumentException e) {
            Nascraft.getInstance().getLogger().info("Invalid argument for image: " + identifier);
            return null;
        }
    }

    private Material resolveMaterial(String identifier) {
        FileConfiguration items = Config.getInstance().getItemsFileConfiguration();

        String typeName = items.getString("items." + identifier + ".item-stack.type");
        if (typeName == null) {
            typeName = identifier.replaceAll("\\d", "");
        }

        try {
            return Material.matchMaterial(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static byte[] getBytesOfImage(BufferedImage image) {
        // Keep callers safe even if a third-party/custom source unexpectedly
        // supplied null. Images are optional presentation data, not market state.
        if (image == null) image = getInstance().createPlaceholderImage();

        ByteArrayOutputStream baosBalance = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", baosBalance);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baosBalance.toByteArray();
    }

}
