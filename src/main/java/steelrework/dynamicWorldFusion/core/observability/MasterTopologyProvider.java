package steelrework.dynamicWorldFusion.core.observability;

import java.util.Map;
import java.util.List;

public interface MasterTopologyProvider {
    Map<String, Map<String, String>> nodeTopologySnapshot();

    Map<String, String> zoneOwnershipSnapshot();

    List<Map<String, String>> recentEventsSnapshot();

    boolean requestZoneMove(String zoneId, String targetNodeId);
}
