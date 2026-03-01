package steelrework.dynamicWorldFusion.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import steelrework.dynamicWorldFusion.core.NexusMode;

import java.util.HashMap;
import java.util.Map;

public record NexusSettings(
        NexusMode mode,
        String nodeId,
        String zoneId,
        String masterBindHost,
        int masterBindPort,
        String masterHost,
        int masterPort,
        int heartbeatIntervalTicks,
        double overloadTpsThreshold,
        int overloadEntitiesThreshold,
        long chunkRetryTimeoutMillis,
        int chunkMaxRetries,
        int chunkFramePayloadBytes,
        long chunkInboundTtlMillis,
        boolean aiPredictionEnabled,
        int aiLookaheadChunks,
        double aiMinSpeedBlocksPerTick,
        boolean entityPathEnabled,
        boolean dynamicResourcePackEnabled,
        Map<String, String> biomePackUrls,
        boolean observabilityApiEnabled,
        String observabilityBindHost,
        int observabilityBindPort,
        boolean observabilityWebsocketEnabled,
        String observabilityWebsocketHost,
        int observabilityWebsocketPort,
        int observabilityStreamIntervalTicks
) {

    public static NexusSettings from(FileConfiguration cfg) {
        return new NexusSettings(
                NexusMode.parse(cfg.getString("nexus.mode", "node")),
                cfg.getString("nexus.node.id", "node-1"),
                cfg.getString("nexus.node.zone", "zone-a"),
                cfg.getString("nexus.master.bind-host", "0.0.0.0"),
                cfg.getInt("nexus.master.bind-port", 25590),
                cfg.getString("nexus.master.host", "127.0.0.1"),
                cfg.getInt("nexus.master.port", 25590),
                cfg.getInt("nexus.heartbeat.interval-ticks", 20),
                cfg.getDouble("nexus.load.overload-tps-threshold", 17.0D),
                cfg.getInt("nexus.load.overload-entities-threshold", 300),
                cfg.getLong("nexus.chunk.retry-timeout-millis", 1200L),
                cfg.getInt("nexus.chunk.max-retries", 3),
                cfg.getInt("nexus.chunk.frame-payload-bytes", 8192),
                cfg.getLong("nexus.chunk.inbound-ttl-millis", 30000L),
                cfg.getBoolean("nexus.ai.enabled", true),
                cfg.getInt("nexus.ai.lookahead-chunks", 500),
                cfg.getDouble("nexus.ai.min-speed-blocks-per-tick", 0.35D),
                cfg.getBoolean("nexus.entity-path.enabled", true),
                cfg.getBoolean("nexus.resource-pack.enabled", true),
                readPackMappings(cfg.getConfigurationSection("nexus.resource-pack.biome-prefix-to-url")),
                cfg.getBoolean("nexus.observability.enabled", true),
                cfg.getString("nexus.observability.bind-host", "0.0.0.0"),
                cfg.getInt("nexus.observability.bind-port", 8099),
                cfg.getBoolean("nexus.observability.websocket.enabled", true),
                cfg.getString("nexus.observability.websocket.bind-host", "0.0.0.0"),
                cfg.getInt("nexus.observability.websocket.bind-port", 8100),
                cfg.getInt("nexus.observability.websocket.stream-interval-ticks", 20)
        );
    }

    private static Map<String, String> readPackMappings(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, String> mappings = new HashMap<>();
        for (String key : section.getKeys(false)) {
            mappings.put(key, section.getString(key, ""));
        }
        return Map.copyOf(mappings);
    }
}
