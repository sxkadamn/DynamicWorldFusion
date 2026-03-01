package steelrework.dynamicWorldFusion.core.chunk;

public final class PendingChunkFrame {
    private final ChunkStreamFrame frame;
    private int retryCount;
    private long lastSentAtMillis;

    public PendingChunkFrame(ChunkStreamFrame frame) {
        this.frame = frame;
        this.retryCount = 0;
        this.lastSentAtMillis = 0L;
    }

    public ChunkStreamFrame frame() {
        return frame;
    }

    public int retryCount() {
        return retryCount;
    }

    public long lastSentAtMillis() {
        return lastSentAtMillis;
    }

    public void markSent(long nowMillis) {
        retryCount++;
        lastSentAtMillis = nowMillis;
    }
}
