package steelrework.dynamicWorldFusion.core.protocol;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class WireCodec {

    private WireCodec() {
    }

    public static String encode(WireMessage message) {
        StringBuilder out = new StringBuilder(message.type().name());
        out.append('|');
        boolean first = true;
        for (Map.Entry<String, String> entry : message.payload().entrySet()) {
            if (!first) {
                out.append(';');
            }
            out.append(sanitize(entry.getKey())).append('=').append(sanitize(entry.getValue()));
            first = false;
        }
        return out.toString();
    }

    public static Optional<WireMessage> decode(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        String[] parts = line.split("\\|", 2);
        MessageType type;
        try {
            type = MessageType.valueOf(parts[0].trim());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        Map<String, String> payload = new LinkedHashMap<>();
        if (parts.length == 2 && !parts[1].isBlank()) {
            for (String pair : parts[1].split(";")) {
                if (pair.isBlank()) {
                    continue;
                }
                String[] kv = pair.split("=", 2);
                String key = kv[0].trim();
                if (key.isEmpty()) {
                    continue;
                }
                String value = kv.length > 1 ? kv[1].trim() : "";
                payload.put(key, value);
            }
        }

        return Optional.of(new WireMessage(type, payload));
    }

    private static String sanitize(String raw) {
        return raw == null ? "" : raw.replace("|", "_").replace(";", "_").replace("=", "_");
    }
}
