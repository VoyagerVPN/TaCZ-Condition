package ru.ranazy.condition.common;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import ru.ranazy.condition.Condition;
import ru.ranazy.condition.network.NetworkHandler;
import ru.ranazy.condition.network.SyncVitalsPacket;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.util.AttachmentDataUtils;

/**
 * Менеджер выносливости (стамины) игрока.
 * Рассчитывает лимиты выносливости по здоровью и броне, списывает при нагрузках
 * и управляет блокировкой бега (Stamina Lock) при полном истощении.
 */
@Mod.EventBusSubscriber(modid = Condition.MOD_ID)
public class StaminaManager {

    private static final String STAMINA_KEY = "condition:stamina";
    private static final String STAMINA_LOCK_KEY = "condition:stamina_lock";
    
    private static final String LAST_SENT_STAMINA = "condition:last_sent_stamina";
    private static final String LAST_SENT_MAX_STAMINA = "condition:last_sent_max_stamina";
    private static final String LAST_SENT_WEIGHT = "condition:last_sent_weight";

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }

        Player player = event.player;
        
        // Обновляем вес и скорость игрока раз в секунду (20 тиков)
        if (player.tickCount % 20 == 0) {
            WeightManager.updatePlayerSpeed(player);
        }

        tickStamina(player);
    }

    @SubscribeEvent
    public static void onPlayerJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }

        var persistentData = player.getPersistentData();
        float currentStamina = persistentData.contains(STAMINA_KEY) ? persistentData.getFloat(STAMINA_KEY) : 200.0f;
        float cost = 15.0f;

        // Проверяем здоровье ног в First Aid
        boolean legsBroken = false;
        var modelOpt = player.getCapability(ichttt.mods.firstaid.api.CapabilityExtendedHealthSystem.INSTANCE).resolve();
        if (modelOpt.isPresent()) {
            var model = modelOpt.get();
            // Если здоровье левой или правой ноги критическое (<= 1.0)
            if (model.LEFT_LEG.currentHealth <= 1.0f || model.RIGHT_LEG.currentHealth <= 1.0f) {
                legsBroken = true;
            }
        }

        if (legsBroken) {
            cost = 30.0f; // Прыжок со сломанными ногами тратит вдвое больше выносливости
            // Наносим дополнительное повреждение ноге при прыжке со сломанными ногами
            if (modelOpt.isPresent()) {
                var model = modelOpt.get();
                if (model.LEFT_LEG.currentHealth > 0.0f) {
                    model.LEFT_LEG.damage(1.0f, player, true);
                } else if (model.RIGHT_LEG.currentHealth > 0.0f) {
                    model.RIGHT_LEG.damage(1.0f, player, true);
                }
            }
        }

        currentStamina = Math.max(0.0f, currentStamina - cost);
        persistentData.putFloat(STAMINA_KEY, currentStamina);
    }

    private static void tickStamina(Player player) {
        var persistentData = player.getPersistentData();

        // 1. Сжатие стамины по общему здоровью First Aid
        float healthRatio = 1.0f;
        var modelOpt = player.getCapability(ichttt.mods.firstaid.api.CapabilityExtendedHealthSystem.INSTANCE).resolve();
        if (modelOpt.isPresent()) {
            var model = modelOpt.get();
            float totalMax = 0.0f;
            float totalCurrent = 0.0f;
            for (var part : model) {
                totalMax += part.getMaxHealth();
                totalCurrent += part.currentHealth;
            }
            if (totalMax > 0.0f) {
                healthRatio = totalCurrent / totalMax;
            }
        }

        // 2. Сжатие стамины по очкам брони
        float armorValue = player.getArmorValue();
        float armorPenaltyRatio = 1.0f - (armorValue * 0.01f); // Минус 1% стамины за каждое очко брони
        if (armorPenaltyRatio < 0.2f) {
            armorPenaltyRatio = 0.2f; // Лимит штрафа брони - максимум 80% снижения
        }

        // 3. Вычисление максимального лимита
        float maxStamina = 200.0f * healthRatio * armorPenaltyRatio;
        if (maxStamina < 20.0f) {
            maxStamina = 20.0f; // Абсолютный минимум максимальной стамины
        }

        float stamina = persistentData.contains(STAMINA_KEY) ? persistentData.getFloat(STAMINA_KEY) : maxStamina;
        if (stamina > maxStamina) {
            stamina = maxStamina;
        }

        // 4. Проверяем Stamina Lock (блокировка бега при полном истощении)
        int lockTicks = persistentData.getInt(STAMINA_LOCK_KEY);
        if (lockTicks > 0) {
            lockTicks--;
            persistentData.putInt(STAMINA_LOCK_KEY, lockTicks);
            player.setSprinting(false); // Запрещаем спринт во время блокировки
        }

        // 5. Рассчитываем расход выносливости в этом тике
        float drain = 0.0f;

        // Бег
        if (player.isSprinting()) {
            drain += 1.0f;
        }

        // Плавание
        if (player.isInWater() && player.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
            drain += 0.5f;
        }

        // Прицеливание (ADS) из тяжелого оружия TacZ
        if (IGunOperator.fromLivingEntity(player).getSynIsAiming()) {
            ItemStack stack = player.getMainHandItem();
            IGun iGun = IGun.getIGunOrNull(stack);
            if (iGun != null) {
                ResourceLocation gunId = iGun.getGunId(stack);
                float gunWeight = TimelessAPI.getCommonGunIndex(gunId)
                    .map(index -> (float) AttachmentDataUtils.getWightWithAttachment(stack, index.getGunData()))
                    .orElse(0.0f);
                if (gunWeight > 4.5f) {
                    drain += 0.5f; // Оружие тяжелее 4.5 кг тратит 0.5 выносливости в тик при прицеливании
                }
            }
        }

        // Обработка изменения выносливости
        if (drain > 0.0f) {
            stamina = Math.max(0.0f, stamina - drain);
            if (stamina == 0.0f && lockTicks <= 0) {
                persistentData.putInt(STAMINA_LOCK_KEY, 60); // Блокировка бега на 3 секунды
                player.setSprinting(false);
            }
        } else {
            // Регенерация при отсутствии нагрузок
            float baseRegen = 1.5f;
            int foodLevel = player.getFoodData().getFoodLevel();

            // Влияние уровня сытости
            if (foodLevel >= 15) {
                baseRegen = 1.5f;
            } else if (foodLevel >= 6) {
                baseRegen = 0.9f; // Скорость регенерации 60%
            } else {
                baseRegen = 0.3f; // Скорость регенерации 20%
                // Накладываем слабость раз в 2 секунды при крайнем голоде
                if (player.tickCount % 40 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, true, false));
                }
            }

            // Замедление регенерации общим весом
            float totalWeight = WeightManager.getTotalWeight(player);
            float weightPenalty = 1.0f - (totalWeight / WeightManager.MAX_WEIGHT);
            if (weightPenalty < 0.1f) {
                weightPenalty = 0.1f; // Лимит штрафа веса - восстановление не падает ниже 10%
            }

            stamina = Math.min(maxStamina, stamina + baseRegen * weightPenalty);
        }

        persistentData.putFloat(STAMINA_KEY, stamina);

        // 6. Сетевая синхронизация с клиентом (с оптимизацией трафика)
        float totalWeight = WeightManager.getTotalWeight(player);
        float lastStamina = persistentData.getFloat(LAST_SENT_STAMINA);
        float lastMaxStamina = persistentData.getFloat(LAST_SENT_MAX_STAMINA);
        float lastWeight = persistentData.getFloat(LAST_SENT_WEIGHT);

        if (Math.abs(stamina - lastStamina) > 0.5f || 
            Math.abs(maxStamina - lastMaxStamina) > 0.5f || 
            Math.abs(totalWeight - lastWeight) > 0.1f ||
            player.tickCount % 40 == 0) {

            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new SyncVitalsPacket(stamina, maxStamina, totalWeight)
                );
            }

            persistentData.putFloat(LAST_SENT_STAMINA, stamina);
            persistentData.putFloat(LAST_SENT_MAX_STAMINA, maxStamina);
            persistentData.putFloat(LAST_SENT_WEIGHT, totalWeight);
        }
    }
}
