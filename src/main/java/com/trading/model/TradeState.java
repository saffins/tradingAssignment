package com.trading.model;

public enum TradeState {
    CREATED,
    EXECUTED,
    PARTIAL,
    PENDING_CONFIRMATION,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    RETRY,
    DUPLICATE
}
