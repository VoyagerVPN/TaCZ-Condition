package ru.ranazy.condition;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

/**
 * Главный класс мода Condition.
 * Отвечает за инициализацию и связывание систем здоровья (First Aid) и 3D-хитбоксов (GeckoLib/TacZ).
 */
@Mod(Condition.MOD_ID)
public class Condition {
    public static final String MOD_ID = "condition";
    public static final Logger LOGGER = LogManager.getLogger();
    public static boolean debugMode = false;

    public Condition() {
        // Получаем шину событий жизненного цикла мода
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрируем слушатели этапов инициализации
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);

        // Регистрируем основной игровой цикл на шине Forge
        MinecraftForge.EVENT_BUS.register(this);

        // Инициализируем хранилище хитбоксов и регистрируем его автоочистку
        ru.ranazy.condition.geometry.HitboxRegistry.init();

        // Инициализируем сетевой канал
        ru.ranazy.condition.network.NetworkHandler.register();

        // Инициализируем обработчик перехвата урона
        ru.ranazy.condition.common.DamageInterceptHandler.init();
        
        LOGGER.info("[TaCZ] Condition mod initialized.");
    }

    /**
     * Общий этап инициализации (выполняется на клиенте и на сервере).
     */
    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Инициализация Condition: общий этап...");
    }

    /**
     * Клиентский этап инициализации (выполняется только на физическом клиенте).
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Инициализация Condition: клиентский этап...");
    }

    /**
     * Отключает естественную регенерацию здоровья на сервере при загрузке уровня.
     */
    @SubscribeEvent
    public void onLevelLoad(final LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, serverLevel.getServer());
            LOGGER.info("Естественная регенерация здоровья отключена для мира: {}", serverLevel.dimension().location());
        }
    }

    /**
     * Синхронизирует режим отладки с игроком при его входе на сервер.
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(final net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ru.ranazy.condition.network.NetworkHandler.sendToPlayer(
                new ru.ranazy.condition.network.SyncDebugModePacket(debugMode), 
                player
            );
            LOGGER.info("[Condition] Синхронизирован режим отладки ({}) для игрока {}", debugMode, player.getName().getString());
        }
    }
}
