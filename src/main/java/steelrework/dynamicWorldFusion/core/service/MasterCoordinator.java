package steelrework.dynamicWorldFusion.core.service;

import org.bukkit.plugin.java.JavaPlugin;
import steelrework.dynamicWorldFusion.core.config.NexusSettings;
import steelrework.dynamicWorldFusion.core.model.NodeLoad;
import steelrework.dynamicWorldFusion.core.model.NodeSession;
import steelrework.dynamicWorldFusion.core.model.RouteMoveSuggestion;
import steelrework.dynamicWorldFusion.core.network.MasterMessageHandler;
import steelrework.dynamicWorldFusion.core.network.TcpConnection;
import steelrework.dynamicWorldFusion.core.observability.MasterTopologyProvider;
import steelrework.dynamicWorldFusion.core.protocol.MessageType;
import steelrework.dynamicWorldFusion.core.protocol.WireMessage;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MasterCoordinator implements MasterMessageHandler, MasterTopologyProvider {
    private final JavaPlugin plugin;
    private final RoutingTable routingTable;
    private final HotSwapPlanner hotSwapPlanner;
    private final ConcurrentHashMap<TcpConnection, NodeSession> sessionsByConnection;
    private final Deque<Map<String, String>> recentEvents;

    public MasterCoordinator(JavaPlugin plugin, NexusSettings settings) {
        this.plugin = plugin;
        this.routingTable = new RoutingTable();
        this.hotSwapPlanner = new HotSwapPlanner(settings);
        this.sessionsByConnection = new ConcurrentHashMap<>();
        this.recentEvents = new ArrayDeque<>();
    }

    @Override
    public void onMessage(TcpConnection connection, WireMessage message) {
        switch (message.type()) {
            case REGISTER_NODE -> handleRegister(connection, message.payload());
            case HEARTBEAT -> handleHeartbeat(connection);
            case LOAD_REPORT -> handleLoad(connection, message.payload());
            case CHUNK_STREAM_FRAME, CHUNK_STREAM_ACK -> relayChunkMessage(message);
            case ENTITY_PURSUIT_SYNC -> broadcastToAllExcept(connection, message);
            case CHUNK_PREFETCH_HINT -> handlePrefetchHint(message.payload());
            default -> {
            }
        }
    }

    @Override
    public void onDisconnect(TcpConnection connection) {
        NodeSession session = sessionsByConnection.remove(connection);
        if (session != null) {
            routingTable.removeByNode(session.nodeId());
            plugin.getLogger().info("Узел отключился: " + session.nodeId());
        }
    }

    private void handleRegister(TcpConnection connection, Map<String, String> payload) {
        String nodeId = payload.getOrDefault("nodeId", connection.remoteAddress());
        String zoneId = payload.getOrDefault("zoneId", "zone-unknown");
        NodeSession session = new NodeSession(nodeId, zoneId, connection);
        sessionsByConnection.put(connection, session);
        routingTable.assign(zoneId, nodeId);
        plugin.getLogger().info("Зарегистрирован узел " + nodeId + " для зоны " + zoneId);
        sendAck(connection, "зарегистрирован");
    }

    private void handleHeartbeat(TcpConnection connection) {
        NodeSession session = sessionsByConnection.get(connection);
        if (session != null) {
            session.markHeartbeat();
        }
    }

    private void handleLoad(TcpConnection connection, Map<String, String> payload) {
        NodeSession session = sessionsByConnection.get(connection);
        if (session == null) {
            return;
        }

        double tps = parseDouble(payload.get("tps"), 20.0D);
        int entities = parseInt(payload.get("entities"), 0);
        int players = parseInt(payload.get("players"), 0);
        session.updateLoad(new NodeLoad(tps, entities, players, System.currentTimeMillis()));
        session.updateExtras(extractExtras(payload));

        Optional<RouteMoveSuggestion> suggestion = hotSwapPlanner.findSuggestion(sessionsByConnection.values());
        suggestion.ifPresent(this::broadcastSuggestion);
    }

    private void broadcastSuggestion(RouteMoveSuggestion suggestion) {
        recordEvent(
                "ROUTE_UPDATE",
                suggestion.zoneId(),
                suggestion.sourceNodeId(),
                suggestion.targetNodeId(),
                suggestion.reason()
        );
        plugin.getLogger().info("Предложение hot-swap: зона " + suggestion.zoneId()
                + " с " + suggestion.sourceNodeId()
                + " на " + suggestion.targetNodeId()
                + " (причина: " + suggestion.reason() + ")");

        for (NodeSession session : sessionsByConnection.values()) {
            if (!session.nodeId().equals(suggestion.targetNodeId())) {
                continue;
            }
            Map<String, String> payload = new HashMap<>();
            payload.put("zoneId", suggestion.zoneId());
            payload.put("sourceNodeId", suggestion.sourceNodeId());
            payload.put("targetNodeId", suggestion.targetNodeId());
            payload.put("reason", suggestion.reason());

            try {
                session.connection().send(new WireMessage(MessageType.ROUTE_UPDATE, payload));
            } catch (IOException ex) {
                plugin.getLogger().warning("Не удалось отправить обновление маршрута: " + ex.getMessage());
            }
        }
    }

    private void sendAck(TcpConnection connection, String status) {
        try {
            connection.send(new WireMessage(MessageType.ACK, Map.of("status", status)));
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось отправить ACK: " + ex.getMessage());
        }
    }

    private void relayChunkMessage(WireMessage message) {
        String targetNodeId = message.payload().get("targetNodeId");
        if (targetNodeId == null || targetNodeId.isBlank()) {
            return;
        }

        NodeSession targetSession = findByNodeId(targetNodeId);
        if (targetSession == null) {
            plugin.getLogger().warning("Целевой узел для релея чанка не подключен: " + targetNodeId);
            return;
        }

        try {
            targetSession.connection().send(message);
        } catch (IOException ex) {
            plugin.getLogger().warning("Ошибка релея чанка на узел " + targetNodeId + ": " + ex.getMessage());
        }
    }

    private void handlePrefetchHint(Map<String, String> payload) {
        String nodeId = payload.getOrDefault("nodeId", "unknown");
        String world = payload.getOrDefault("world", "world");
        String chunkX = payload.getOrDefault("chunkX", "0");
        String chunkZ = payload.getOrDefault("chunkZ", "0");
        plugin.getLogger().fine("AI-подсказка предзагрузки от " + nodeId + " для " + world + " [" + chunkX + "," + chunkZ + "]");
    }

    private void broadcastToAllExcept(TcpConnection source, WireMessage message) {
        for (NodeSession session : sessionsByConnection.values()) {
            if (session.connection() == source) {
                continue;
            }
            try {
                session.connection().send(message);
            } catch (IOException ex) {
                plugin.getLogger().warning("Ошибка широковещательной отправки " + message.type() + ": " + ex.getMessage());
            }
        }
    }

    private NodeSession findByNodeId(String nodeId) {
        for (NodeSession session : sessionsByConnection.values()) {
            if (session.nodeId().equals(nodeId)) {
                return session;
            }
        }
        return null;
    }

    @Override
    public Map<String, Map<String, String>> nodeTopologySnapshot() {
        Map<String, Map<String, String>> snapshot = new HashMap<>();
        for (NodeSession session : sessionsByConnection.values()) {
            Map<String, String> fields = new HashMap<>();
            fields.put("zoneId", session.zoneId());
            fields.put("tps", String.valueOf(session.latestLoad().tps()));
            fields.put("entities", String.valueOf(session.latestLoad().entities()));
            fields.put("players", String.valueOf(session.latestLoad().players()));
            fields.put("lastHeartbeatMillis", String.valueOf(session.lastHeartbeatMillis()));
            fields.putAll(session.latestExtras());
            snapshot.put(session.nodeId(), fields);
        }
        return snapshot;
    }
    @Override
    public Map<String, String> zoneOwnershipSnapshot() {
        return routingTable.snapshot();
    }

    @Override
    public java.util.List<Map<String, String>> recentEventsSnapshot() {
        synchronized (recentEvents) {
            return new java.util.ArrayList<>(recentEvents);
        }
    }

    private Map<String, String> extractExtras(Map<String, String> payload) {
        Map<String, String> extras = new HashMap<>();
        copyIfPresent(payload, extras, "loadedChunks");
        copyIfPresent(payload, extras, "playerNames");
        copyIfPresent(payload, extras, "playerChunks");
        copyIfPresent(payload, extras, "playerDetails");
        copyIfPresent(payload, extras, "worldChunks");
        return extras;
    }

    private void copyIfPresent(Map<String, String> source, Map<String, String> target, String key) {
        String value = source.get(key);
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    @Override
    public boolean requestZoneMove(String zoneId, String targetNodeId) {
        NodeSession target = findByNodeId(targetNodeId);
        if (target == null) {
            return false;
        }

        String sourceNodeId = routingTable.snapshot().getOrDefault(zoneId, "unknown");
        routingTable.assign(zoneId, targetNodeId);
        broadcastSuggestion(new RouteMoveSuggestion(
                zoneId,
                sourceNodeId,
                targetNodeId,
                "ручное_управление"
        ));
        return true;
    }

    private void recordEvent(String type, String zoneId, String sourceNodeId, String targetNodeId, String reason) {
        Map<String, String> event = new HashMap<>();
        event.put("ts", String.valueOf(System.currentTimeMillis()));
        event.put("type", type);
        event.put("zoneId", zoneId);
        event.put("sourceNodeId", sourceNodeId);
        event.put("targetNodeId", targetNodeId);
        event.put("reason", reason == null ? "" : reason);
        synchronized (recentEvents) {
            recentEvents.addFirst(event);
            while (recentEvents.size() > 50) {
                recentEvents.removeLast();
            }
        }
    }
    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}




