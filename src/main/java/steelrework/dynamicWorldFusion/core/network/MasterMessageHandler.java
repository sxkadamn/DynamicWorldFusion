package steelrework.dynamicWorldFusion.core.network;

import steelrework.dynamicWorldFusion.core.protocol.WireMessage;

public interface MasterMessageHandler {
    void onMessage(TcpConnection connection, WireMessage message);

    void onDisconnect(TcpConnection connection);
}
