package steelrework.dynamicWorldFusion.core.network;

import steelrework.dynamicWorldFusion.core.protocol.WireCodec;
import steelrework.dynamicWorldFusion.core.protocol.WireMessage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class TcpConnection implements Closeable {
    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    public TcpConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public String readLine() throws IOException {
        return reader.readLine();
    }

    public synchronized void send(WireMessage message) throws IOException {
        writer.write(WireCodec.encode(message));
        writer.newLine();
        writer.flush();
    }

    public String remoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
