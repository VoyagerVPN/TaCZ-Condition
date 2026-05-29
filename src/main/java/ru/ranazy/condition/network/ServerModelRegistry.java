package ru.ranazy.condition.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.ranazy.condition.geometry.RotatedHitbox;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный реестр чертежей хитбоксов.
 * Хранит стандартные недеформированные структуры OBB для каждого типа моба/игрока.
 */
public class ServerModelRegistry {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<String, List<RotatedHitbox>> BLUEPRINTS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<RotatedHitbox>> LIVE_PLAYER_HITBOXES = new ConcurrentHashMap<>();

    public static void register(String entityTypeId, List<RotatedHitbox> hitboxes) {
        if (!BLUEPRINTS.containsKey(entityTypeId)) {
            BLUEPRINTS.put(entityTypeId, hitboxes);
            LOGGER.info("[Condition] Зарегистрирован статический чертеж геометрии для: {} (компонентов: {})", 
                entityTypeId, hitboxes.size());
        }
    }

    public static List<RotatedHitbox> get(String entityTypeId) {
        return BLUEPRINTS.get(entityTypeId);
    }

    public static void updateLiveHitboxes(UUID entityUuid, List<RotatedHitbox> hitboxes) {
        LIVE_PLAYER_HITBOXES.put(entityUuid, hitboxes);
    }

    public static List<RotatedHitbox> getLiveHitboxes(UUID entityUuid) {
        return LIVE_PLAYER_HITBOXES.get(entityUuid);
    }

    public static void removeLiveHitboxes(UUID entityUuid) {
        LIVE_PLAYER_HITBOXES.remove(entityUuid);
    }

    public static void clear() {
        BLUEPRINTS.clear();
        LIVE_PLAYER_HITBOXES.clear();
    }
}
