package steelrework.dynamicWorldFusion.core.model;

public record NodeLoad(
        double tps,
        int entities,
        int players,
        long capturedAtMillis
) {
}
