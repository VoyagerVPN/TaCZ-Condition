package ru.ranazy.condition.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.ranazy.condition.client.ClientModelTracker;
import ru.ranazy.condition.client.VanillaModelScanner;
import ru.ranazy.condition.geometry.HitboxRegistry;
import ru.ranazy.condition.geometry.RotatedHitbox;
import ru.ranazy.condition.network.RegisterModelStructurePacket;
import ru.ranazy.condition.network.SyncEntityHitboxesPacket;
import ru.ranazy.condition.util.IAccurateEntity;
import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;

/**
 * Миксин для LivingEntityRenderer, перехватывающий фазу рендеринга ванильных сущностей
 * и извлекающий их OBB хитбоксы.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow public abstract M getModel();

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void preRender(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!entity.isAlive() || entity.isRemoved()) {
            HitboxRegistry.remove(entity.getUUID());
            return;
        }

        // Запоминаем сущность и параметры камеры в начале рендеринга
        ClientModelTracker.currentlyScanningEntity = entity;
        ClientModelTracker.currentCameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        ClientModelTracker.currentCameraPitch = Minecraft.getInstance().gameRenderer.getMainCamera().getXRot();
        ClientModelTracker.currentCameraYaw = Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();

        ((IAccurateEntity) entity).condition$clearHitboxes();
    }

    @Inject(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V")
    )
    private void extractHitboxes(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (ClientModelTracker.currentlyScanningEntity == entity) {
            ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            String cacheKey = entityTypeId.toString() + (entity.isBaby() ? "_baby" : "");

            // Если чертеж для данного типа сущности еще не отправлен, генерируем и отправляем его
            if (!ClientModelTracker.sentBlueprints.contains(cacheKey)) {
                ClientModelTracker.sentBlueprints.add(cacheKey);
                ClientModelTracker.isGeneratingBlueprint = true;
                ClientModelTracker.currentBlueprintBoxes.clear();

                // Строим матрицы для статического чертежа модели (по умолчанию масштаб -1, -1, 1)
                PoseStack pureMatrices = new PoseStack();
                pureMatrices.scale(-1.0f, -1.0f, 1.0f);
                pureMatrices.translate(0.0f, -1.501f, 0.0f);

                if (entity.isBaby() && this.getModel() instanceof HierarchicalModel) {
                    pureMatrices.scale(0.5f, 0.5f, 0.5f);
                }

                VanillaModelScanner.scanModel(this.getModel(), pureMatrices, true, entity.isBaby());
                RegisterModelStructurePacket.send(cacheKey, new ArrayList<>(ClientModelTracker.currentBlueprintBoxes));

                ClientModelTracker.currentBlueprintBoxes.clear();
                ClientModelTracker.isGeneratingBlueprint = false;
            }

            // Сканируем живую модель в текущем кадре рендеринга
            VanillaModelScanner.scanModel(this.getModel(), poseStack, false, entity.isBaby());
        }
    }

    @Unique
    private static final Map<UUID, PoseState> condition$lastSentPoses = new ConcurrentHashMap<>();

    @Unique
    private static class PoseState {
        final boolean crouching;
        final boolean crawling;
        final float aimingProgress;
        final boolean reloading;
        final boolean bolting;

        PoseState(boolean crouching, boolean crawling, float aimingProgress, boolean reloading, boolean bolting) {
            this.crouching = crouching;
            this.crawling = crawling;
            this.aimingProgress = aimingProgress;
            this.reloading = reloading;
            this.bolting = bolting;
        }

        boolean hasChanged(boolean currentCrouching, boolean currentCrawling, float currentAiming, boolean currentReloading, boolean currentBolting) {
            return this.crouching != currentCrouching ||
                   this.crawling != currentCrawling ||
                   Math.abs(this.aimingProgress - currentAiming) > 0.05f ||
                   this.reloading != currentReloading ||
                   this.bolting != currentBolting;
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void postRender(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (ClientModelTracker.currentlyScanningEntity == entity) {
            List<RotatedHitbox> boxes = ((IAccurateEntity) entity).condition$getHitboxes();
            
            // Сохраняем отсканированные живые хитбоксы в кэш
            HitboxRegistry.put(entity.getUUID(), new ArrayList<>(boxes));
            ClientModelTracker.renderedThisFrame.add(entity.getUUID());

            // Событийная отправка OBB-хитбоксов на сервер при смене позы (только для мультиплеера)
            if (entity instanceof Player player) {
                if (player.level().isClientSide() && Minecraft.getInstance().getConnection() != null) {
                    IGunOperator operator = IGunOperator.fromLivingEntity(player);
                    boolean crouching = player.isCrouching();
                    boolean crawling = player.isSwimming(); // в Minecraft ползание (prone) переиспользует анимацию плавания
                    float aiming = operator.getSynAimingProgress();
                    boolean reloading = operator.getSynReloadState().getStateType().isReloading();
                    boolean bolting = operator.getSynIsBolting();

                    PoseState lastState = condition$lastSentPoses.get(player.getUUID());
                    if (lastState == null || lastState.hasChanged(crouching, crawling, aiming, reloading, bolting)) {
                        condition$lastSentPoses.put(player.getUUID(), new PoseState(crouching, crawling, aiming, reloading, bolting));
                        
                        List<RotatedHitbox> localBoxes = new ArrayList<>();
                        float yBodyRot = player.yBodyRot;
                        Vec3 playerPos = player.position();
                        Matrix4f rotY = new Matrix4f().rotateY((float) Math.toRadians(yBodyRot));

                        for (RotatedHitbox obb : boxes) {
                            // M_local = RotationY(yBodyRot) * M_world
                            Matrix4f localM = new Matrix4f(rotY).mul(obb.transformMatrix);

                            // O_local = RotationY(yBodyRot) * (O_world - player_pos)
                            Vec3 diff = obb.worldOffset.subtract(playerPos);
                            Vector3f localO = new Vector3f((float) diff.x, (float) diff.y, (float) diff.z);
                            rotY.transformPosition(localO);

                            localBoxes.add(new RotatedHitbox(
                                obb.localBounds,
                                localM,
                                new Vec3(localO.x, localO.y, localO.z),
                                obb.bodyPart
                            ));
                        }

                        SyncEntityHitboxesPacket.send(player.getUUID(), localBoxes);
                    }
                }
            }

            // Сбрасываем контекст сканирования
            ClientModelTracker.currentlyScanningEntity = null;
            ClientModelTracker.currentCameraPos = null;
        }
    }
}
