package steelrework.dynamicWorldFusion.core.update;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SpigotUpdateChecker {
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final JavaPlugin plugin;
    private final int resourceId;
    private final String currentVersion;

    public SpigotUpdateChecker(JavaPlugin plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    public void checkAsync(Consumer<UpdateResult> consumer) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UpdateResult result = checkOnce();
            if (result != null) {
                consumer.accept(result);
            }
        });
    }

    private UpdateResult checkOnce() {
        if (resourceId <= 0) {
            return null;
        }
        try {
            URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "DynamicWorldFusion Update Checker");
            String latest;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                latest = reader.readLine();
            }
            if (latest == null || latest.isBlank()) {
                return new UpdateResult(false, currentVersion, currentVersion);
            }
            boolean newer = isNewer(currentVersion, latest.trim());
            return new UpdateResult(newer, currentVersion, latest.trim());
        } catch (Exception ex) {
            plugin.getLogger().warning("Не удалось проверить обновления: " + ex.getMessage());
            return null;
        }
    }

    private static boolean isNewer(String current, String latest) {
        List<Integer> currentParts = extractNumbers(current);
        List<Integer> latestParts = extractNumbers(latest);
        if (currentParts.isEmpty() || latestParts.isEmpty()) {
            return !current.equalsIgnoreCase(latest);
        }
        int max = Math.max(currentParts.size(), latestParts.size());
        for (int i = 0; i < max; i++) {
            int c = i < currentParts.size() ? currentParts.get(i) : 0;
            int l = i < latestParts.size() ? latestParts.get(i) : 0;
            if (l > c) {
                return true;
            }
            if (l < c) {
                return false;
            }
        }
        return latest.toLowerCase(Locale.ROOT).contains("snapshot")
                && !current.toLowerCase(Locale.ROOT).contains("snapshot");
    }

    private static List<Integer> extractNumbers(String value) {
        List<Integer> result = new ArrayList<>();
        Matcher matcher = DIGITS.matcher(value);
        while (matcher.find()) {
            try {
                result.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public record UpdateResult(boolean updateAvailable, String currentVersion, String latestVersion) {
    }
}
