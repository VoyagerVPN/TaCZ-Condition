package ru.ranazy.condition.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.ranazy.condition.client.ClientVitalsCache;

import java.util.function.Supplier;

/**
 * Сетевой пакет для синхронизации выносливости (стамины) и веса игрока с сервера на клиент.
 * Отправляется сервером при изменении параметров или каждый тик.
 */
public class SyncVitalsPacket {
    private final float stamina;
    private final float maxStamina;
    private final float weight;

    public SyncVitalsPacket(float stamina, float maxStamina, float weight) {
        this.stamina = stamina;
        this.maxStamina = maxStamina;
        this.weight = weight;
    }

    public SyncVitalsPacket(FriendlyByteBuf buf) {
        this.stamina = buf.readFloat();
        this.maxStamina = buf.readFloat();
        this.weight = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(this.stamina);
        buf.writeFloat(this.maxStamina);
        buf.writeFloat(this.weight);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Запись в клиентский кэш с защитой от запуска на сервере
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientVitalsCache.stamina = this.stamina;
                ClientVitalsCache.maxStamina = this.maxStamina;
                ClientVitalsCache.weight = this.weight;
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
