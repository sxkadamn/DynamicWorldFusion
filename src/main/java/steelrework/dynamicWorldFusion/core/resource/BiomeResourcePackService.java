package steelrework.dynamicWorldFusion.core.resource;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class BiomeResourcePackService implements Listener {
    private final JavaPlugin plugin;
    private final Map<String, String> biomePrefixToPackUrl;
    private final Map<String, String> lastAppliedUrlByPlayer;

    public BiomeResourcePackService(JavaPlugin plugin, Map<String, String> biomePrefixToPackUrl) {
        this.plugin = plugin;
        this.biomePrefixToPackUrl = biomePrefixToPackUrl;
        this.lastAppliedUrlByPlayer = new HashMap<>();
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Biome biome = event.getTo().getBlock().getBiome();
        NamespacedKey key = biome.getKey();
        String biomeId = key.getNamespace() + ":" + key.getKey();
        String packUrl = resolvePackUrl(biomeId);
        if (packUrl == null || packUrl.isBlank()) {
            return;
        }

        String playerId = player.getUniqueId().toString();
        if (packUrl.equals(lastAppliedUrlByPlayer.get(playerId))) {
            return;
        }
        lastAppliedUrlByPlayer.put(playerId, packUrl);
        applyPack(player, packUrl);
    }

    private String resolvePackUrl(String biomeId) {
        String normalized = biomeId.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : biomePrefixToPackUrl.entrySet()) {
            if (normalized.startsWith(entry.getKey().toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void applyPack(Player player, String packUrl) {
        try {
            Method legacy = Player.class.getMethod("setResourcePack", String.class);
            legacy.invoke(player, packUrl);
            return;
        } catch (Exception ignored) {
        }

        plugin.getLogger().warning("Unable to apply resource pack for player " + player.getName()
                + ". Не найден совместимый API-метод.");
    }
}
