package steelrework.dynamicWorldFusion.core.chunk;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import steelrework.dynamicWorldFusion.core.protocol.MessageType;
import steelrework.dynamicWorldFusion.core.protocol.WireMessage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ReliableChunkTransferService {
    private final JavaPlugin plugin;
    private final String localNodeId;
    private final ChunkNmsAdapter nmsAdapter;
    private final Consumer<WireMessage> sender;
    private final ConcurrentHashMap<String, PendingChunkFrame> pendingFrames;
    private final ConcurrentHashMap<String, ChunkReassemblyBuffer> inboundStreams;

    private final long retryTimeoutMillis;
    private final int maxRetries;
    private final int maxFramePayloadBytes;
    private final long inboundTtlMillis;

    private BukkitTask retryTask;

    public ReliableChunkTransferService(
            JavaPlugin plugin,
            String localNodeId,
            ChunkNmsAdapter nmsAdapter,
            Consumer<WireMessage> sender,
            long retryTimeoutMillis,
            int maxRetries,
            int maxFramePayloadBytes,
            long inboundTtlMillis
    ) {
        this.plugin = plugin;
        this.localNodeId = localNodeId;
        this.nmsAdapter = nmsAdapter;
        this.sender = sender;
        this.retryTimeoutMillis = retryTimeoutMillis;
        this.maxRetries = maxRetries;
        this.maxFramePayloadBytes = Math.max(512, maxFramePayloadBytes);
        this.inboundTtlMillis = Math.max(5000L, inboundTtlMillis);
        this.pendingFrames = new ConcurrentHashMap<>();
        this.inboundStreams = new ConcurrentHashMap<>();
    }

    public void start() {
        if (retryTask != null) {
            return;
        }
        retryTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::onMaintenanceTick,
                20L,
                20L
        );
    }

    public void stop() {
        if (retryTask != null) {
            retryTask.cancel();
            retryTask = null;
        }
        pendingFrames.clear();
        inboundStreams.clear();
    }

    public void sendChunk(String targetNodeId, String world, int chunkX, int chunkZ) {
        byte[] payload = nmsAdapter.encodeChunk(world, chunkX, chunkZ);
        if (payload.length == 0) {
            plugin.getLogger().warning("Пустой payload чанка, отправка пропущена: " + world + " [" + chunkX + "," + chunkZ + "]");
            return;
        }

        String streamId = UUID.randomUUID().toString();
        String checksum = Checksum.sha256Hex(payload);
        int totalFrames = (int) Math.ceil(payload.length / (double) maxFramePayloadBytes);

        for (int seq = 0; seq < totalFrames; seq++) {
            int start = seq * maxFramePayloadBytes;
            int end = Math.min(payload.length, start + maxFramePayloadBytes);
            byte[] fragment = new byte[end - start];
            System.arraycopy(payload, start, fragment, 0, fragment.length);

            String frameId = streamId + "-" + seq;
            ChunkStreamFrame frame = new ChunkStreamFrame(
                    streamId,
                    frameId,
                    localNodeId,
                    targetNodeId,
                    world,
                    chunkX,
                    chunkZ,
                    seq,
                    totalFrames,
                    checksum,
                    fragment
            );

            PendingChunkFrame pending = new PendingChunkFrame(frame);
            pendingFrames.put(frameId, pending);
            sendFrame(pending);
        }
    }

    public void onFrame(ChunkStreamFrame frame) {
        sender.accept(new WireMessage(
                MessageType.CHUNK_STREAM_ACK,
                Map.of(
                        "frameId", frame.frameId(),
                        "streamId", frame.streamId(),
                        "sourceNodeId", localNodeId,
                        "targetNodeId", frame.sourceNodeId()
                )
        ));

        ChunkReassemblyBuffer buffer = inboundStreams.computeIfAbsent(frame.streamId(), id -> new ChunkReassemblyBuffer(frame));
        boolean complete = buffer.addFrame(frame);
        if (!complete) {
            return;
        }

        inboundStreams.remove(frame.streamId());
        byte[] assembled = buffer.assemble();
        String actualChecksum = Checksum.sha256Hex(assembled);
        if (!actualChecksum.equalsIgnoreCase(buffer.checksumSha256())) {
            plugin.getLogger().warning("Checksum mismatch для stream " + buffer.streamId());
            return;
        }

        nmsAdapter.applyChunkPayload(buffer.world(), buffer.chunkX(), buffer.chunkZ(), assembled);
    }

    public void onAck(Map<String, String> payload) {
        String frameId = payload.get("frameId");
        if (frameId == null) {
            return;
        }
        PendingChunkFrame removed = pendingFrames.remove(frameId);
        if (removed != null) {
            plugin.getLogger().fine("Подтвержден фрейм чанка (ACK): " + frameId);
        }
    }

    private void onMaintenanceTick() {
        retryExpiredFrames();
        pruneInboundStreams();
    }

    private void retryExpiredFrames() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, PendingChunkFrame> entry : pendingFrames.entrySet()) {
            PendingChunkFrame pending = entry.getValue();
            long delta = now - pending.lastSentAtMillis();
            if (pending.lastSentAtMillis() == 0L || delta >= retryTimeoutMillis) {
                if (pending.retryCount() >= maxRetries) {
                    pendingFrames.remove(entry.getKey());
                    plugin.getLogger().warning("Доставка фрейма чанка не удалась после повторов: " + entry.getKey());
                    continue;
                }
                sendFrame(pending);
            }
        }
    }

    private void pruneInboundStreams() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ChunkReassemblyBuffer> entry : inboundStreams.entrySet()) {
            if ((now - entry.getValue().updatedAtMillis()) > inboundTtlMillis) {
                inboundStreams.remove(entry.getKey());
            }
        }
    }

    private void sendFrame(PendingChunkFrame pending) {
        pending.markSent(System.currentTimeMillis());
        sender.accept(new WireMessage(MessageType.CHUNK_STREAM_FRAME, pending.frame().toPayloadMap()));
    }
}

