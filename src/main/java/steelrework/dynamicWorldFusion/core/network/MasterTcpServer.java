package steelrework.dynamicWorldFusion.core.network;

import org.bukkit.plugin.java.JavaPlugin;
import steelrework.dynamicWorldFusion.core.protocol.WireCodec;
import steelrework.dynamicWorldFusion.core.protocol.WireMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MasterTcpServer {
    private final JavaPlugin plugin;
    private final String host;
    private final int port;
    private final MasterMessageHandler handler;
    private final AtomicBoolean running;
    private final ExecutorService acceptExecutor;
    private final ExecutorService clientExecutor;

    private ServerSocket serverSocket;

    public MasterTcpServer(JavaPlugin plugin, String host, int port, MasterMessageHandler handler) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.handler = handler;
        this.running = new AtomicBoolean(false);
        this.acceptExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "nexus-master-accept"));
        this.clientExecutor = Executors.newCachedThreadPool(r -> new Thread(r, "nexus-master-client"));
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        acceptExecutor.execute(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.bind(new InetSocketAddress(InetAddress.getByName(host), port));
                plugin.getLogger().info("Nexus Master запущен и слушает " + host + ":" + port);

                while (running.get()) {
                    Socket socket = serverSocket.accept();
                    clientExecutor.execute(() -> handleClient(socket));
                }
            } catch (IOException ex) {
                if (running.get()) {
                    plugin.getLogger().severe("Ошибка Master-сервера: " + ex.getMessage());
                }
            }
        });
    }

    public void stop() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        acceptExecutor.shutdownNow();
        clientExecutor.shutdownNow();
    }

    private void handleClient(Socket socket) {
        TcpConnection connection = null;
        try {
            connection = new TcpConnection(socket);
            plugin.getLogger().info("Подключен узел: " + connection.remoteAddress());
            String line;
            while ((line = connection.readLine()) != null && running.get()) {
                Optional<WireMessage> decoded = WireCodec.decode(line);
                if (decoded.isPresent()) {
                    handler.onMessage(connection, decoded.get());
                }
            }
        } catch (IOException ex) {
            if (running.get()) {
                plugin.getLogger().warning("Соединение с узлом закрыто с ошибкой: " + ex.getMessage());
            }
        } finally {
            if (connection != null) {
                handler.onDisconnect(connection);
                try {
                    connection.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}

