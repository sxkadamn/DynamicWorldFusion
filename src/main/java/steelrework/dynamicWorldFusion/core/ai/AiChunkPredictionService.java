package steelrework.dynamicWorldFusion.core.ai;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class AiChunkPredictionService implements Listener {
    private final JavaPlugin plugin;
    private final String nodeId;
    private final Consumer<PredictionHint> sink;
    private final int lookaheadChunks;
    private final double minSpeedBlocksPerTick;

    private final Map<String, Long> lastEmitMillisByPlayer = new HashMap<>();

    public AiChunkPredictionService(
            JavaPlugin plugin,
            String nodeId,
            Consumer<PredictionHint> sink,
            int lookaheadChunks,
            double minSpeedBlocksPerTick
    ) {
        this.plugin = plugin;
        this.nodeId = nodeId;
        this.sink = sink;
        this.lookaheadChunks = lookaheadChunks;
        this.minSpeedBlocksPerTick = minSpeedBlocksPerTick;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Vector movement = to.toVector().subtract(from.toVector());
        double speed = movement.length();
        if (speed < minSpeedBlocksPerTick) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastEmitMillisByPlayer.get(player.getUniqueId().toString());
        if (last != null && (now - last) < 700L) {
            return;
        }
        lastEmitMillisByPlayer.put(player.getUniqueId().toString(), now);

        Vector dir = to.getDirection().normalize();
        int predictedChunkX = (int) Math.floor((to.getX() + dir.getX() * lookaheadChunks * 16.0) / 16.0);
        int predictedChunkZ = (int) Math.floor((to.getZ() + dir.getZ() * lookaheadChunks * 16.0) / 16.0);

        sink.accept(new PredictionHint(
                nodeId,
                player.getUniqueId().toString(),
                player.getWorld().getName(),
                predictedChunkX,
                predictedChunkZ,
                lookaheadChunks,
                "скорость=" + String.format("%.2f", speed)
        ));
    }
}

