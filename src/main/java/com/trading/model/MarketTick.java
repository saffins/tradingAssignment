package com.trading.model;

public class MarketTick {

    private final String isin;
    private final double price;
    private final long timestamp;

    public MarketTick(String isin, double price, long timestamp) {
        this.isin = isin;
        this.price = price;
        this.timestamp = timestamp;
    }

    public String getIsin() { return isin; }
    public double getPrice() { return price; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "MarketTick{" +
                "isin='" + isin + '\'' +
                ", price=" + price +
                ", timestamp=" + timestamp +
                '}';
    }
}
