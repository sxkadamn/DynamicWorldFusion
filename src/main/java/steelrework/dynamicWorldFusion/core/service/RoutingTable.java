package steelrework.dynamicWorldFusion.core.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RoutingTable {
    private final ConcurrentHashMap<String, String> zoneToNode = new ConcurrentHashMap<>();

    public void assign(String zoneId, String nodeId) {
        zoneToNode.put(zoneId, nodeId);
    }

    public void removeByNode(String nodeId) {
        zoneToNode.entrySet().removeIf(entry -> entry.getValue().equals(nodeId));
    }

    public Map<String, String> snapshot() {
        return Map.copyOf(zoneToNode);
    }
}
