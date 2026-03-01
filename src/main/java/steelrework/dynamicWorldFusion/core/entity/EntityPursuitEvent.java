package steelrework.dynamicWorldFusion.core.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record EntityPursuitEvent(
        String sourceNodeId,
        String world,
        String entityUuid,
        String targetPlayerUuid,
        double x,
        double y,
        double z
) {
    public Map<String, String> toPayloadMap() {
        Map<String, String> payload = new HashMap<>();
        payload.put("sourceNodeId", sourceNodeId);
        payload.put("world", world);
        payload.put("entityUuid", entityUuid);
        payload.put("targetPlayerUuid", targetPlayerUuid);
        payload.put("x", String.valueOf(x));
        payload.put("y", String.valueOf(y));
        payload.put("z", String.valueOf(z));
        return payload;
    }

    public static Optional<EntityPursuitEvent> fromPayloadMap(Map<String, String> payload) {
        try {
            return Optional.of(new EntityPursuitEvent(
                    payload.getOrDefault("sourceNodeId", ""),
                    payload.getOrDefault("world", "world"),
                    payload.getOrDefault("entityUuid", ""),
                    payload.getOrDefault("targetPlayerUuid", ""),
                    Double.parseDouble(payload.getOrDefault("x", "0")),
                    Double.parseDouble(payload.getOrDefault("y", "0")),
                    Double.parseDouble(payload.getOrDefault("z", "0"))
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
