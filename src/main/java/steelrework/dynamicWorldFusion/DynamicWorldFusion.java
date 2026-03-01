package steelrework.dynamicWorldFusion;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import steelrework.dynamicWorldFusion.core.NexusRuntime;
import steelrework.dynamicWorldFusion.core.update.SpigotUpdateChecker;
import steelrework.dynamicWorldFusion.core.update.UpdateNotifier;

public final class DynamicWorldFusion extends JavaPlugin {

    private NexusRuntime runtime;
    private UpdateNotifier updateNotifier;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initMetrics();
        initUpdateChecker();
        this.runtime = new NexusRuntime(this);
        this.runtime.start();
    }

    @Override
    public void onDisable() {
        if (this.runtime != null) {
            this.runtime.stop();
        }
    }

    private void initMetrics() {
        if (!getConfig().getBoolean("bstats.enabled", true)) {
            return;
        }
        int pluginId = getConfig().getInt("bstats.plugin-id", 0);
        if (pluginId <= 0) {
            getLogger().info("bStats отключен: не указан plugin-id.");
            return;
        }
        new Metrics(this, pluginId);
    }

    private void initUpdateChecker() {
        if (!getConfig().getBoolean("update.enabled", true)) {
            return;
        }
        int resourceId = getConfig().getInt("update.resource-id", 0);
        if (resourceId <= 0) {
            getLogger().info("Проверка обновлений отключена: не указан resource-id.");
            return;
        }

        boolean notifyOnJoin = getConfig().getBoolean("update.notify-on-join", true);
        if (notifyOnJoin) {
            updateNotifier = new UpdateNotifier(resourceId);
            getServer().getPluginManager().registerEvents(updateNotifier, this);
        }

        SpigotUpdateChecker checker = new SpigotUpdateChecker(this, resourceId);
        checker.checkAsync(result -> {
            if (updateNotifier != null) {
                updateNotifier.setResult(result);
            }
            if (result.updateAvailable()) {
                getLogger().warning("Доступна новая версия DynamicWorldFusion: "
                        + result.latestVersion() + " (установлена " + result.currentVersion() + ").");
            } else {
                getLogger().info("DynamicWorldFusion обновлен до последней версии (" + result.currentVersion() + ").");
            }
        });
    }
}
