package ru.ranazy.condition.mixin.compat;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.EntityUtil;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.ranazy.condition.Condition;
import ru.ranazy.condition.common.DamageInterceptHandler;
import ru.ranazy.condition.geometry.HitboxRegistry;
import ru.ranazy.condition.geometry.RotatedHitbox;
import ru.ranazy.condition.network.ServerModelRegistry;

import java.util.List;
import java.util.Optional;

/**
 * Миксин для перехвата проверки попаданий пуль TacZ и перенаправления её на OBB хитбоксы.
 */
@Mixin(value = EntityUtil.class, remap = false)
public class TaczBulletRaytraceMixin {
    private static final Logger LOGGER = LogManager.getLogger();

    @Inject(method = "getHitResult", at = @At("HEAD"), cancellable = true, remap = false)
    private static void condition$getHitResult(Projectile bulletEntity, Entity entity, Vec3 startVec, Vec3 endVec, 
                                             CallbackInfoReturnable<EntityKineticBullet.EntityResult> cir) {
        // Система работает только для игроков (мобы не участвуют)
        if (!(entity instanceof Player player)) {
            return;
        }

        LOGGER.info("[Condition Raytrace Server] getHitResult вызвана! debugMode={}, пуля={}, игрок={}, yBodyRot={}, getYRot()={}, yHeadRot={}. Старт: {}, Конец: {}",
            Condition.debugMode, bulletEntity.getUUID(), player.getName().getString(), player.yBodyRot, player.getYRot(), player.yHeadRot, startVec, endVec);

        double closestDistance = Double.MAX_VALUE;
        Vec3 closestHit = null;
        EnumPlayerPart closestPart = null;

        // 1. Пытаемся получить «живые» анимированные хитбоксы (клиент / одиночная игра)
        List<RotatedHitbox> activeHitboxes = HitboxRegistry.get(player.getUUID());
        boolean isDedicatedServer = false;
        if (activeHitboxes == null || activeHitboxes.isEmpty()) {
            // Если на сервере (выделенный сервер), берем присланные от клиента живые хитбоксы
            activeHitboxes = ServerModelRegistry.getLiveHitboxes(player.getUUID());
            isDedicatedServer = true;
        }
        boolean hasHitboxes = activeHitboxes != null && !activeHitboxes.isEmpty();

        if (hasHitboxes) {
            if (Condition.debugMode) {
                LOGGER.info("[Condition Raytrace] Найдено {} живых хитбоксов для игрока {}", activeHitboxes.size(), player.getName().getString());
            }
            float targetYaw = player.yBodyRot;
            Vec3 playerPos = player.position();
            Matrix4f rotYNeg = new Matrix4f().rotateY((float) Math.toRadians(180.0f - targetYaw));

            for (RotatedHitbox obb : activeHitboxes) {
                RotatedHitbox checkObb;
                if (isDedicatedServer) {
                    // Восстанавливаем мировые координаты из локальных:
                    // M_world = RotationY(-player.yBodyRot) * M_local
                    Matrix4f worldM = new Matrix4f(rotYNeg).mul(obb.transformMatrix);

                    // O_world = player_pos, так как O_local равен Vec3.ZERO
                    Vec3 realWorldOffset = playerPos;

                    checkObb = new RotatedHitbox(obb.localBounds, worldM, realWorldOffset, obb.bodyPart);
                } else {
                    // В синглплеере/клиенте хитбоксы уже в мировых координатах
                    checkObb = obb;
                }

                Optional<Vec3> hitOpt = checkObb.intersect(startVec, endVec);
                if (hitOpt.isPresent()) {
                    double dist = startVec.distanceToSqr(hitOpt.get());
                    if (Condition.debugMode) {
                        LOGGER.info("  - Попадание в живую конечность: {}, Точка: {}, Дистанция: {}", checkObb.bodyPart, hitOpt.get(), dist);
                    }
                    if (dist < closestDistance) {
                        closestDistance = dist;
                        closestHit = hitOpt.get();
                        closestPart = checkObb.bodyPart;
                    }
                }
            }
        } else {
            // 2. Если живых хитбоксов нет (выделенный сервер), используем статический чертеж модели игрока
            ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(player.getType());
            String cacheKey = typeId.toString() + (player.isBaby() ? "_baby" : "");
            List<RotatedHitbox> blueprint = ServerModelRegistry.get(cacheKey);

            if (blueprint != null && !blueprint.isEmpty()) {
                hasHitboxes = true;
                if (Condition.debugMode) {
                    LOGGER.info("[Condition Raytrace] Найдено {} хитбоксов в статическом чертеже {}", blueprint.size(), cacheKey);
                }
                // Вращаем статический чертеж вокруг Y оси на угол поворота тела игрока
                float targetYaw = player.yBodyRot;
                LOGGER.info("[Condition Raytrace Server] Трассировка чертежа. Углы игрока: yBodyRot={}, getYRot()={}, yHeadRot={}. Позиция: {}", 
                    targetYaw, player.getYRot(), player.yHeadRot, player.position());
                
                for (RotatedHitbox obb : blueprint) {
                    Matrix4f rotationMatrix = new Matrix4f()
                        .rotateY((float) Math.toRadians(180.0f - targetYaw))
                        .mul(obb.transformMatrix);

                    RotatedHitbox rotatedObb = new RotatedHitbox(obb.localBounds, rotationMatrix, player.position(), obb.bodyPart);
                    Optional<Vec3> hitOpt = rotatedObb.intersect(startVec, endVec);
                    
                    LOGGER.info("  - Чертеж OBB: {}, Границы: {}, Позиция игрока: {}, Поворот: {}", 
                        obb.bodyPart, obb.localBounds, player.position(), targetYaw);

                    if (hitOpt.isPresent()) {
                        double dist = startVec.distanceToSqr(hitOpt.get());
                        LOGGER.info("    -> ПОПАДАНИЕ в OBB конечность: {}, Точка: {}, Дистанция: {}", obb.bodyPart, hitOpt.get(), dist);
                        if (dist < closestDistance) {
                            closestDistance = dist;
                            closestHit = hitOpt.get();
                            closestPart = obb.bodyPart;
                        }
                    } else {
                        LOGGER.info("    -> Промах по OBB конечности: {}", obb.bodyPart);
                    }
                }
            } else {
                LOGGER.info("[Condition Raytrace Server] Чертеж не найден для ключа: {}", cacheKey);
            }
        }

        // Если коллизия обработана по нашей системе
        if (hasHitboxes) {
            if (closestHit != null) {
                LOGGER.info("[Condition Raytrace Server] Итог: ПОПАДАНИЕ в конечность: {} в точке {}", closestPart, closestHit);
                // Запоминаем пораженную конечность для этой пули
                DamageInterceptHandler.BULLET_HIT_PARTS.put(bulletEntity.getUUID(), closestPart);

                // Возвращаем результат попадания. Хедшот определяется строго по конечности HEAD
                boolean isHeadshot = (closestPart == EnumPlayerPart.HEAD);
                cir.setReturnValue(new EntityKineticBullet.EntityResult(player, closestHit, isHeadshot));
            } else {
                LOGGER.info("[Condition Raytrace Server] Итог: ПРОМАХ (пуля пролетела мимо всех OBB)");
                // Пуля пролетела мимо повернутых OBB хитбоксов
                cir.setReturnValue(null);
            }
        }
    }

    @ModifyConstant(
        method = {"findEntityOnPath", "findEntitiesOnPath"},
        constant = @Constant(doubleValue = 1.0),
        remap = false
    )
    private static double condition$modifyInflate(double original) {
        // Увеличиваем радиус поиска коробки пули до 3.0 блоков для надежного нахождения OBB в любых позах (включая prone)
        return 3.0;
    }
}
