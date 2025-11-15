package com.trading.model;

public enum TradeState {
    CREATED,
    PARTIAL,
    EXECUTED,
    PENDING_CONFIRMATION,
    CONFIRMED,
    REJECTED,
    RETRY,
    CANCELLED
}
