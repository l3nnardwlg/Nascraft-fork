package me.bounser.nascraft.chart.price;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.stats.Instant;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.ui.RectangleInsets;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class ItemChartReduced {

    private static BufferedImage ditheredUp;
    private static BufferedImage ditheredDown;

    public static void load() {
        ditheredUp = loadGradient("images/gradient-dithered-up.png");
        ditheredDown = loadGradient("images/gradient-dithered-down.png");
    }

    private static BufferedImage loadGradient(String path) {
        BufferedImage fallback = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        try (InputStream input = Nascraft.getInstance().getResource(path)) {
            if (input == null) {
                Nascraft.getInstance().getLogger().warning("Missing chart resource " + path + "; using transparent fallback.");
                return fallback;
            }
            BufferedImage source = ImageIO.read(input);
            if (source == null) return fallback;
            Graphics2D graphics = fallback.createGraphics();
            graphics.drawImage(source, 0, 0, 128, 128, null);
            graphics.dispose();
            return fallback;
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning("Could not load chart resource " + path + ": " + e.getMessage());
            return fallback;
        }
    }

    public static BufferedImage getImage(Item item, ChartType chartType) {
        if (item == null) return new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);

        Item finalItem = item.isParent() ? item : item.getParent();
        if (finalItem == null) finalItem = item;

        BufferedImage image = createChart(finalItem, chartType).createBufferedImage(128, 128);

        boolean up = switch (chartType) {
            case DAY -> finalItem.getPrice().getDayChange() > 0;
            case MONTH -> finalItem.getPrice().getMonthChange() > 0;
            case YEAR -> finalItem.getPrice().getYearChange() > 0;
            case ALL -> finalItem.getPrice().getAllChange() > 0;
        };

        BufferedImage gradient = up ? ditheredUp : ditheredDown;
        if (gradient == null) gradient = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        BufferedImage background = mergeImages(image, gradient);

        Graphics2D imageGraphics = background.createGraphics();
        drawDottedLine(imageGraphics, 2, 31, 125, 31, Color.GRAY, 1, 2);
        drawDottedLine(imageGraphics, 2, 62, 125, 62, Color.GRAY, 1, 2);
        drawDottedLine(imageGraphics, 2, 93, 125, 93, Color.GRAY, 1, 2);
        imageGraphics.dispose();

        return background;
    }

    public static void drawDottedLine(Graphics2D g, int x1, int y1, int x2, int y2, Color color, float dotLength, float spaceLength) {
        g.setColor(color);
        float[] dashPattern = { dotLength, spaceLength };
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, dashPattern, 0));
        g.drawLine(x1, y1, x2, y2);
    }

    public static BufferedImage mergeImages(BufferedImage img1, BufferedImage img2) {
        int width = Math.max(img1.getWidth(), img2.getWidth());
        int height = Math.max(img1.getHeight(), img2.getHeight());
        BufferedImage mergedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel1 = (x < img1.getWidth() && y < img1.getHeight()) ? img1.getRGB(x, y) : 0;
                int pixel2 = (x < img2.getWidth() && y < img2.getHeight()) ? img2.getRGB(x, y) : 0;

                Color color1 = new Color(pixel1, true);
                if (color1.getAlpha() == 0) {
                    mergedImage.setRGB(x, y, new Color(0, 0, 0, 0).getRGB());
                } else if (areColorsSimilar(pixel1, new Color(0, 155, 0).getRGB(), 15)
                        || areColorsSimilar(pixel1, new Color(155, 0, 0).getRGB(), 15)) {
                    mergedImage.setRGB(x, y, pixel1);
                } else {
                    mergedImage.setRGB(x, y, pixel2);
                }
            }
        }

        return mergedImage;
    }

    public static boolean areColorsSimilar(int color1, int color2, int tolerance) {
        int red1 = (color1 >> 16) & 0xFF;
        int green1 = (color1 >> 8) & 0xFF;
        int blue1 = color1 & 0xFF;

        int red2 = (color2 >> 16) & 0xFF;
        int green2 = (color2 >> 8) & 0xFF;
        int blue2 = color2 & 0xFF;

        return Math.abs(red1 - red2) <= tolerance
                && Math.abs(green1 - green2) <= tolerance
                && Math.abs(blue1 - blue2) <= tolerance;
    }

    private static JFreeChart createChart(Item item, ChartType chartType) {
        List<Instant> data;

        try {
            data = switch (chartType) {
                case DAY -> DatabaseManager.get().getDatabase().getDayPrices(item);
                case MONTH -> DatabaseManager.get().getDatabase().getMonthPrices(item);
                case YEAR -> DatabaseManager.get().getDatabase().getYearPrices(item);
                case ALL -> DatabaseManager.get().getDatabase().getAllPrices(item);
            };
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning("Could not load chart history for " + item.getIdentifier() + ": " + e.getMessage());
            data = Collections.emptyList();
        }

        TimeSeries series = createPriceDataset(data, item, chartType);
        TimeSeriesCollection dataset = new TimeSeriesCollection(series);

        boolean up = switch (chartType) {
            case DAY -> item.getPrice().getDayChange() > 0;
            case MONTH -> item.getPrice().getMonthChange() > 0;
            case YEAR -> item.getPrice().getYearChange() > 0;
            case ALL -> item.getPrice().getAllChange() > 0;
        };

        JFreeChart chart = ChartFactory.createTimeSeriesChart(null, null, null, dataset, false, false, false);
        XYPlot plot = chart.getXYPlot();
        DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();

        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);
        dateAxis.setVisible(false);
        rangeAxis.setVisible(false);
        plot.setBackgroundPaint(new Color(0, 0, 0, 0));

        XYAreaRenderer areaRenderer = new XYAreaRenderer();
        XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer();
        lineRenderer.setSeriesShapesVisible(0, false);
        lineRenderer.setSeriesStroke(0, new BasicStroke(1.5f));

        chart.setBackgroundPaint(new Color(0, 0, 0, 0));
        areaRenderer.setSeriesPaint(0, Color.WHITE);
        lineRenderer.setSeriesPaint(0, up ? new Color(0, 155, 0) : new Color(155, 0, 0));

        plot.setInsets(new RectangleInsets(7, 0, 0, 0), true);
        areaRenderer.setOutline(false);
        dateAxis.setLowerMargin(0);
        dateAxis.setUpperMargin(0);
        plot.setDataset(1, dataset);
        plot.setRenderer(0, lineRenderer);
        plot.setRenderer(1, areaRenderer);
        plot.setOutlineVisible(false);
        chart.setPadding(new RectangleInsets(0, 0, 0, 0));
        plot.mapDatasetToRangeAxis(1, 0);
        lineRenderer.setSeriesVisibleInLegend(0, false);

        return chart;
    }

    private static TimeSeries createPriceDataset(List<Instant> instants, Item item, ChartType type) {
        TimeSeries series = new TimeSeries("Price");

        double firstValue = 0;
        double lastValue = 0;
        LocalDateTime timeOld = null;
        LocalDateTime timeRecent = null;
        double high = 0;
        double low = -1;
        int validPoints = 0;

        if (instants != null) {
            for (Instant instant : instants) {
                if (instant == null || !Double.isFinite(instant.getPrice()) || instant.getPrice() <= 0) continue;

                LocalDateTime time = instant.getLocalDateTime();
                if (time == null) continue;

                Minute minute = new Minute(time.getMinute(), time.getHour(), time.getDayOfMonth(), time.getMonthValue(), time.getYear());
                series.addOrUpdate(minute, instant.getPrice());
                validPoints++;

                high = high == 0 ? instant.getPrice() : Math.max(high, instant.getPrice());
                low = low < 0 ? instant.getPrice() : Math.min(low, instant.getPrice());

                if (timeRecent == null || time.isAfter(timeRecent)) {
                    timeRecent = time;
                    lastValue = instant.getPrice();
                }
                if (timeOld == null || time.isBefore(timeOld)) {
                    timeOld = time;
                    firstValue = instant.getPrice();
                }
            }
        }

        // A fresh market or a recently added item can legitimately have no history yet.
        // Render a flat current-price line instead of an empty/NaN chart.
        if (validPoints < 2) {
            double current = item.getPrice().getValue();
            if (!Double.isFinite(current) || current <= 0) current = Math.max(0.01, item.getPrice().getInitialValue());
            LocalDateTime now = LocalDateTime.now();
            series.addOrUpdate(toMinute(now.minusMinutes(1)), current);
            series.addOrUpdate(toMinute(now), current);
            firstValue = current;
            lastValue = current;
            high = Math.max(high, current);
            low = low < 0 ? current : Math.min(low, current);
        }

        float change = firstValue > 0 && Double.isFinite(firstValue) && Double.isFinite(lastValue)
                ? (float) ((lastValue / firstValue) - 1.0)
                : 0f;
        if (!Float.isFinite(change)) change = 0f;
        if (low < 0 || !Double.isFinite(low)) low = high;

        switch (type) {
            case DAY -> {
                item.getPrice().setDayHigh(high);
                item.getPrice().setDayLow(low);
                item.getPrice().setDayChange(change);
            }
            case MONTH -> {
                item.getPrice().setMonthHigh(high);
                item.getPrice().setMonthLow(low);
                item.getPrice().setMonthChange(change);
            }
            case YEAR -> {
                item.getPrice().setYearHigh(high);
                item.getPrice().setYearLow(low);
                item.getPrice().setYearChange(change);
            }
            case ALL -> {
                item.getPrice().setAllHigh(high);
                item.getPrice().setAllLow(low);
                item.getPrice().setAllChange(change);
            }
        }

        return series;
    }

    private static Minute toMinute(LocalDateTime time) {
        return new Minute(time.getMinute(), time.getHour(), time.getDayOfMonth(), time.getMonthValue(), time.getYear());
    }
}
