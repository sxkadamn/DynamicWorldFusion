package steelrework.dynamicWorldFusion.core.protocol;

public enum MessageType {
    REGISTER_NODE,
    HEARTBEAT,
    LOAD_REPORT,
    CHUNK_PREFETCH_HINT,
    CHUNK_STREAM_FRAME,
    CHUNK_STREAM_ACK,
    ENTITY_PURSUIT_SYNC,
    ROUTE_UPDATE,
    ACK
}
