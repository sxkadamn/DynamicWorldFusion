package steelrework.dynamicWorldFusion.core.service;

import steelrework.dynamicWorldFusion.core.config.NexusSettings;
import steelrework.dynamicWorldFusion.core.model.NodeSession;
import steelrework.dynamicWorldFusion.core.model.RouteMoveSuggestion;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public final class HotSwapPlanner {
    private final NexusSettings settings;

    public HotSwapPlanner(NexusSettings settings) {
        this.settings = settings;
    }

    public Optional<RouteMoveSuggestion> findSuggestion(Collection<NodeSession> sessions) {
        if (sessions.size() < 2) {
            return Optional.empty();
        }

        Optional<NodeSession> overloaded = sessions.stream()
                .filter(this::isOverloaded)
                .max(Comparator.comparingDouble(s -> s.latestLoad().entities() - s.latestLoad().tps()));

        Optional<NodeSession> leastLoaded = sessions.stream()
                .filter(s -> !isOverloaded(s))
                .min(Comparator.comparingDouble(s -> s.latestLoad().entities() + s.latestLoad().players()));

        if (overloaded.isEmpty() || leastLoaded.isEmpty()) {
            return Optional.empty();
        }

        NodeSession src = overloaded.get();
        NodeSession dst = leastLoaded.get();
        if (src.nodeId().equals(dst.nodeId())) {
            return Optional.empty();
        }

        String reason = "TPS=" + src.latestLoad().tps() + ", сущности=" + src.latestLoad().entities();
        return Optional.of(new RouteMoveSuggestion(src.zoneId(), src.nodeId(), dst.nodeId(), reason));
    }

    private boolean isOverloaded(NodeSession session) {
        return session.latestLoad().tps() < settings.overloadTpsThreshold()
                || session.latestLoad().entities() > settings.overloadEntitiesThreshold();
    }
}
