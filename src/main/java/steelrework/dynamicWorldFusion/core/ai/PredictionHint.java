package steelrework.dynamicWorldFusion.core.ai;

public record PredictionHint(
        String nodeId,
        String playerId,
        String world,
        int predictedChunkX,
        int predictedChunkZ,
        int lookaheadChunks,
        String reason
) {
}
