package com.trading;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExposureService {

    // Mock exposure storage
    private final Map<String, Double> currentExposure = new ConcurrentHashMap<>();

    // Hard-coded credit limits per trader
    private final Map<String, Double> creditLimits = Map.of(
            "T-saffin", 5_000_000.0,
            "T-Alpha", 10_000_000.0,
            "T-Beta", 2_000_000.0,
            "T-risky", 1_000_000.0
    );

    // Update exposure after a trade
    public void addExposure(String trader, double amount) {
        currentExposure.merge(trader, amount, Double::sum);
    }

    // Return exposure info in a simple Map
    public Map<String, Object> getExposureInfo(String trader) {
        double exposure = currentExposure.getOrDefault(trader, 0.0);
        double limit = creditLimits.getOrDefault(trader, 2_000_000.0);

        boolean allowed = (exposure < limit);

        return Map.of(
                "trader", trader,
                "currentExposure", exposure,
                "limit", limit,
                "allowed", allowed
        );
    }

    // Check if trader allowed
    public boolean isAllowed(String trader) {
        double exposure = currentExposure.getOrDefault(trader, 0.0);
        double limit = creditLimits.getOrDefault(trader, 2_000_000.0);
        return exposure < limit;
    }
}
