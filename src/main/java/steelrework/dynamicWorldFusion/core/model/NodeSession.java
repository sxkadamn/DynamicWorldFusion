package steelrework.dynamicWorldFusion.core.model;

import steelrework.dynamicWorldFusion.core.network.TcpConnection;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

public final class NodeSession {
    private final String nodeId;
    private final String zoneId;
    private final TcpConnection connection;
    private final AtomicLong lastHeartbeatMillis;
    private final AtomicReference<NodeLoad> latestLoad;
    private final AtomicReference<Map<String, String>> latestExtras;

    public NodeSession(String nodeId, String zoneId, TcpConnection connection) {
        this.nodeId = nodeId;
        this.zoneId = zoneId;
        this.connection = connection;
        this.lastHeartbeatMillis = new AtomicLong(System.currentTimeMillis());
        this.latestLoad = new AtomicReference<>(new NodeLoad(20.0D, 0, 0, System.currentTimeMillis()));
        this.latestExtras = new AtomicReference<>(Map.of());
    }

    public String nodeId() {
        return nodeId;
    }

    public String zoneId() {
        return zoneId;
    }

    public TcpConnection connection() {
        return connection;
    }

    public long lastHeartbeatMillis() {
        return lastHeartbeatMillis.get();
    }

    public void markHeartbeat() {
        lastHeartbeatMillis.set(System.currentTimeMillis());
    }

    public NodeLoad latestLoad() {
        return latestLoad.get();
    }

    public void updateLoad(NodeLoad load) {
        latestLoad.set(load);
    }

    public void updateExtras(Map<String, String> extras) {
        latestExtras.set(extras == null ? Map.of() : Map.copyOf(extras));
    }

    public Map<String, String> latestExtras() {
        return latestExtras.get();
    }
}
