package ru.ranazy.condition.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.ranazy.condition.Condition;

import java.util.function.Supplier;

/**
 * Сетевой пакет для синхронизации флага отладки (debugMode) с сервера на клиент.
 */
public class SyncDebugModePacket {
    private final boolean enabled;

    public SyncDebugModePacket(boolean enabled) {
        this.enabled = enabled;
    }

    public SyncDebugModePacket(FriendlyByteBuf buf) {
        this.enabled = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.enabled);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Применяем флаг отладки на клиенте с защитой от запуска на сервере
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Condition.debugMode = this.enabled;
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
