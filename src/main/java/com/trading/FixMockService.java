package com.trading;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class FixMockService {

    private final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

    // create a simple JSON-ish string (or valid JSON) for exec report
    public String createExecutionReport(String tradeId, double lastPx, int lastQty) {
        String execId = UUID.randomUUID().toString();
        String json = String.format("{\"MsgType\":\"8\",\"ExecType\":\"0\",\"ExecID\":\"%s\",\"OrderID\":\"%s\",\"LastPx\":%s,\"LastQty\":%d}",
                execId, tradeId, lastPx, lastQty);
        map.put(tradeId, json);
        return json;
    }

    public String getExecutionReport(String tradeId) {
        return map.get(tradeId);
    }
}
