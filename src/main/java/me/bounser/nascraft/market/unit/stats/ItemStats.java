package me.bounser.nascraft.market.unit.stats;

import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.market.unit.Item;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ItemStats {

    private List<Instant> dataMinute = new ArrayList<>();

    private List<Instant> dataDay = new ArrayList<>();


    private Item item;

    public ItemStats(Item item) { this.item = item; }

    public void addInstant(Instant instant) {

        dataMinute.add(instant);

        // Keep the raw minute sample in the persistent day history. Previously
        // prices_day only received a five-minute aggregate, which made short web
        // chart ranges look like a single diagonal line and threw those recent
        // samples away from the chart after a server restart.
        if (Config.getInstance().isPrimaryNode()) {
            DatabaseManager.get().getDatabase().saveDayPrice(item, instant);
        }

        if (dataMinute.size() % 5 == 0) {

            while (dataMinute.size() > 60)  dataMinute.remove(0);

            Instant dayInstant = new Instant(
                    getLocalDateTimeBetween(LocalDateTime.now(), dataMinute.get(dataMinute.size()-5).getLocalDateTime()),
                    priceAverage(dataMinute.subList(dataMinute.size()-5, dataMinute.size())),
                    volumeAdder(dataMinute.subList(dataMinute.size()-5, dataMinute.size())));

            dataDay.add(dayInstant);

            while (dataDay.size() > 288)  dataDay.remove(0);

            // Persist coarser OHLCV candles on the primary node only. Followers
            // keep the in-memory series above but must not write duplicate rows
            // into the shared database.
            if (Config.getInstance().isPrimaryNode()) {

                Instant bigDayInstant = new Instant(
                        LocalDateTime.now(),
                        priceAverage(dataDay),
                        volumeAdder(dataDay));

                DatabaseManager.get().getDatabase().saveMonthPrice(item, bigDayInstant);

                DatabaseManager.get().getDatabase().saveHistoryPrices(item, bigDayInstant);
            }
        }
    }

    public float priceAverage(List<Instant> instants) {
        if (instants.isEmpty())
            return 0;

        float price = 0;
        for (Instant instantPrice : instants) {
            price += instantPrice.getPrice();
        }

        return price / instants.size();
    }

    public int volumeAdder(List<Instant> instants) {
        if (instants.isEmpty())
            return 0;

        int volume = 0;
        for (Instant instantVolume : instants) {
            volume += instantVolume.getVolume();
        }

        return volume;
    }

    public LocalDateTime getLocalDateTimeBetween(LocalDateTime fecha1, LocalDateTime fecha2) {
        Duration diferencia = Duration.between(fecha1, fecha2);

        Duration mitadDiferencia = diferencia.dividedBy(2);

        LocalDateTime menorFecha = (fecha1.isBefore(fecha2)) ? fecha1 : fecha2;

        return menorFecha.plus(mitadDiferencia);
    }

}
