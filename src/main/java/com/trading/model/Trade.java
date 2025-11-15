package com.trading.model;

import java.util.ArrayList;
import java.util.List;

public class Trade {

    private final String id;
    private final String isin;
    private final String trader;
    private final int quantity;
    private int filled;
    private final double limitPrice;

    private double executionPrice;
    private TradeState state;
    private final List<String> history = new ArrayList<>();

    private int retryCount = 0;
    private long executionStartTime = 0;
    private long executionEndTime = 0;

    public Trade(String id, String isin, String trader, int quantity, double limitPrice) {
        this.id = id;
        this.isin = isin;
        this.trader = trader;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.filled = 0;
        this.state = TradeState.CREATED;
    }

    // Basic getters
    public String getId() { return id; }
    public String getIsin() { return isin; }
    public String getTrader() { return trader; }
    public int getQuantity() { return quantity; }
    public int getFilled() { return filled; }
    public double getLimitPrice() { return limitPrice; }
    public double getExecutionPrice() { return executionPrice; }
    public TradeState getState() { return state; }
    public List<String> getHistory() { return new ArrayList<>(history); }

    // Basic setters / mutators used by TradeService
    public void setExecutionPrice(double price) { this.executionPrice = price; }
    public void setState(TradeState s) { this.state = s; }

    // record lifecycle events — MAKE THIS PUBLIC
    public void addHistory(String entry) {
        if (entry != null) history.add(entry);
    }

    // filled quantity helpers
    public void addFilled(int qty) {
        if (qty <= 0) return;
        this.filled = Math.min(this.quantity, this.filled + qty);
    }

    // retry counter helpers
    public void incrementRetry() { retryCount++; }
    public int getRetryCount() { return retryCount; }

    // execution timing
    public void setExecutionStartTime(long ts) { this.executionStartTime = ts; }
    public long getExecutionStartTime() { return executionStartTime; }
    public void setExecutionEndTime(long ts) { this.executionEndTime = ts; }
    public long getExecutionEndTime() { return executionEndTime; }

    // convenience
    @Override
    public String toString() {
        return "Trade{" +
                "id='" + id + '\'' +
                ", isin='" + isin + '\'' +
                ", trader='" + trader + '\'' +
                ", quantity=" + quantity +
                ", filled=" + filled +
                ", limitPrice=" + limitPrice +
                ", executionPrice=" + executionPrice +
                ", state=" + state +
                ", history=" + history +
                ", retryCount=" + retryCount +
                '}';
    }
}
