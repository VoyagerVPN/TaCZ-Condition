package ru.ranazy.condition.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.ranazy.condition.Condition;

/**
 * Менеджер сетевого взаимодействия мода Condition.
 * Регистрирует каналы связи и обеспечивает отправку пакетов.
 */
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static int packetId = 0;

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(Condition.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    /**
     * Регистрирует пакеты мода. Вызывается при общей инициализации.
     */
    public static void register() {
        INSTANCE.messageBuilder(RegisterModelStructurePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RegisterModelStructurePacket::encode)
            .decoder(RegisterModelStructurePacket::new)
            .consumerMainThread(RegisterModelStructurePacket::handle)
            .add();

        INSTANCE.messageBuilder(SyncEntityHitboxesPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(SyncEntityHitboxesPacket::encode)
            .decoder(SyncEntityHitboxesPacket::new)
            .consumerMainThread(SyncEntityHitboxesPacket::handle)
            .add();

        INSTANCE.messageBuilder(SyncVitalsPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncVitalsPacket::encode)
            .decoder(SyncVitalsPacket::new)
            .consumerMainThread(SyncVitalsPacket::handle)
            .add();

        INSTANCE.messageBuilder(SyncDebugModePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncDebugModePacket::encode)
            .decoder(SyncDebugModePacket::new)
            .consumerMainThread(SyncDebugModePacket::handle)
            .add();
    }

    /**
     * Отправляет пакет от клиента на сервер.
     */
    public static void sendToServer(Object packet) {
        INSTANCE.sendToServer(packet);
    }

    /**
     * Отправляет пакет от сервера конкретному игроку на клиент.
     */
    public static void sendToPlayer(Object packet, net.minecraft.server.level.ServerPlayer player) {
        INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
