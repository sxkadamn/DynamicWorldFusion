package steelrework.dynamicWorldFusion.core.update;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.concurrent.atomic.AtomicReference;

public final class UpdateNotifier implements Listener {
    private final int resourceId;
    private final AtomicReference<SpigotUpdateChecker.UpdateResult> latestResult = new AtomicReference<>();

    public UpdateNotifier(int resourceId) {
        this.resourceId = resourceId;
    }

    public void setResult(SpigotUpdateChecker.UpdateResult result) {
        latestResult.set(result);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        SpigotUpdateChecker.UpdateResult result = latestResult.get();
        if (result == null || !result.updateAvailable()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("dynamicworldfusion.update")) {
            return;
        }
        player.sendMessage(ChatColor.GOLD + "[DynamicWorldFusion] Доступна новая версия: "
                + result.latestVersion() + " (установлена " + result.currentVersion() + ").");
        if (resourceId > 0) {
            player.sendMessage(ChatColor.YELLOW + "Spigot: spigotmc.org/resources/" + resourceId);
        }
    }
}
