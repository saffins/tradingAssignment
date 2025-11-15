package com.trading;

import com.trading.model.MarketTick;
import com.trading.model.Trade;
import com.trading.model.TradeState;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TradeService {

    private final MarketDataService market;
    private final InstrumentService instrumentService;
    private final CreditExposureService exposureService;
    private final FixMockService fixService;

    private final ConcurrentHashMap<String, Trade> store = new ConcurrentHashMap<>();
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Random rnd = new Random();

    // tolerance for price deviation (10% default)
    private final double marketTolerance = 0.10;

    public TradeService(MarketDataService market,
                        InstrumentService instrumentService,
                        CreditExposureService exposureService,
                        FixMockService fixService) {

        this.market = market;
        this.instrumentService = instrumentService;
        this.exposureService = exposureService;
        this.fixService = fixService;
    }

    public Trade createTrade(String tradeId, String isin, String trader, int qty, double limitPrice) {
        Trade t = new Trade(tradeId, isin, trader, qty, limitPrice);
        store.put(tradeId, t);
        return t;
    }

    public Optional<Trade> find(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public Collection<Trade> getAllTrades() {
        return new ArrayList<>(store.values());
    }

    public boolean cancel(String id) {
        Trade t = store.get(id);
        if (t == null) return false;

        synchronized (t) {
            if (t.getState() == TradeState.CONFIRMED || t.getState() == TradeState.REJECTED)
                return false;

            t.setState(TradeState.CANCELLED);
            t.addHistory("CANCELLED");
            store.put(t.getId(), t);
            return true;
        }
    }

    public void executeAsync(Trade t) {
        exec.submit(() -> internalExecuteWithRetry(t));
    }

    public void submitForExecution(Trade t) {
        executeAsync(t);
    }

    private void internalExecuteWithRetry(Trade t) {

        AtomicBoolean attempted = new AtomicBoolean(false);
        if (!attempted.compareAndSet(false, true)) return;

        int attempts = 0;
        int maxAttempts = 5;
        long backoffBase = 400;

        while (attempts < maxAttempts) {
            attempts++;
            t.incrementRetry();
            t.setExecutionStartTime(System.currentTimeMillis());

            try {
                if (t.getState() == TradeState.CANCELLED) {
                    store.put(t.getId(), t);
                    return;
                }

                // 12% chance of random failure
                if (rnd.nextDouble() < 0.12) {
                    t.setState(TradeState.RETRY);
                    t.addHistory("TRANSIENT_FAILURE");
                    store.put(t.getId(), t);
                    Thread.sleep(100 + rnd.nextInt(300));
                    throw new RuntimeException("Simulated transient failure");
                }

                // Market price fetch
                MarketTick mt = market.getLatest(t.getIsin());
                double marketPx = (mt != null) ? mt.getPrice() : 0.0;

                // Execution price decision
                double execPx = t.getLimitPrice() > 0 ? t.getLimitPrice() : marketPx;
                t.setExecutionPrice(execPx);

                // Market average for deviation check
                double avg = market.getAveragePrice(t.getIsin(), 5);
                if (avg > 0) {
                    double diff = Math.abs(execPx - avg) / avg;
                    if (diff > marketTolerance) {
                        t.setState(TradeState.REJECTED);
                        t.addHistory("REJECTED_PRICE_DEVIATION");
                        t.setExecutionEndTime(System.currentTimeMillis());
                        fixService.createExecutionReport(t.getId(), execPx, 0);
                        store.put(t.getId(), t);
                        return;
                    }
                }

                // Remaining qty
                int remaining = t.getQuantity() - t.getFilled();
                if (remaining <= 0) {
                    t.setState(TradeState.CONFIRMED);
                    t.setExecutionEndTime(System.currentTimeMillis());
                    store.put(t.getId(), t);
                    return;
                }

                // 🔥 GUARANTEED PARTIAL FILL MODE
                boolean forcePartial =
                        t.getTrader().equalsIgnoreCase("TEST_PARTIAL")
                                || t.getLimitPrice() == -1;

                if (forcePartial || rnd.nextDouble() < 0.25) {
                    // Guaranteed or random partial fill
                    int fillQty = Math.max(1, remaining / 2);
                    t.addFilled(fillQty);
                    t.setState(TradeState.PARTIAL);
                    t.addHistory("PARTIAL_FILL:" + fillQty);

                    fixService.createExecutionReport(t.getId(), execPx, fillQty);
                    store.put(t.getId(), t);

                    scheduler.schedule(() -> internalExecuteWithRetry(t),
                            300 + rnd.nextInt(400),
                            TimeUnit.MILLISECONDS);

                } else {
                    // FULL FILL
                    int fillQty = remaining;
                    t.addFilled(fillQty);
                    t.setState(TradeState.EXECUTED);
                    t.addHistory("EXECUTED:" + fillQty);

                    fixService.createExecutionReport(t.getId(), execPx, fillQty);
                    store.put(t.getId(), t);

                    // Confirmation after delay
                    scheduler.schedule(() -> {
                        if (t.getState() == TradeState.CANCELLED) {
                            store.put(t.getId(), t);
                            return;
                        }

                        if (rnd.nextDouble() < 0.08) {
                            t.setState(TradeState.REJECTED);
                            t.addHistory("CONFIRMATION_FAILED");
                        } else {
                            t.setState(TradeState.CONFIRMED);
                            t.addHistory("CONFIRMED");
                        }

                        t.setExecutionEndTime(System.currentTimeMillis());
                        store.put(t.getId(), t);

                    }, 200 + rnd.nextInt(400), TimeUnit.MILLISECONDS);
                }

                t.setExecutionEndTime(System.currentTimeMillis());
                return;

            } catch (Exception e) {
                t.addHistory("EXCEPTION:" + e.getMessage());
                try {
                    Thread.sleep(backoffBase * attempts);
                } catch (InterruptedException ignored) {}
            }
        }

        // All retries exhausted
        t.setState(TradeState.REJECTED);
        t.addHistory("REJECTED_AFTER_RETRIES");
        t.setExecutionEndTime(System.currentTimeMillis());
        store.put(t.getId(), t);
    }

    public void storeTrade(Trade t) {
        store.put(t.getId(), t);
    }


}
