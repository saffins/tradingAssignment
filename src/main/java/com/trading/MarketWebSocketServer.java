package com.trading;

import com.trading.model.MarketTick;
import com.google.gson.Gson;
import org.java_websocket.WebSocket;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket server that broadcasts market ticks periodically.
 * Listens on port 8090.
 */
public class MarketWebSocketServer extends WebSocketServer {

    private final MarketDataService market;
    private final Gson gson = new Gson();

    public MarketWebSocketServer(int port, MarketDataService market) {
        super(new InetSocketAddress(port));
        this.market = market;
        // schedule broadcaster
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::broadcastTicks, 0, 300, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("WS Open: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("WS Close: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // optional: handle simple subscribe messages from client
        System.out.println("WS Msg from " + conn.getRemoteSocketAddress() + ": " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WS Error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("Market WebSocket started on port " + getPort());
    }

    private void broadcastTicks() {
        try {
            Collection<WebSocket> conns = getConnections();
            if (conns == null || conns.isEmpty()) return;
            // send all ticks as an array
            var isins = market.getAllIsins();
            var ticks = isins.stream().map(market::getLatest).toArray();
            String json = gson.toJson(ticks);
            for (WebSocket c : conns) {
                c.send(json);
            }
        } catch (Exception e) {
            System.err.println("Error broadcasting ticks: " + e.getMessage());
        }
    }
}
