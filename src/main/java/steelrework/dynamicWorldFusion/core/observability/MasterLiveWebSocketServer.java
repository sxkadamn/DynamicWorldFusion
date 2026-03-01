package steelrework.dynamicWorldFusion.core.observability;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MasterLiveWebSocketServer {
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final JavaPlugin plugin;
    private final MasterTopologyProvider topologyProvider;
    private final String bindHost;
    private final int bindPort;
    private final int streamIntervalTicks;
    private final AtomicBoolean running;
    private final ExecutorService acceptExecutor;
    private final ExecutorService clientExecutor;
    private final Set<Socket> clients;

    private ServerSocket serverSocket;
    private BukkitTask streamTask;

    public MasterLiveWebSocketServer(
            JavaPlugin plugin,
            MasterTopologyProvider topologyProvider,
            String bindHost,
            int bindPort,
            int streamIntervalTicks
    ) {
        this.plugin = plugin;
        this.topologyProvider = topologyProvider;
        this.bindHost = bindHost;
        this.bindPort = bindPort;
        this.streamIntervalTicks = Math.max(1, streamIntervalTicks);
        this.running = new AtomicBoolean(false);
        this.acceptExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "nexus-ws-accept"));
        this.clientExecutor = Executors.newCachedThreadPool(r -> new Thread(r, "nexus-ws-client"));
        this.clients = ConcurrentHashMap.newKeySet();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        acceptExecutor.execute(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.bind(new InetSocketAddress(InetAddress.getByName(bindHost), bindPort));
                plugin.getLogger().info("WebSocket поток запущен на ws://" + bindHost + ":" + bindPort + "/live");
                while (running.get()) {
                    Socket socket = serverSocket.accept();
                    clientExecutor.execute(() -> handleClient(socket));
                }
            } catch (Exception ex) {
                if (running.get()) {
                    plugin.getLogger().warning("Ошибка WebSocket сервера: " + ex.getMessage());
                }
            }
        });

        streamTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::broadcastTopology,
                streamIntervalTicks,
                streamIntervalTicks
        );
    }

    public void stop() {
        running.set(false);
        if (streamTask != null) {
            streamTask.cancel();
            streamTask = null;
        }
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
        }
        for (Socket client : clients) {
            closeQuietly(client);
        }
        clients.clear();
        acceptExecutor.shutdownNow();
        clientExecutor.shutdownNow();
    }

    private void handleClient(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            String wsKey = null;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    break;
                }
                String lower = line.toLowerCase();
                if (lower.startsWith("sec-websocket-key:")) {
                    wsKey = line.substring(line.indexOf(':') + 1).trim();
                }
            }

            if (wsKey == null || wsKey.isBlank()) {
                closeQuietly(socket);
                return;
            }

            String accept = websocketAccept(wsKey);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            writer.write("HTTP/1.1 101 Switching Protocols\r\n");
            writer.write("Upgrade: websocket\r\n");
            writer.write("Connection: Upgrade\r\n");
            writer.write("Sec-WebSocket-Accept: " + accept + "\r\n");
            writer.write("\r\n");
            writer.flush();

            clients.add(socket);
            while (running.get() && !socket.isClosed()) {
                if (socket.getInputStream().read() == -1) {
                    break;
                }
            }
        } catch (Exception ignored) {
        } finally {
            clients.remove(socket);
            closeQuietly(socket);
        }
    }

    private void broadcastTopology() {
        String payload = topologyJson();
        byte[] frame = buildTextFrame(payload);
        for (Socket client : clients) {
            try {
                OutputStream out = client.getOutputStream();
                out.write(frame);
                out.flush();
            } catch (Exception ex) {
                clients.remove(client);
                closeQuietly(client);
            }
        }
    }

    private String topologyJson() {
        StringBuilder json = new StringBuilder("{\"nodes\":[");
        boolean firstNode = true;
        for (Map.Entry<String, Map<String, String>> entry : topologyProvider.nodeTopologySnapshot().entrySet()) {
            if (!firstNode) {
                json.append(',');
            }
            json.append("{\"nodeId\":\"").append(escape(entry.getKey())).append("\"");
            for (Map.Entry<String, String> field : entry.getValue().entrySet()) {
                json.append(",\"").append(escape(field.getKey())).append("\":\"")
                        .append(escape(field.getValue())).append("\"");
            }
            json.append('}');
            firstNode = false;
        }
        json.append("],\"zones\":[");
        boolean firstZone = true;
        for (Map.Entry<String, String> zone : topologyProvider.zoneOwnershipSnapshot().entrySet()) {
            if (!firstZone) {
                json.append(',');
            }
            json.append("{\"zoneId\":\"").append(escape(zone.getKey())).append("\",\"nodeId\":\"")
                    .append(escape(zone.getValue())).append("\"}");
            firstZone = false;
        }
        json.append("],\"events\":[");
        boolean firstEvent = true;
        for (Map<String, String> event : topologyProvider.recentEventsSnapshot()) {
            if (!firstEvent) {
                json.append(',');
            }
            json.append('{');
            boolean firstField = true;
            for (Map.Entry<String, String> field : event.entrySet()) {
                if (!firstField) {
                    json.append(',');
                }
                json.append("\"").append(escape(field.getKey())).append("\":\"")
                        .append(escape(field.getValue())).append("\"");
                firstField = false;
            }
            json.append('}');
            firstEvent = false;
        }
        json.append("]}");
        return json.toString();
    }

    private static String websocketAccept(String key) throws Exception {
        String value = key + WS_GUID;
        byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sha1);
    }

    private static byte[] buildTextFrame(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int length = data.length;
        if (length <= 125) {
            byte[] frame = new byte[2 + length];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) length;
            System.arraycopy(data, 0, frame, 2, length);
            return frame;
        }
        if (length <= 65535) {
            byte[] frame = new byte[4 + length];
            frame[0] = (byte) 0x81;
            frame[1] = 126;
            frame[2] = (byte) ((length >> 8) & 0xFF);
            frame[3] = (byte) (length & 0xFF);
            System.arraycopy(data, 0, frame, 4, length);
            return frame;
        }

        byte[] frame = new byte[10 + length];
        frame[0] = (byte) 0x81;
        frame[1] = 127;
        long l = length;
        for (int i = 0; i < 8; i++) {
            frame[9 - i] = (byte) (l & 0xFF);
            l >>= 8;
        }
        System.arraycopy(data, 0, frame, 10, length);
        return frame;
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }
}

