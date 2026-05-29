package ru.ranazy.condition.network;

import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import ru.ranazy.condition.geometry.RotatedHitbox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Сетевой пакет для динамической синхронизации OBB хитбоксов сущности (игрока) с клиента на сервер.
 * Отправляется клиентом при изменении позы.
 */
public class SyncEntityHitboxesPacket {
    private static final Logger LOGGER = LogManager.getLogger();

    private final UUID entityUuid;
    private final List<RotatedHitbox> hitboxes;

    public SyncEntityHitboxesPacket(UUID entityUuid, List<RotatedHitbox> hitboxes) {
        this.entityUuid = entityUuid;
        this.hitboxes = hitboxes;
    }

    public SyncEntityHitboxesPacket(FriendlyByteBuf buf) {
        this.entityUuid = buf.readUUID();
        int size = buf.readInt();
        this.hitboxes = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            AABB localBounds = new AABB(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble()
            );

            Matrix4f transformMatrix = new Matrix4f(
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()
            );

            Vec3 worldOffset = new Vec3(
                buf.readDouble(), buf.readDouble(), buf.readDouble()
            );

            EnumPlayerPart bodyPart = null;
            byte partOrdinal = buf.readByte();
            if (partOrdinal >= 0 && partOrdinal < EnumPlayerPart.values().length) {
                bodyPart = EnumPlayerPart.values()[partOrdinal];
            }

            this.hitboxes.add(new RotatedHitbox(localBounds, transformMatrix, worldOffset, bodyPart));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.entityUuid);
        buf.writeInt(this.hitboxes.size());

        for (RotatedHitbox obb : this.hitboxes) {
            buf.writeDouble(obb.localBounds.minX);
            buf.writeDouble(obb.localBounds.minY);
            buf.writeDouble(obb.localBounds.minZ);
            buf.writeDouble(obb.localBounds.maxX);
            buf.writeDouble(obb.localBounds.maxY);
            buf.writeDouble(obb.localBounds.maxZ);

            buf.writeFloat(obb.transformMatrix.m00());
            buf.writeFloat(obb.transformMatrix.m01());
            buf.writeFloat(obb.transformMatrix.m02());
            buf.writeFloat(obb.transformMatrix.m03());
            buf.writeFloat(obb.transformMatrix.m10());
            buf.writeFloat(obb.transformMatrix.m11());
            buf.writeFloat(obb.transformMatrix.m12());
            buf.writeFloat(obb.transformMatrix.m13());
            buf.writeFloat(obb.transformMatrix.m20());
            buf.writeFloat(obb.transformMatrix.m21());
            buf.writeFloat(obb.transformMatrix.m22());
            buf.writeFloat(obb.transformMatrix.m23());
            buf.writeFloat(obb.transformMatrix.m30());
            buf.writeFloat(obb.transformMatrix.m31());
            buf.writeFloat(obb.transformMatrix.m32());
            buf.writeFloat(obb.transformMatrix.m33());

            buf.writeDouble(obb.worldOffset.x);
            buf.writeDouble(obb.worldOffset.y);
            buf.writeDouble(obb.worldOffset.z);

            if (obb.bodyPart != null) {
                buf.writeByte(obb.bodyPart.ordinal());
            } else {
                buf.writeByte(-1);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Player sender = ctx.get().getSender();
            if (sender == null) {
                return;
            }

            Entity target = ((net.minecraft.server.level.ServerLevel) sender.level()).getEntity(this.entityUuid);
            if (target == null) {
                return;
            }

            // Валидация безопасности: локальное смещение OBB относительно центра игрока не должно превышать 3 блоков (9.0 кв.м)
            boolean isValid = true;
            for (RotatedHitbox obb : this.hitboxes) {
                if (obb.worldOffset.lengthSqr() > 9.0) {
                    isValid = false;
                    break;
                }
            }

            if (isValid) {
                ServerModelRegistry.updateLiveHitboxes(this.entityUuid, this.hitboxes);
            } else {
                LOGGER.warn("[Condition Server] Отклонен некорректный пакет синхронизации хитбоксов для сущности: {}", this.entityUuid);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void send(UUID entityUuid, List<RotatedHitbox> hitboxes) {
        NetworkHandler.sendToServer(new SyncEntityHitboxesPacket(entityUuid, hitboxes));
    }
}
