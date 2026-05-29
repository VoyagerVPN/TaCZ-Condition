package ru.ranazy.condition.common;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.ranazy.condition.Condition;

/**
 * Обработчик звуков шагов.
 * Перехватывает событие воспроизведения звуков у сущностей и увеличивает громкость шагов игрока при перегрузе.
 */
@Mod.EventBusSubscriber(modid = Condition.MOD_ID)
public class FootstepSoundHandler {

    @SubscribeEvent
    public static void onPlaySound(PlayLevelSoundEvent.AtEntity event) {
        if (event.getEntity() instanceof Player player) {
            // Фильтруем звуки шагов по пути ResourceLocation
            if (event.getSound() != null && event.getSound().value().getLocation().getPath().contains("step")) {
                float totalWeight = WeightManager.getTotalWeight(player);
                float threshold = WeightManager.MAX_WEIGHT * 0.7f; // Порог начала утяжеления шагов (35 кг)

                if (totalWeight > threshold) {
                    float ratio = (totalWeight - threshold) / (WeightManager.MAX_WEIGHT - threshold);
                    if (ratio > 1.0f) {
                        ratio = 1.0f;
                    }
                    // Увеличиваем громкость шагов от 1.0x до 2.0x в зависимости от перегруза
                    float volumeMultiplier = 1.0f + ratio;
                    event.setNewVolume(event.getNewVolume() * volumeMultiplier);
                }
            }
        }
    }
}
