package com.trading.model;

import java.util.ArrayList;
import java.util.List;

public class Trade {

    private final String id;
    private final String isin;
    private final String trader;
    private final int quantity;

    private double limitPrice;
    private double executionPrice;

    private int filled = 0;
    private int retryCount = 0;

    private long executionStartTime;
    private long executionEndTime;

    private TradeState state = TradeState.CREATED;
    private final List<String> history = new ArrayList<>();

    public Trade(String id, String isin, String trader, int quantity, double limitPrice) {
        this.id = id;
        this.isin = isin;
        this.trader = trader;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        addHistory("CREATED");
    }

    // getters
    public String getId() { return id; }
    public String getIsin() { return isin; }
    public String getTrader() { return trader; }
    public int getQuantity() { return quantity; }
    public double getLimitPrice() { return limitPrice; }
    public double getExecutionPrice() { return executionPrice; }
    public int getFilled() { return filled; }
    public int getRetryCount() { return retryCount; }

    public long getExecutionStartTime() { return executionStartTime; }
    public long getExecutionEndTime() { return executionEndTime; }

    public TradeState getState() { return state; }
    public List<String> getHistory() { return history; }

    // setters
    public void setState(TradeState state) {
        this.state = state;
        addHistory(state.name());
    }

    public void setExecutionPrice(double px) { this.executionPrice = px; }
    public void setExecutionStartTime(long t) { this.executionStartTime = t; }
    public void setExecutionEndTime(long t) { this.executionEndTime = t; }

    public void incrementRetry() { retryCount++; }

    public void addFilled(int qty) { this.filled += qty; }

    public void addHistory(String h) {
        history.add(h);
    }
}
