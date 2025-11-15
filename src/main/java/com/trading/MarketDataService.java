package com.trading;

import com.trading.model.MarketTick;

import java.util.*;
import java.util.concurrent.*;

public class MarketDataService {

    private final ConcurrentMap<String, MarketTick> ticks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final List<String> isins = List.of("US0001", "US0002", "GB0001", "JP0001");

    public void start() {
        for (String isin : isins) {
            ticks.put(isin, new MarketTick(isin, 100.0, System.currentTimeMillis()));
        }
        scheduler.scheduleAtFixedRate(this::tick, 0, 300, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        for (String isin : isins) {
            var last = ticks.get(isin);
            double newPrice = Math.max(
                    0.01,
                    Math.round((last.getPrice() + randomVolatility()) * 100.0) / 100.0
            );
            ticks.put(isin, new MarketTick(isin, newPrice, System.currentTimeMillis()));
        }
    }

    private double randomVolatility() {
        return ThreadLocalRandom.current().nextDouble(-0.8, 0.8);
    }

    public MarketTick getLatest(String isin) {
        return ticks.get(isin);
    }

    // naive average (latest) for demo
    public double getAveragePrice(String isin, int samples) {
        MarketTick t = ticks.get(isin);
        return t == null ? -1.0 : t.getPrice();
    }

    public List<String> getAllIsins() {
        return Collections.unmodifiableList(isins);
    }
}
