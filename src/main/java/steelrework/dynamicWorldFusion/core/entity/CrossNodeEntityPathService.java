package steelrework.dynamicWorldFusion.core.entity;

import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class CrossNodeEntityPathService implements Listener {
    private final JavaPlugin plugin;
    private final String nodeId;
    private final Consumer<EntityPursuitEvent> outboundSink;
    private final Map<String, EntityPursuitEvent> remoteTargets;

    public CrossNodeEntityPathService(JavaPlugin plugin, String nodeId, Consumer<EntityPursuitEvent> outboundSink) {
        this.plugin = plugin;
        this.nodeId = nodeId;
        this.outboundSink = outboundSink;
        this.remoteTargets = new HashMap<>();
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        Entity entity = event.getEntity();
        Entity target = event.getTarget();
        if (!(entity instanceof Creature) || !(target instanceof Player player)) {
            return;
        }

        outboundSink.accept(new EntityPursuitEvent(
                nodeId,
                entity.getWorld().getName(),
                entity.getUniqueId().toString(),
                player.getUniqueId().toString(),
                entity.getLocation().getX(),
                entity.getLocation().getY(),
                entity.getLocation().getZ()
        ));
    }

    public void onRemotePursuit(EntityPursuitEvent pursuitEvent) {
        remoteTargets.put(pursuitEvent.entityUuid(), pursuitEvent);
        plugin.getLogger().fine("Обновлен удаленный граф преследования для сущности " + pursuitEvent.entityUuid());
    }

    public Map<String, EntityPursuitEvent> snapshotRemoteTargets() {
        return Map.copyOf(remoteTargets);
    }
}

