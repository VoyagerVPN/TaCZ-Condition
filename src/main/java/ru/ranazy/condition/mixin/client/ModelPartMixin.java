package ru.ranazy.condition.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.ranazy.condition.client.ClientModelTracker;
import ru.ranazy.condition.geometry.RotatedHitbox;
import ru.ranazy.condition.util.IAccurateEntity;

import java.util.List;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin {

    @Shadow public boolean visible;
    @Shadow public abstract void translateAndRotate(PoseStack poseStack);
    
    // We can't easily shadow final fields with @Shadow if they don't match, 
    // but cubes is private final List<ModelPart.Cube> cubes;
    // We already have ModelPartAccessor, let's use it!

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private void onRender(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (!this.visible || ClientModelTracker.currentlyScanningEntity == null || ClientModelTracker.currentCameraPos == null) {
            return;
        }

        EnumPlayerPart partEnum = ClientModelTracker.trackedParts.get((ModelPart) (Object) this);
        if (partEnum != null) {
            List<ModelPart.Cube> cuboids = ((ModelPartAccessor) (Object) this).condition$getCubes();
            if (cuboids != null && !cuboids.isEmpty()) {
                poseStack.pushPose();
                this.translateAndRotate(poseStack);
                
                PoseStack.Pose entry = poseStack.last();
                float scale = 0.0625f; // 1/16

                Matrix4f cameraInverse = new Matrix4f()
                    .rotateY((float) Math.toRadians(-ClientModelTracker.currentCameraYaw - 180.0f))
                    .rotateX((float) Math.toRadians(-ClientModelTracker.currentCameraPitch));
                Matrix4f rotationTransform = new Matrix4f().mul(cameraInverse).mul(entry.pose());

                for (ModelPart.Cube cuboid : cuboids) {
                    AABB box = new AABB(
                        cuboid.minX * scale, cuboid.minY * scale, cuboid.minZ * scale,
                        cuboid.maxX * scale, cuboid.maxY * scale, cuboid.maxZ * scale
                    );
                    RotatedHitbox obb = new RotatedHitbox(box, rotationTransform, ClientModelTracker.currentCameraPos, partEnum);
                    ((IAccurateEntity) ClientModelTracker.currentlyScanningEntity).condition$getHitboxes().add(obb);
                }

                poseStack.popPose();
            }
        }
    }
}
