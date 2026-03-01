package steelrework.dynamicWorldFusion.core.network;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import steelrework.dynamicWorldFusion.core.ai.AiChunkPredictionService;
import steelrework.dynamicWorldFusion.core.ai.PredictionHint;
import steelrework.dynamicWorldFusion.core.chunk.BukkitSnapshotChunkAdapter;
import steelrework.dynamicWorldFusion.core.chunk.ChunkStreamFrame;
import steelrework.dynamicWorldFusion.core.chunk.ReliableChunkTransferService;
import steelrework.dynamicWorldFusion.core.config.NexusSettings;
import steelrework.dynamicWorldFusion.core.entity.CrossNodeEntityPathService;
import steelrework.dynamicWorldFusion.core.entity.EntityPursuitEvent;
import steelrework.dynamicWorldFusion.core.protocol.MessageType;
import steelrework.dynamicWorldFusion.core.protocol.WireCodec;
import steelrework.dynamicWorldFusion.core.protocol.WireMessage;
import steelrework.dynamicWorldFusion.core.resource.BiomeResourcePackService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NodeTcpClient {
    private final JavaPlugin plugin;
    private final NexusSettings settings;
    private final AtomicBoolean running;
    private final ExecutorService ioExecutor;
    private final ReliableChunkTransferService chunkTransferService;
    private final AiChunkPredictionService aiPredictionService;
    private final CrossNodeEntityPathService entityPathService;
    private final BiomeResourcePackService biomeResourcePackService;

    public NodeTcpClient(JavaPlugin plugin, NexusSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.running = new AtomicBoolean(false);
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "nexus-node-client"));
        this.chunkTransferService = new ReliableChunkTransferService(
                plugin,
                settings.nodeId(),
                new BukkitSnapshotChunkAdapter(plugin),
                this::sendWireMessage,
                settings.chunkRetryTimeoutMillis(),
                settings.chunkMaxRetries(),
                settings.chunkFramePayloadBytes(),
                settings.chunkInboundTtlMillis()
        );
        this.aiPredictionService = new AiChunkPredictionService(
                plugin,
                settings.nodeId(),
                this::onPredictionHint,
                settings.aiLookaheadChunks(),
                settings.aiMinSpeedBlocksPerTick()
        );
        this.entityPathService = new CrossNodeEntityPathService(plugin, settings.nodeId(), this::sendEntityPursuitSync);
        this.biomeResourcePackService = new BiomeResourcePackService(plugin, settings.biomePackUrls());
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        ioExecutor.execute(this::runClientLoop);
        chunkTransferService.start();
        if (settings.aiPredictionEnabled()) {
            aiPredictionService.start();
        }
        if (settings.entityPathEnabled()) {
            entityPathService.start();
        }
        if (settings.dynamicResourcePackEnabled()) {
            biomeResourcePackService.start();
        }
        plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::sendHeartbeatAndLoad,
                settings.heartbeatIntervalTicks(),
                settings.heartbeatIntervalTicks()
        );
    }

    public void stop() {
        running.set(false);
        chunkTransferService.stop();
        ioExecutor.shutdownNow();
    }

    public void sendChunkFrame(String targetNodeId, String world, int chunkX, int chunkZ) {
        chunkTransferService.sendChunk(targetNodeId, world, chunkX, chunkZ);
    }

    private volatile TcpConnection connection;

    private void runClientLoop() {
        while (running.get()) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(settings.masterHost(), settings.masterPort()), 2000);
                this.connection = new TcpConnection(socket);
                plugin.getLogger().info("Подключено к Master " + settings.masterHost() + ":" + settings.masterPort());
                register();

                String line;
                while (running.get() && (line = connection.readLine()) != null) {
                    Optional<WireMessage> message = WireCodec.decode(line);
                    message.ifPresent(this::onMasterMessage);
                }
            } catch (IOException ex) {
                if (running.get()) {
                    plugin.getLogger().warning("Потеряно соединение с Master: " + ex.getMessage());
                }
            } finally {
                closeConnection();
            }

            if (running.get()) {
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void register() throws IOException {
        Map<String, String> payload = new HashMap<>();
        payload.put("nodeId", settings.nodeId());
        payload.put("zoneId", settings.zoneId());
        connection.send(new WireMessage(MessageType.REGISTER_NODE, payload));
    }

    private void sendHeartbeatAndLoad() {
        if (!running.get() || connection == null) {
            return;
        }

        try {
            connection.send(new WireMessage(MessageType.HEARTBEAT, Map.of("nodeId", settings.nodeId())));
            connection.send(new WireMessage(MessageType.LOAD_REPORT, collectLoadPayload()));
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось отправить heartbeat/load: " + ex.getMessage());
        }
    }

    private Map<String, String> collectLoadPayload() {
        int players = Bukkit.getOnlinePlayers().size();
        int entities = Bukkit.getWorlds().stream().mapToInt(world -> world.getEntities().size()).sum();
        double[] tps = Bukkit.getTPS();
        int loadedChunks = Bukkit.getWorlds().stream().mapToInt(world -> world.getLoadedChunks().length).sum();

        Map<String, String> payload = new HashMap<>();
        payload.put("nodeId", settings.nodeId());
        payload.put("tps", String.format(Locale.US, "%.2f", tps.length > 0 ? tps[0] : 20.0D));
        payload.put("entities", String.valueOf(entities));
        payload.put("players", String.valueOf(players));
        payload.put("loadedChunks", String.valueOf(loadedChunks));
        payload.put("playerNames", collectPlayerNames(25));
        payload.put("playerChunks", collectPlayerChunks(25));
        payload.put("playerDetails", collectPlayerDetails(25));
        payload.put("worldChunks", collectWorldChunkStats());
        return payload;
    }

    private String collectPlayerNames(int limit) {
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (var player : Bukkit.getOnlinePlayers()) {
            if (count >= limit) {
                break;
            }
            if (count > 0) {
                out.append(',');
            }
            out.append(player.getName());
            count++;
        }
        return out.toString();
    }

    private String collectPlayerChunks(int limit) {
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (var player : Bukkit.getOnlinePlayers()) {
            if (count >= limit) {
                break;
            }
            if (count > 0) {
                out.append(',');
            }
            var loc = player.getLocation();
            out.append(player.getName())
                    .append('@')
                    .append(loc.getWorld() != null ? loc.getWorld().getName() : "world")
                    .append(':')
                    .append(loc.getChunk().getX())
                    .append(':')
                    .append(loc.getChunk().getZ());
            count++;
        }
        return out.toString();
    }

    private String collectPlayerDetails(int limit) {
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (var player : Bukkit.getOnlinePlayers()) {
            if (count >= limit) {
                break;
            }
            if (count > 0) {
                out.append(',');
            }
            var loc = player.getLocation();
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            int ping = player.getPing();
            out.append(player.getName())
                    .append('@')
                    .append(loc.getWorld() != null ? loc.getWorld().getName() : "world")
                    .append(':')
                    .append(x)
                    .append(':')
                    .append(y)
                    .append(':')
                    .append(z)
                    .append(':')
                    .append(ping)
                    .append(':')
                    .append(loc.getChunk().getX())
                    .append(':')
                    .append(loc.getChunk().getZ());
            count++;
        }
        return out.toString();
    }

    private String collectWorldChunkStats() {
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (var world : Bukkit.getWorlds()) {
            if (count > 0) {
                out.append(',');
            }
            out.append(world.getName())
                    .append(':')
                    .append(world.getLoadedChunks().length);
            count++;
        }
        return out.toString();
    }

    private void onMasterMessage(WireMessage message) {
        if (message.type() == MessageType.ROUTE_UPDATE) {
            plugin.getLogger().info("Обновление маршрута: " + message.payload());
            return;
        }

        if (message.type() == MessageType.CHUNK_STREAM_FRAME) {
            ChunkStreamFrame.fromPayloadMap(message.payload()).ifPresent(chunkTransferService::onFrame);
            return;
        }

        if (message.type() == MessageType.CHUNK_STREAM_ACK) {
            chunkTransferService.onAck(message.payload());
            return;
        }

        if (message.type() == MessageType.ENTITY_PURSUIT_SYNC) {
            EntityPursuitEvent.fromPayloadMap(message.payload()).ifPresent(entityPathService::onRemotePursuit);
        }
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException ignored) {
            }
        }
        connection = null;
    }

    private void sendWireMessage(WireMessage message) {
        if (connection == null) {
            return;
        }
        try {
            connection.send(message);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось отправить сетевое сообщение " + message.type() + ": " + ex.getMessage());
        }
    }

    private void onPredictionHint(PredictionHint hint) {
        Map<String, String> payload = new HashMap<>();
        payload.put("nodeId", hint.nodeId());
        payload.put("playerId", hint.playerId());
        payload.put("world", hint.world());
        payload.put("chunkX", String.valueOf(hint.predictedChunkX()));
        payload.put("chunkZ", String.valueOf(hint.predictedChunkZ()));
        payload.put("lookahead", String.valueOf(hint.lookaheadChunks()));
        payload.put("reason", hint.reason());
        sendWireMessage(new WireMessage(MessageType.CHUNK_PREFETCH_HINT, payload));
    }

    private void sendEntityPursuitSync(EntityPursuitEvent pursuitEvent) {
        sendWireMessage(new WireMessage(MessageType.ENTITY_PURSUIT_SYNC, pursuitEvent.toPayloadMap()));
    }
}

