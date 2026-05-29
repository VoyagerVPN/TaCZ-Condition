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
import ru.ranazy.condition.client.PoseState;
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
import net.minecraft.util.Mth;
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

        if (this.getModel() instanceof net.minecraft.client.model.PlayerModel<?> playerModel) {
            ClientModelTracker.registerParts(playerModel);
        }

        ((IAccurateEntity) entity).condition$clearHitboxes();
    }

    @Inject(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V", shift = At.Shift.AFTER)
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
        }
    }

    @Unique
    private static final Map<UUID, PoseState> condition$lastSentPoses = new ConcurrentHashMap<>();

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
                    float pitch = player.getXRot();
                    float headYawOffset = player.yHeadRot - player.yBodyRot;

                    boolean isMoving = player.distanceToSqr(player.xOld, player.yOld, player.zOld) > 0.0001 || player.swingTime > 0 || player.walkAnimation.isMoving();
                    long currentTime = System.currentTimeMillis();

                    PoseState lastState = condition$lastSentPoses.get(player.getUUID());
                    long currentLastMoveTime = isMoving ? currentTime : (lastState != null ? lastState.lastMoveTime : 0);

                    if (lastState == null || lastState.hasChanged(crouching, crawling, aiming, reloading, bolting, pitch, headYawOffset, currentLastMoveTime, currentTime)) {
                        condition$lastSentPoses.put(player.getUUID(), new PoseState(crouching, crawling, aiming, reloading, bolting, pitch, headYawOffset, currentTime, currentLastMoveTime));
                        
                        List<RotatedHitbox> localBoxes = new ArrayList<>();

                        // Конвертация camera-space → body-local для серверной отправки:
                        // rotationTransform = cameraInverse × entry.pose()
                        //   cameraInverse уже убирает ViewRot → rotationTransform = Translate(entityPos-camPos) × bodyTransforms
                        // bodyLocal = bodyRotInv × entityTransInv × rotationTransform
                        //   = RotateY(bodyYaw-180) × Translate(camPos-entityPos) × Translate(entityPos-camPos) × bodyTransforms
                        //   = Scale(-1,-1,1) × Translate(0,-1.501,0) × boneChain (формат blueprint)
                        float bodyYaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
                        Matrix4f bodyRotInv = new Matrix4f()
                            .rotateY((float) Math.toRadians(bodyYaw - 180.0f));
                        Vec3 camPos = ClientModelTracker.currentCameraPos;
                        Vec3 entityPos = player.position();
                        Matrix4f entityTransInv = new Matrix4f().translate(
                            (float)(camPos.x - entityPos.x),
                            (float)(camPos.y - entityPos.y),
                            (float)(camPos.z - entityPos.z)
                        );

                        for (RotatedHitbox obb : boxes) {
                            Matrix4f bodyLocalM = new Matrix4f(bodyRotInv)
                                .mul(entityTransInv)
                                .mul(obb.transformMatrix);

                            localBoxes.add(new RotatedHitbox(
                                obb.localBounds,
                                bodyLocalM,
                                Vec3.ZERO,
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
