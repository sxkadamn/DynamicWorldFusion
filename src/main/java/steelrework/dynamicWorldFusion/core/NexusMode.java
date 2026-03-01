package steelrework.dynamicWorldFusion.core;

public enum NexusMode {
    MASTER,
    NODE;

    public static NexusMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NODE;
        }
        try {
            return NexusMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return NODE;
        }
    }
}
