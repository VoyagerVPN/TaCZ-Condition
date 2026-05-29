package ru.ranazy.trauma;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Главный класс мода [TaCZ] Trauma.
 * Отвечает за инициализацию и связывание систем здоровья (First Aid) и 3D-хитбоксов (GeckoLib/TacZ).
 */
@Mod(Trauma.MOD_ID)
public class Trauma {
    public static final String MOD_ID = "trauma";
    public static final Logger LOGGER = LogManager.getLogger();

    public Trauma() {
        // Получаем шину событий жизненного цикла мода
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрируем слушатели этапов инициализации
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);

        // Регистрируем основной игровой цикл на шине Forge
        MinecraftForge.EVENT_BUS.register(this);
        
        LOGGER.info("[TaCZ] Trauma mod initialized.");
    }

    /**
     * Общий этап инициализации (выполняется на клиенте и на сервере).
     */
    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Инициализация Trauma: общий этап...");
    }

    /**
     * Клиентский этап инициализации (выполняется только на физическом клиенте).
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Инициализация Trauma: клиентский этап...");
    }
}
