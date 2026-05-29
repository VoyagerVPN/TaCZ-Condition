package ru.ranazy.condition.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import ru.ranazy.condition.geometry.RotatedHitbox;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер состояния сканирования 3D-моделей на клиенте.
 * Сохраняет промежуточные переменные во время этапа рендеринга.
 */
public class ClientModelTracker {
    public static LivingEntity currentlyScanningEntity = null;
    public static Vec3 currentCameraPos = null;
    public static float currentCameraPitch = 0.0f;
    public static float currentCameraYaw = 0.0f;
    public static boolean isGeneratingBlueprint = false;
    
    // Список OBB коробок для формируемого статического чертежа модели
    public static final List<RotatedHitbox> currentBlueprintBoxes = new ArrayList<>();
    
    // Список кэш-ключей зарегистрированных чертежей на сервере
    public static final Set<String> sentBlueprints = new HashSet<>();
    
    // Список UUID сущностей, отрендеренных в текущем кадре
    public static final Set<UUID> renderedThisFrame = ConcurrentHashMap.newKeySet();
}
