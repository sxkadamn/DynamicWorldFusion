package steelrework.dynamicWorldFusion.core.chunk;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChunkReassemblyBuffer {
    private final String streamId;
    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final int totalFrames;
    private final String checksumSha256;
    private final byte[][] frames;
    private final boolean[] received;
    private final AtomicInteger receivedCount;
    private volatile long updatedAtMillis;

    public ChunkReassemblyBuffer(ChunkStreamFrame firstFrame) {
        this.streamId = firstFrame.streamId();
        this.world = firstFrame.world();
        this.chunkX = firstFrame.chunkX();
        this.chunkZ = firstFrame.chunkZ();
        this.totalFrames = Math.max(1, firstFrame.totalFrames());
        this.checksumSha256 = firstFrame.checksumSha256();
        this.frames = new byte[this.totalFrames][];
        this.received = new boolean[this.totalFrames];
        this.receivedCount = new AtomicInteger(0);
        this.updatedAtMillis = System.currentTimeMillis();
    }

    public synchronized boolean addFrame(ChunkStreamFrame frame) {
        int seq = frame.sequence();
        if (seq < 0 || seq >= totalFrames) {
            return false;
        }
        if (received[seq]) {
            updatedAtMillis = System.currentTimeMillis();
            return isComplete();
        }

        frames[seq] = Arrays.copyOf(frame.payload(), frame.payload().length);
        received[seq] = true;
        receivedCount.incrementAndGet();
        updatedAtMillis = System.currentTimeMillis();
        return isComplete();
    }

    public synchronized byte[] assemble() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < frames.length; i++) {
            if (!received[i] || frames[i] == null) {
                return new byte[0];
            }
            out.writeBytes(frames[i]);
        }
        return out.toByteArray();
    }

    public boolean isComplete() {
        return receivedCount.get() == totalFrames;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }

    public String streamId() {
        return streamId;
    }

    public String world() {
        return world;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public String checksumSha256() {
        return checksumSha256;
    }
}
