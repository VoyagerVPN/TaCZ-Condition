package ru.ranazy.condition.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import ru.ranazy.condition.Condition;
import ru.ranazy.condition.geometry.HitboxRegistry;
import ru.ranazy.condition.geometry.RotatedHitbox;

import java.util.List;

/**
 * Клиентский обработчик рендеринга OBB хитбоксов игроков в мире.
 * Визуализирует хитбоксы голубым (Cyan) цветом при активном F3+B или включенном debugMode.
 */
@Mod.EventBusSubscriber(modid = Condition.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HitboxRenderer {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // Рендерим OBB хитбоксы только на стадии AFTER_ENTITIES
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }

        // Проверяем, включен ли показ хитбоксов через F3+B или режим отладки Condition
        boolean showHitboxes = client.getEntityRenderDispatcher().shouldRenderHitBoxes() || Condition.debugMode;
        if (!showHitboxes) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack matrices = event.getPoseStack();
        MultiBufferSource.BufferSource immediate = client.renderBuffers().bufferSource();
        VertexConsumer vertices = immediate.getBuffer(RenderType.lines());

        float pitch = camera.getXRot();
        float yaw = camera.getYRot();

        // Строим матрицу поворота камеры
        Matrix4f camRot = new Matrix4f()
            .rotateX((float) Math.toRadians(pitch))
            .rotateY((float) Math.toRadians(yaw + 180.0f));

        // Обходим всех игроков в мире
        for (Player player : client.level.players()) {
            // Для себя хитбоксы рисуем только от третьего лица
            if (player.getUUID().equals(client.player.getUUID()) && client.options.getCameraType().isFirstPerson()) {
                continue;
            }

            List<RotatedHitbox> obbs = HitboxRegistry.get(player.getUUID());
            if (obbs == null || obbs.isEmpty()) {
                continue;
            }

            for (RotatedHitbox obb : obbs) {
                // Смещение OBB относительно камеры
                double dx = obb.worldOffset.x - camPos.x;
                double dy = obb.worldOffset.y - camPos.y;
                double dz = obb.worldOffset.z - camPos.z;

                // Результирующая матрица рендеринга OBB
                Matrix4f renderMatrix = new Matrix4f(camRot)
                    .translate((float) dx, (float) dy, (float) dz)
                    .mul(obb.transformMatrix);

                matrices.pushPose();
                // Подменяем матрицу трансформации в PoseStack на нашу OBB матрицу
                matrices.last().pose().set(renderMatrix);

                // Отрисовываем ребра коробки голубым цветом (R=0.0f, G=1.0f, B=1.0f, A=1.0f)
                LevelRenderer.renderLineBox(matrices, vertices, obb.localBounds, 0.0f, 1.0f, 1.0f, 1.0f);

                matrices.popPose();
            }
        }

        // Принудительно отрисовываем накопленные линии
        immediate.endBatch(RenderType.lines());
    }
}
