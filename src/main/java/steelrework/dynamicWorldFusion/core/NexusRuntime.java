package steelrework.dynamicWorldFusion.core;

import org.bukkit.plugin.java.JavaPlugin;
import steelrework.dynamicWorldFusion.core.command.NexusChunkCommand;
import steelrework.dynamicWorldFusion.core.config.NexusSettings;
import steelrework.dynamicWorldFusion.core.network.MasterTcpServer;
import steelrework.dynamicWorldFusion.core.network.NodeTcpClient;
import steelrework.dynamicWorldFusion.core.observability.MasterLiveWebSocketServer;
import steelrework.dynamicWorldFusion.core.observability.MasterObservabilityHttpServer;
import steelrework.dynamicWorldFusion.core.service.MasterCoordinator;

public final class NexusRuntime {

    private final JavaPlugin plugin;
    private final NexusSettings settings;

    private MasterTcpServer masterServer;
    private MasterObservabilityHttpServer observabilityHttpServer;
    private MasterLiveWebSocketServer liveWebSocketServer;
    private NodeTcpClient nodeClient;

    public NexusRuntime(JavaPlugin plugin) {
        this.plugin = plugin;
        this.settings = NexusSettings.from(plugin.getConfig());
    }

    public void start() {
        plugin.getLogger().info("Режим Nexus: " + settings.mode());
        if (settings.mode() == NexusMode.MASTER) {
            startMaster();
            return;
        }
        startNode();
    }

    public void stop() {
        if (nodeClient != null) {
            nodeClient.stop();
        }
        if (masterServer != null) {
            masterServer.stop();
        }
        if (observabilityHttpServer != null) {
            observabilityHttpServer.stop();
        }
        if (liveWebSocketServer != null) {
            liveWebSocketServer.stop();
        }
    }

    private void startMaster() {
        MasterCoordinator coordinator = new MasterCoordinator(plugin, settings);
        this.masterServer = new MasterTcpServer(
                plugin,
                settings.masterBindHost(),
                settings.masterBindPort(),
                coordinator
        );
        this.masterServer.start();
        if (settings.observabilityApiEnabled()) {
            this.observabilityHttpServer = new MasterObservabilityHttpServer(
                    plugin,
                    coordinator,
                    settings.observabilityBindHost(),
                    settings.observabilityBindPort(),
                    settings.observabilityWebsocketHost(),
                    settings.observabilityWebsocketPort()
            );
            this.observabilityHttpServer.start();
        }
        if (settings.observabilityWebsocketEnabled()) {
            this.liveWebSocketServer = new MasterLiveWebSocketServer(
                    plugin,
                    coordinator,
                    settings.observabilityWebsocketHost(),
                    settings.observabilityWebsocketPort(),
                    settings.observabilityStreamIntervalTicks()
            );
            this.liveWebSocketServer.start();
        }
    }

    private void startNode() {
        this.nodeClient = new NodeTcpClient(plugin, settings);
        this.nodeClient.start();
        if (plugin.getCommand("nexuschunk") != null) {
            plugin.getCommand("nexuschunk").setExecutor(new NexusChunkCommand(nodeClient));
        }
    }
}

