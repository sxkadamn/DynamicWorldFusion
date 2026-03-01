package steelrework.dynamicWorldFusion.core.chunk;

public interface ChunkNmsAdapter {
    byte[] encodeChunk(String worldName, int chunkX, int chunkZ);

    void applyChunkPayload(String worldName, int chunkX, int chunkZ, byte[] payload);
}
