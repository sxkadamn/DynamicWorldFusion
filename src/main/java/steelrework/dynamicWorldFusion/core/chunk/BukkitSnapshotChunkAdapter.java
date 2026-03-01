package steelrework.dynamicWorldFusion.core.chunk;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class BukkitSnapshotChunkAdapter implements ChunkNmsAdapter {
    private static final int SECTION_HEIGHT = 16;

    private final JavaPlugin plugin;

    public BukkitSnapshotChunkAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public byte[] encodeChunk(String worldName, int chunkX, int chunkZ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Мир не найден для сериализации чанка: " + worldName);
            return new byte[0];
        }

        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(true, true, true);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos);
             DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeUTF(worldName);
            out.writeInt(chunkX);
            out.writeInt(chunkZ);
            out.writeInt(minY);
            out.writeInt(maxY);

            for (int y = minY; y < maxY; y += SECTION_HEIGHT) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        out.writeInt(snapshot.getBlockType(lx, y, lz).ordinal());
                    }
                }
            }
            out.flush();
            gzip.finish();
            return baos.toByteArray();
        } catch (IOException ex) {
            plugin.getLogger().warning("Ошибка сериализации чанка: " + ex.getMessage());
            return new byte[0];
        }
    }

    @Override
    public void applyChunkPayload(String worldName, int chunkX, int chunkZ, byte[] payload) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(payload);
             GZIPInputStream gzip = new GZIPInputStream(bais);
             DataInputStream in = new DataInputStream(gzip)) {
            String payloadWorld = in.readUTF();
            int payloadChunkX = in.readInt();
            int payloadChunkZ = in.readInt();
            int minY = in.readInt();
            int maxY = in.readInt();

            int blocksRead = 0;
            for (int y = minY; y < maxY; y += SECTION_HEIGHT) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        in.readInt();
                        blocksRead++;
                    }
                }
            }

            plugin.getLogger().fine(
                    "Данные чанка приняты: " + payloadWorld + " [" + payloadChunkX + "," + payloadChunkZ + "], блоков=" + blocksRead
            );
        } catch (Exception ex) {
            plugin.getLogger().warning(
                    "Ошибка применения payload чанка " + worldName + " [" + chunkX + "," + chunkZ + "]: " + ex.getMessage()
            );
        }
    }
}

