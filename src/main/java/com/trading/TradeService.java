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

    public TradeService(MarketDataService market, InstrumentService instrumentService,
                        CreditExposureService exposureService, FixMockService fixService) {
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
            TradeState s = t.getState();
            if (s == TradeState.CONFIRMED || s == TradeState.REJECTED) return false;
            t.setState(TradeState.CANCELLED);
            t.addHistory("CANCELLED");
            // persist back to store just to be explicit
            store.put(t.getId(), t);
            return true;
        }
    }

    // submit trade for async execution (robot tests call create then this runs)
    public void executeAsync(Trade t) {
        // schedule immediate execution in background
        exec.submit(() -> internalExecuteWithRetry(t));
    }

    // public wrapper (MainServer: use this)
    public void submitForExecution(Trade t) {
        executeAsync(t);
    }

    private void internalExecuteWithRetry(Trade t) {
        // ensure only run once concurrently per invocation (local guard)
        AtomicBoolean attempted = new AtomicBoolean(false);
        if (!attempted.compareAndSet(false, true)) return;

        int attempts = 0;
        int maxAttempts = 5;
        long backoffBase = 400; // ms

        while (attempts < maxAttempts) {
            attempts++;
            t.incrementRetry();
            t.setExecutionStartTime(System.currentTimeMillis());
            boolean success = false;
            try {
                // check cancellation
                if (t.getState() == TradeState.CANCELLED) {
                    store.put(t.getId(), t);
                    return;
                }

                // simulate possible transient network/execution failures
                if (rnd.nextDouble() < 0.12) { // 12% transient failure
                    t.setState(TradeState.RETRY);
                    t.addHistory("TRANSIENT_FAILURE");
                    store.put(t.getId(), t);
                    Thread.sleep(100 + rnd.nextInt(300));
                    throw new RuntimeException("Transient execution failure (simulated)");
                }

                // fetch market price (use getter)
                MarketTick mt = market.getLatest(t.getIsin());
                double marketPrice = (mt != null) ? mt.getPrice() : 0.0;

                // execution price logic: if limit matches, use limit; else take market.
                double execPx = (t.getLimitPrice() > 0) ? t.getLimitPrice() : marketPrice;
                t.setExecutionPrice(execPx);

                // check price deviation against market average
                double avg = market.getAveragePrice(t.getIsin(), 5);
                if (avg > 0) {
                    double diff = Math.abs(execPx - avg) / avg;
                    if (diff > marketTolerance) {
                        // Reject trade for deviation
                        t.setState(TradeState.REJECTED);
                        t.addHistory("REJECTED_PRICE_DEVIATION");
                        t.setExecutionEndTime(System.currentTimeMillis());
                        // create fix report even for rejected (useful)
                        fixService.createExecutionReport(t.getId(), execPx, 0);
                        store.put(t.getId(), t);
                        return;
                    }
                }

                // simulate partial fills: some trades fill partially
                int remaining = t.getQuantity() - t.getFilled();
                int fillQty;
                if (remaining <= 0) {
                    // already filled
                    t.setState(TradeState.CONFIRMED);
                    t.setExecutionEndTime(System.currentTimeMillis());
                    store.put(t.getId(), t);
                    return;
                }

                if (rnd.nextDouble() < 0.25) { // 25% partial fill
                    fillQty = Math.max(1, remaining / (2 + rnd.nextInt(3)));
                    t.addFilled(fillQty);
                    t.setExecutionPrice(execPx);
                    t.setState(TradeState.PARTIAL);
                    t.addHistory("PARTIAL_FILL:" + fillQty);
                    // create partial fix
                    fixService.createExecutionReport(t.getId(), execPx, fillQty);
                    store.put(t.getId(), t);
                    // schedule follow-up execution to fill rest
                    scheduler.schedule(() -> internalExecuteWithRetry(t), 300 + rnd.nextInt(400), TimeUnit.MILLISECONDS);
                } else {
                    // full fill
                    fillQty = remaining;
                    t.addFilled(fillQty);
                    t.setExecutionPrice(execPx);
                    t.setState(TradeState.EXECUTED);
                    t.addHistory("EXECUTED:" + fillQty);
                    // create fix report
                    fixService.createExecutionReport(t.getId(), execPx, fillQty);
                    // go to pending confirmation
                    t.setState(TradeState.PENDING_CONFIRMATION);
                    store.put(t.getId(), t);

                    // simulate confirmation with a small delay, possibly failing randomly
                    scheduler.schedule(() -> {
                        // if cancelled meanwhile, leave CANCELLED
                        if (t.getState() == TradeState.CANCELLED) {
                            store.put(t.getId(), t);
                            return;
                        }
                        if (rnd.nextDouble() < 0.08) { // 8% chance to fail confirmation
                            t.setState(TradeState.REJECTED);
                            t.addHistory("CONFIRMATION_FAILED");
                            t.setExecutionEndTime(System.currentTimeMillis());
                        } else {
                            t.setState(TradeState.CONFIRMED);
                            t.addHistory("CONFIRMED");
                            t.setExecutionEndTime(System.currentTimeMillis());
                            // If you have exposure update logic, call it here (optional)
                            // exposureService.applyConfirmedTrade(t.getTrader(), t.getFilled(), t.getExecutionPrice());
                        }
                        store.put(t.getId(), t);
                    }, 200 + rnd.nextInt(400), TimeUnit.MILLISECONDS);
                }

                success = true;
                return;

            } catch (Exception e) {
                // transient error -> backoff and retry loop
                t.addHistory("EXCEPTION:" + e.getMessage());
                try {
                    Thread.sleep(backoffBase * attempts);
                } catch (InterruptedException ignored) {}
                // continue loop to retry
            } finally {
                t.setExecutionEndTime(System.currentTimeMillis());
                store.put(t.getId(), t);
            }
        }

        // if we reach here, attempts exhausted
        t.setState(TradeState.REJECTED);
        t.addHistory("REJECTED_AFTER_RETRIES");
        t.setExecutionEndTime(System.currentTimeMillis());
        store.put(t.getId(), t);
    }

    // persist trade into store (public API)
    public void storeTrade(Trade t) {
        store.put(t.getId(), t);
    }
}
