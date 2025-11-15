package com.trading;

import com.trading.model.MarketTick;

import java.util.*;
import java.util.concurrent.*;

public class MarketDataService {

    private final ConcurrentHashMap<String, MarketTick> ticks = new ConcurrentHashMap<>();
    private final Random rnd = new Random();

    private final List<String> sampleIsins =
            Arrays.asList("US0001", "US0002", "US0003", "DE0001", "IN0001");

    public void start() {
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::updateTicks, 0, 500, TimeUnit.MILLISECONDS);
    }

    /** Generate new prices randomly */
    private void updateTicks() {
        for (String isin : sampleIsins) {
            double price = 80 + rnd.nextDouble() * 40;
            ticks.put(isin, new MarketTick(isin, price, System.currentTimeMillis()));
        }
    }

    public MarketTick getLatest(String isin) {
        return ticks.getOrDefault(isin,
                new MarketTick(isin, 100, System.currentTimeMillis()));
    }

    public double getAveragePrice(String isin, int window) {
        // simple: use last price +/- random small variation
        MarketTick t = getLatest(isin);
        return t.getPrice() * (0.98 + rnd.nextDouble() * 0.04);
    }

    public Set<String> getAllIsins() {
        return ticks.keySet();
    }
}
