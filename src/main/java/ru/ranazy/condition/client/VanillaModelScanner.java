package ru.ranazy.condition.client;

import com.mojang.blaze3d.vertex.PoseStack;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import ru.ranazy.condition.geometry.RotatedHitbox;
import ru.ranazy.condition.mixin.client.ModelPartAccessor;
import ru.ranazy.condition.util.IAccurateEntity;

import java.util.List;
import java.util.Map;

/**
 * Сканер ванильной 3D-модели игрока (PlayerModel) на клиенте.
 * Извлекает OBB хитбоксы для каждой части тела.
 */
public class VanillaModelScanner {

    /**
     * Рекурсивно сканирует кубоиды внутри ModelPart и строит OBB хитбоксы.
     */
    public static void scanModelPartTree(ModelPart part, PoseStack matrices, EnumPlayerPart bodyPart, boolean isBlueprint) {
        if (!part.visible) {
            return;
        }

        matrices.pushPose();
        part.translateAndRotate(matrices);

        List<ModelPart.Cube> cuboids = ((ModelPartAccessor) (Object) part).condition$getCubes();
        if (cuboids != null && !cuboids.isEmpty()) {
            float scale = 0.0625f; // 1/16
            PoseStack.Pose entry = matrices.last();

            if (isBlueprint) {
                for (ModelPart.Cube cuboid : cuboids) {
                    AABB box = new AABB(
                        cuboid.minX * scale, cuboid.minY * scale, cuboid.minZ * scale,
                        cuboid.maxX * scale, cuboid.maxY * scale, cuboid.maxZ * scale
                    );
                    RotatedHitbox obb = new RotatedHitbox(box, entry.pose(), Vec3.ZERO, bodyPart);
                    ClientModelTracker.currentBlueprintBoxes.add(obb);
                }
            } else if (ClientModelTracker.currentlyScanningEntity != null && ClientModelTracker.currentCameraPos != null) {
                // Перевод в абсолютные мировые координаты с учетом камеры
                Matrix4f cameraInverse = new Matrix4f()
                    .rotateY((float) Math.toRadians(-ClientModelTracker.currentCameraYaw - 180.0f))
                    .rotateX((float) Math.toRadians(-ClientModelTracker.currentCameraPitch));
                Matrix4f rotationTransform = new Matrix4f().mul(cameraInverse).mul(entry.pose());
                rotationTransform.setTranslation(0.0f, 0.0f, 0.0f); // Убираем глобальный перенос камеры и игрока

                for (ModelPart.Cube cuboid : cuboids) {
                    AABB box = new AABB(
                        cuboid.minX * scale, cuboid.minY * scale, cuboid.minZ * scale,
                        cuboid.maxX * scale, cuboid.maxY * scale, cuboid.maxZ * scale
                    );
                    // Точкой отсчета является мировая позиция игрока
                    RotatedHitbox obb = new RotatedHitbox(box, rotationTransform, ClientModelTracker.currentlyScanningEntity.position(), bodyPart);
                    ((IAccurateEntity) ClientModelTracker.currentlyScanningEntity).condition$getHitboxes().add(obb);
                }
            }
        }

        // Рекурсивно обходим дочерние элементы этой части модели
        Map<String, ModelPart> children = ((ModelPartAccessor) (Object) part).condition$getChildren();
        if (children != null) {
            for (ModelPart child : children.values()) {
                scanModelPartTree(child, matrices, bodyPart, isBlueprint);
            }
        }

        matrices.popPose();
    }

    /**
     * Сканирует модель игрока и извлекает хитбоксы для всех конечностей.
     */
    public static void scanModel(EntityModel<?> model, PoseStack matrices, boolean isBlueprint, boolean isBaby) {
        if (model instanceof PlayerModel<?> playerModel) {
            try {
                // Голова и шлем
                scanModelPartTree(playerModel.head, matrices, EnumPlayerPart.HEAD, isBlueprint);
                scanModelPartTree(playerModel.hat, matrices, EnumPlayerPart.HEAD, isBlueprint);

                // Тело и куртка
                scanModelPartTree(playerModel.body, matrices, EnumPlayerPart.BODY, isBlueprint);
                scanModelPartTree(playerModel.jacket, matrices, EnumPlayerPart.BODY, isBlueprint);

                // Левая рука и рукав
                scanModelPartTree(playerModel.leftArm, matrices, EnumPlayerPart.LEFT_ARM, isBlueprint);
                scanModelPartTree(playerModel.leftSleeve, matrices, EnumPlayerPart.LEFT_ARM, isBlueprint);

                // Правая рука и рукав
                scanModelPartTree(playerModel.rightArm, matrices, EnumPlayerPart.RIGHT_ARM, isBlueprint);
                scanModelPartTree(playerModel.rightSleeve, matrices, EnumPlayerPart.RIGHT_ARM, isBlueprint);

                // Левая нога и штанина
                scanModelPartTree(playerModel.leftLeg, matrices, EnumPlayerPart.LEFT_LEG, isBlueprint);
                scanModelPartTree(playerModel.leftPants, matrices, EnumPlayerPart.LEFT_LEG, isBlueprint);

                // Правая нога и штанина
                scanModelPartTree(playerModel.rightLeg, matrices, EnumPlayerPart.RIGHT_LEG, isBlueprint);
                scanModelPartTree(playerModel.rightPants, matrices, EnumPlayerPart.RIGHT_LEG, isBlueprint);
            } catch (Exception e) {
                // Предотвращаем падение рендеринга
            }
        }
    }
}
