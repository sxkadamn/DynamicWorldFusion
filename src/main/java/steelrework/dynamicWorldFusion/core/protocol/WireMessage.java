package steelrework.dynamicWorldFusion.core.protocol;

import java.util.Collections;
import java.util.Map;

public record WireMessage(MessageType type, Map<String, String> payload) {
    public WireMessage {
        payload = payload == null ? Collections.emptyMap() : Map.copyOf(payload);
    }
}
