package com.trading.model;

public class Instrument {
    private final String isin;
    private final String currency;
    private final double yield;
    private final String tenor;

    public Instrument(String isin, String currency, double yield, String tenor) {
        this.isin = isin;
        this.currency = currency;
        this.yield = yield;
        this.tenor = tenor;
    }

    public String getIsin() { return isin; }
    public String getCurrency() { return currency; }
    public double getYield() { return yield; }
    public String getTenor() { return tenor; }
}
