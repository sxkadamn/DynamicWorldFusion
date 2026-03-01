package steelrework.dynamicWorldFusion.core.model;

public record RouteMoveSuggestion(
        String zoneId,
        String sourceNodeId,
        String targetNodeId,
        String reason
) {
}
