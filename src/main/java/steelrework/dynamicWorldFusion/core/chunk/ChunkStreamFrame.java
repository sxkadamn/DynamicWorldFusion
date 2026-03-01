package steelrework.dynamicWorldFusion.core.chunk;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record ChunkStreamFrame(
        String streamId,
        String frameId,
        String sourceNodeId,
        String targetNodeId,
        String world,
        int chunkX,
        int chunkZ,
        int sequence,
        int totalFrames,
        String checksumSha256,
        byte[] payload
) {
    public Map<String, String> toPayloadMap() {
        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("streamId", streamId);
        payloadMap.put("frameId", frameId);
        payloadMap.put("sourceNodeId", sourceNodeId);
        payloadMap.put("targetNodeId", targetNodeId);
        payloadMap.put("world", world);
        payloadMap.put("chunkX", String.valueOf(chunkX));
        payloadMap.put("chunkZ", String.valueOf(chunkZ));
        payloadMap.put("seq", String.valueOf(sequence));
        payloadMap.put("total", String.valueOf(totalFrames));
        payloadMap.put("checksum", checksumSha256);
        payloadMap.put("payload", Base64.getUrlEncoder().withoutPadding().encodeToString(payload));
        return payloadMap;
    }

    public static Optional<ChunkStreamFrame> fromPayloadMap(Map<String, String> payloadMap) {
        try {
            return Optional.of(new ChunkStreamFrame(
                    payloadMap.getOrDefault("streamId", payloadMap.getOrDefault("frameId", "")),
                    payloadMap.getOrDefault("frameId", ""),
                    payloadMap.getOrDefault("sourceNodeId", ""),
                    payloadMap.getOrDefault("targetNodeId", ""),
                    payloadMap.getOrDefault("world", "world"),
                    Integer.parseInt(payloadMap.getOrDefault("chunkX", "0")),
                    Integer.parseInt(payloadMap.getOrDefault("chunkZ", "0")),
                    Integer.parseInt(payloadMap.getOrDefault("seq", "0")),
                    Integer.parseInt(payloadMap.getOrDefault("total", "1")),
                    payloadMap.getOrDefault("checksum", ""),
                    decodePayload(payloadMap.getOrDefault("payload", ""))
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static byte[] decodePayload(String raw) {
        if (raw == null || raw.isBlank()) {
            return new byte[0];
        }
        return Base64.getUrlDecoder().decode(raw);
    }
}
