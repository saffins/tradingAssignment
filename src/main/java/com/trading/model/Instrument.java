package com.trading.model;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Instrument)) return false;
        Instrument that = (Instrument) o;
        return Double.compare(that.yield, yield) == 0 &&
                Objects.equals(isin, that.isin) &&
                Objects.equals(currency, that.currency) &&
                Objects.equals(tenor, that.tenor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isin, currency, yield, tenor);
    }

    @Override
    public String toString() {
        return "Instrument{" +
                "isin='" + isin + '\'' +
                ", currency='" + currency + '\'' +
                ", yield=" + yield +
                ", tenor='" + tenor + '\'' +
                '}';
    }
}
