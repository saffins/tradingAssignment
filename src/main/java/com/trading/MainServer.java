package com.trading;

import com.google.gson.Gson;
import com.trading.model.MarketTick;
import com.trading.model.Trade;

import com.sun.net.httpserver.HttpServer;
import com.trading.model.TradeState;

import java.net.InetSocketAddress;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MainServer {

    // Dedup map for duplicate trade prevention
    private static final ConcurrentHashMap<String, String> dedupMap = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {

        int port = 8080;

        // Initialize services
        InstrumentService instrumentService = new InstrumentService();
        MarketDataService market = new MarketDataService();
        CreditExposureService exposureService = new CreditExposureService();   // <-- correct service
        FixMockService fixService = new FixMockService();
        TradeService tradeService = new TradeService(market, instrumentService, exposureService, fixService);

        // Start mock market feed
        market.start();

        // Start WebSocket feed
        MarketWebSocketServer ws = new MarketWebSocketServer(8090, market);
        ws.start();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        Gson gson = new Gson();

        //----------------------------------------------------------------------
        // 1️⃣ CREATE TRADE
        //----------------------------------------------------------------------
        server.createContext("/api/trades/create", exchange -> {
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map req = gson.fromJson(body, Map.class);

            String isin = (String) req.get("isin");
            String trader = (String) req.get("trader");
            double limit = Double.parseDouble(req.get("limitPrice").toString());
            int qty = ((Double) req.get("quantity")).intValue();

            // Duplicate check
            String payloadHash = Integer.toString(Objects.hash(isin, trader, qty, limit));
            if (dedupMap.containsKey(payloadHash)) {
                String existing = dedupMap.get(payloadHash);
                sendJson(exchange, gson.toJson(Map.of("tradeId", existing, "state", "DUPLICATE")), 200);
                return;
            }

            // Validate ISIN
            var instrument = instrumentService.fetchByIsin(isin);
            if (instrument.isEmpty()) {
                sendJson(exchange, gson.toJson(Map.of("error", "Invalid ISIN")), 400);
                return;
            }
            String tradeId = UUID.randomUUID().toString();

            // Exposure check BEFORE trade creation
            boolean allowed = exposureService.isAllowed(trader, qty, limit);

            if (!allowed) {
                Trade rejected = new Trade(tradeId, isin, trader, qty, limit);
                rejected.setState(TradeState.REJECTED);
                tradeService.storeTrade(rejected);

                sendJson(exchange, gson.toJson(
                        Map.of(
                                "tradeId", tradeId,
                                "state", "REJECTED",
                                "reason", "Exposure breach"
                        )), 200);
                return;
            }


            // Create Trade
             tradeId = UUID.randomUUID().toString();
            Trade t = tradeService.createTrade(tradeId, isin, trader, qty, limit);

            // Dedup store
            dedupMap.put(payloadHash, tradeId);

            // Execute async (mock)
            tradeService.submitForExecution(t);

            sendJson(exchange, gson.toJson(Map.of("tradeId", tradeId, "state", t.getState().name())), 200);
        });

        //----------------------------------------------------------------------
        // 2️⃣ MARKET PRICE
        //----------------------------------------------------------------------
        server.createContext("/api/trades/market", exchange -> {
            var query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("isin=")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            String isin = query.substring("isin=".length());
            MarketTick tick = market.getLatest(isin);
            sendJson(exchange, gson.toJson(tick), 200);
        });

        //----------------------------------------------------------------------
        // 3️⃣ EXPOSURE CHECK — used by Robot keyword Validate Trader Exposure
        //----------------------------------------------------------------------
        server.createContext("/api/exposure", exchange -> {
            try {
                var query = exchange.getRequestURI().getQuery();

                if (query == null || !query.startsWith("trader=")) {
                    sendJson(exchange, gson.toJson(Map.of("error", "Missing trader parameter")), 400);
                    return;
                }

                String trader = query.substring("trader=".length());

                Map<String, Object> resp = Map.of(
                        "trader", trader,
                        "currentExposure", exposureService.getExposure(trader),
                        "limit", exposureService.getLimit(trader),
                        "allowed", exposureService.isAllowed(trader, 0, 0)
                );

                sendJson(exchange, gson.toJson(resp), 200);

            } catch (Exception e) {
                sendJson(exchange, gson.toJson(Map.of("error", e.getMessage())), 500);
            }
        });

        //----------------------------------------------------------------------
        // 4️⃣ GET TRADE BY ID
        //----------------------------------------------------------------------
        server.createContext("/api/trades/get", exchange -> {
            var query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("id=")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            String id = query.substring("id=".length());
            var opt = tradeService.find(id);
            Object responseObj = opt.isPresent() ? opt.get() : Map.of("error", "not found");
            sendJson(exchange, gson.toJson(responseObj), 200);
        });

        //----------------------------------------------------------------------
        // 5️⃣ GET ALL TRADES (Dashboard)
        //----------------------------------------------------------------------
        server.createContext("/api/trades/all", exchange -> {
            Collection<Trade> all = tradeService.getAllTrades();
            sendJson(exchange, gson.toJson(all), 200);
        });

        //----------------------------------------------------------------------
        // 6️⃣ CANCEL TRADE
        //----------------------------------------------------------------------
        server.createContext("/api/trades/cancel", exchange -> {
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map req = gson.fromJson(body, Map.class);
            String id = (String) req.get("id");
            boolean ok = tradeService.cancel(id);
            sendJson(exchange, gson.toJson(Map.of("cancelled", ok)), 200);
        });

        //----------------------------------------------------------------------
        // 7️⃣ MARKET AVERAGE
        //----------------------------------------------------------------------
        server.createContext("/api/trades/market/average", exchange -> {
            var query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("isin=")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            String isin = query.substring("isin=".length());
            double avg = market.getAveragePrice(isin, 5);
            sendJson(exchange, gson.toJson(Map.of("isin", isin, "average", avg)), 200);
        });

        //----------------------------------------------------------------------
        // 8️⃣ FIX MOCK ENDPOINT
        //----------------------------------------------------------------------
        server.createContext("/api/fix/execution", exchange -> {
            var query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("tradeId=")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            String id = query.substring("tradeId=".length());
            String fix = fixService.getExecutionReport(id);

            if (fix == null)
                sendJson(exchange, gson.toJson(Map.of("error", "no fix")), 404);
            else
                sendJson(exchange, fix, 200);
        });

        //----------------------------------------------------------------------
        // 9️⃣ DASHBOARD HTML
        //----------------------------------------------------------------------
        server.createContext("/dashboard", exchange -> {
            String html = dashboardHtml();
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, html.getBytes().length);
            exchange.getResponseBody().write(html.getBytes());
            exchange.getResponseBody().close();
        });

        //----------------------------------------------------------------------
        // START SERVER
        //----------------------------------------------------------------------
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();

        System.out.println("Server running → http://localhost:" + port);
        System.out.println("WebSocket running → ws://localhost:8090");
        System.out.println("Dashboard → http://localhost:8080/dashboard");
    }


    private static void sendJson(com.sun.net.httpserver.HttpExchange ex, String json, int status) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, json.getBytes().length);
        ex.getResponseBody().write(json.getBytes());
        ex.getResponseBody().close();
    }
    private static String dashboardHtml() {
        String html =
                "<!doctype html>\n" +
                        "<html>\n" +
                        "<head><meta charset=\"utf-8\"/><title>Trading Dashboard</title></head>\n" +
                        "<body>\n" +
                        "<h2>Trading Dashboard</h2>\n" +
                        "<div>\n" +
                        "  <button onclick=\"refreshTrades()\">Refresh trades (HTTP)</button>\n" +
                        "  <pre id=\"trades\">No data</pre>\n" +
                        "</div>\n" +
                        "<div>\n" +
                        "  <h3>WebSocket Market Stream (port 8090)</h3>\n" +
                        "  <pre id=\"ws\">No WS messages</pre>\n" +
                        "</div>\n" +
                        "<script>\n" +
                        "  function refreshTrades(){\n" +
                        "    fetch('/api/trades/all').then(r=>r.json()).then(d=>{\n" +
                        "      document.getElementById('trades').textContent = JSON.stringify(d, null, 2);\n" +
                        "    });\n" +
                        "  }\n" +
                        "  refreshTrades();\n" +
                        "  const ws = new WebSocket('ws://localhost:8090');\n" +
                        "  ws.onopen = ()=> console.log('ws open');\n" +
                        "  ws.onmessage = (m)=> {\n" +
                        "    document.getElementById('ws').textContent = m.data;\n" +
                        "    refreshTrades();\n" +
                        "  };\n" +
                        "  ws.onerror = (e)=> console.error(e);\n" +
                        "</script>\n" +
                        "</body>\n" +
                        "</html>";
        return html;
    }

}
