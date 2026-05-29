package ru.ranazy.condition.geometry;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище «живых» анимированных хитбоксов сущностей в оперативной памяти.
 * Используется для быстрой синхронизации без сетевого оверхеда в одиночной игре.
 */
public class HitboxRegistry {
    private static final Map<UUID, List<RotatedHitbox>> ACTIVE_HITBOXES = new ConcurrentHashMap<>();

    /**
     * Регистрирует менеджер в шине событий Forge для автоматической очистки кэша.
     */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new HitboxRegistry());
    }

    public static void put(UUID entityUuid, List<RotatedHitbox> hitboxes) {
        ACTIVE_HITBOXES.put(entityUuid, hitboxes);
    }

    public static List<RotatedHitbox> get(UUID entityUuid) {
        return ACTIVE_HITBOXES.get(entityUuid);
    }

    public static void remove(UUID entityUuid) {
        ACTIVE_HITBOXES.remove(entityUuid);
    }

    public static void clear() {
        ACTIVE_HITBOXES.clear();
    }

    /**
     * Автоматически удаляет хитбоксы сущности из кэша при её выгрузке из мира
     * (деспавн, смерть, перемещение в другой мир или выгрузка чанка),
     * предотвращая утечки памяти (memory leaks).
     */
    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() != null) {
            remove(event.getEntity().getUUID());
        }
    }
}
