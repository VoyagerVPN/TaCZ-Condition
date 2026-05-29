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
    
    // Карта для связи объектов ModelPart с их типами
    public static final Map<net.minecraft.client.model.geom.ModelPart, ichttt.mods.firstaid.api.enums.EnumPlayerPart> trackedParts = new IdentityHashMap<>();

    public static void registerParts(net.minecraft.client.model.PlayerModel<?> playerModel) {
        trackedParts.clear();
        trackedParts.put(playerModel.head, ichttt.mods.firstaid.api.enums.EnumPlayerPart.HEAD);
        trackedParts.put(playerModel.body, ichttt.mods.firstaid.api.enums.EnumPlayerPart.BODY);
        trackedParts.put(playerModel.jacket, ichttt.mods.firstaid.api.enums.EnumPlayerPart.BODY);
        
        trackedParts.put(playerModel.leftArm, ichttt.mods.firstaid.api.enums.EnumPlayerPart.LEFT_ARM);
        trackedParts.put(playerModel.leftSleeve, ichttt.mods.firstaid.api.enums.EnumPlayerPart.LEFT_ARM);
        
        trackedParts.put(playerModel.rightArm, ichttt.mods.firstaid.api.enums.EnumPlayerPart.RIGHT_ARM);
        trackedParts.put(playerModel.rightSleeve, ichttt.mods.firstaid.api.enums.EnumPlayerPart.RIGHT_ARM);
        
        trackedParts.put(playerModel.leftLeg, ichttt.mods.firstaid.api.enums.EnumPlayerPart.LEFT_LEG);
        trackedParts.put(playerModel.leftPants, ichttt.mods.firstaid.api.enums.EnumPlayerPart.LEFT_LEG);
        
        trackedParts.put(playerModel.rightLeg, ichttt.mods.firstaid.api.enums.EnumPlayerPart.RIGHT_LEG);
        trackedParts.put(playerModel.rightPants, ichttt.mods.firstaid.api.enums.EnumPlayerPart.RIGHT_LEG);
    }
}
