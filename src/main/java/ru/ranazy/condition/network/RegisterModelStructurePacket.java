package ru.ranazy.condition.network;

import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.joml.Matrix4f;
import ru.ranazy.condition.geometry.RotatedHitbox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Сетевой пакет для регистрации структуры (чертежа) модели сущности на сервере.
 * Отправляется клиентом при первом рендере сущности нового типа.
 */
public class RegisterModelStructurePacket {
    private final String entityTypeId;
    private final List<RotatedHitbox> hitboxes;

    public RegisterModelStructurePacket(String entityTypeId, List<RotatedHitbox> hitboxes) {
        this.entityTypeId = entityTypeId;
        this.hitboxes = hitboxes;
    }

    public RegisterModelStructurePacket(FriendlyByteBuf buf) {
        this.entityTypeId = buf.readUtf(256);
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
            
            // Считываем конечность
            EnumPlayerPart bodyPart = null;
            byte partOrdinal = buf.readByte();
            if (partOrdinal >= 0 && partOrdinal < EnumPlayerPart.values().length) {
                bodyPart = EnumPlayerPart.values()[partOrdinal];
            }
            
            // Чертеж хранится со смещением Vec3.ZERO
            this.hitboxes.add(new RotatedHitbox(localBounds, transformMatrix, Vec3.ZERO, bodyPart));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.entityTypeId, 256);
        buf.writeInt(this.hitboxes.size());
        
        for (RotatedHitbox obb : this.hitboxes) {
            buf.writeDouble(obb.localBounds.minX);
            buf.writeDouble(obb.localBounds.minY);
            buf.writeDouble(obb.localBounds.minZ);
            buf.writeDouble(obb.localBounds.maxX);
            buf.writeDouble(obb.localBounds.maxY);
            buf.writeDouble(obb.localBounds.maxZ);
            
            // Запись 16 элементов матрицы
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
            
            // Запись конечности
            if (obb.bodyPart != null) {
                buf.writeByte(obb.bodyPart.ordinal());
            } else {
                buf.writeByte(-1);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Регистрируем чертеж на сервере
            ServerModelRegistry.register(this.entityTypeId, this.hitboxes);
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Вспомогательный метод для отправки пакета с клиента на сервер.
     */
    public static void send(String entityTypeId, List<RotatedHitbox> hitboxes) {
        NetworkHandler.sendToServer(new RegisterModelStructurePacket(entityTypeId, hitboxes));
    }
}
