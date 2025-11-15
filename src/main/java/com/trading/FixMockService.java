package com.trading;

import java.util.concurrent.ConcurrentHashMap;

public class FixMockService {

    private final ConcurrentHashMap<String, String> fixReports = new ConcurrentHashMap<>();

    public void createExecutionReport(String tradeId, double price, int qty) {
        String fix =
                "{ \"MsgType\": \"8\","
                        + "\"tradeId\": \"" + tradeId + "\","
                        + "\"price\": " + price + ","
                        + "\"filledQty\": " + qty + " }";

        fixReports.put(tradeId, fix);
    }

    public String getExecutionReport(String tradeId) {
        return fixReports.get(tradeId);
    }
}
