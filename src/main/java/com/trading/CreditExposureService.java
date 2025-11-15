package com.trading;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock credit/exposure service.
 * Tracks simple per-trader exposure (notional) and a per-trader limit.
 */
public class CreditExposureService {

    // notional exposure (mock)
    private final ConcurrentHashMap<String, Double> exposures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> limits = new ConcurrentHashMap<>();

    public CreditExposureService() {
        // default limits (mock)
        limits.put("TRADER1", 1_000_000.0);
        limits.put("TRADER2", 500_000.0);
        // others use a default below
    }

    // returns total exposure
    public double getExposure(String trader) {
        return exposures.getOrDefault(trader, 0.0);
    }

    public double getLimit(String trader) {
        return limits.getOrDefault(trader, 250_000.0);
    }

    // simple check: allowed if current exposure + notional <= limit
    public boolean isAllowed(String trader, int qty, double limitPrice) {
        double notional = qty * limitPrice;
        double current = getExposure(trader);
        return (current + notional) <= getLimit(trader);
    }

    // increase exposure (call when trade is confirmed)
    public void addExposure(String trader, double notional) {
        exposures.merge(trader, notional, Double::sum);
    }
}
