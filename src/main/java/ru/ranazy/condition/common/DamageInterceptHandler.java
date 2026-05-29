package ru.ranazy.condition.common;

import com.tacz.guns.entity.EntityKineticBullet;
import ichttt.mods.firstaid.api.CapabilityExtendedHealthSystem;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.ranazy.condition.Condition;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обработчик перехвата урона.
 * Связывает попадания пуль TacZ по OBB хитбоксам с locational системой здоровья First Aid.
 */
public class DamageInterceptHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    
    // Временный кэш для хранения пораженных конечностей по UUID пуль
    public static final Map<UUID, EnumPlayerPart> BULLET_HIT_PARTS = new ConcurrentHashMap<>();

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new DamageInterceptHandler());
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        // Обрабатываем урон только для игроков
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Проверяем, что источником урона является пуля TacZ
        Entity directEntity = event.getSource().getDirectEntity();
        if (directEntity instanceof EntityKineticBullet bullet) {
            UUID bulletUuid = bullet.getUUID();
            EnumPlayerPart hitPart = BULLET_HIT_PARTS.remove(bulletUuid);

            // Если конечность была определена нашей OBB хитбокс-системой
            if (hitPart != null) {
                // Получаем модель здоровья игрока из First Aid
                player.getCapability(CapabilityExtendedHealthSystem.INSTANCE).ifPresent(model -> {
                    float rawDamage = event.getAmount();

                    // 1. Находим слот брони, соответствующий этой конечности
                    EquipmentSlot armorSlot = getArmorSlotForPart(hitPart);
                    ItemStack armorStack = player.getItemBySlot(armorSlot);

                    float finalDamage = rawDamage;

                    // 2. Рассчитываем поглощение брони именно для этой конечности
                    if (!armorStack.isEmpty() && armorStack.getItem() instanceof ArmorItem armorItem) {
                        int defense = armorItem.getDefense();
                        float toughness = armorItem.getToughness();

                        // Используем ванильные формулы для расчета урона после брони
                        finalDamage = CombatRules.getDamageAfterAbsorb(rawDamage, defense, toughness);

                        // Наносим урон прочности брони в этом слоте
                        int damageToArmor = (int) Math.max(1.0f, finalDamage / 4.0f);
                        armorStack.hurtAndBreak(damageToArmor, player, p -> p.broadcastBreakEvent(armorSlot));
                    }

                    // 3. Наносим урон в конкретную часть тела First Aid
                    AbstractDamageablePart part = model.getFromEnum(hitPart);
                    float excess = part.damage(finalDamage, player, true);

                    // 4. Механика перелива урона (Spillover): 80% избытка идет в тело (BODY)
                    if (excess > 0.0f && hitPart != EnumPlayerPart.BODY) {
                        model.BODY.damage(excess * 0.8f, player, true);
                    }

                    // Принудительно планируем синхронизацию здоровья с клиентом
                    model.scheduleResync();

                    // Отменяем стандартный ванильный урон игроку, чтобы избежать двойного вычета ХП
                    event.setCanceled(true);

                    // 5. Логирование и отладка
                    Entity attacker = event.getSource().getEntity();
                    
                    // Лог в консоль сервера (всегда)
                    LOGGER.info("[Condition Hit] {} -> {} | Конечность: {} | Исходный урон: {} | После брони: {} | Броня: {} (Износ: {})", 
                        attacker != null ? attacker.getName().getString() : "Неизвестно",
                        player.getName().getString(),
                        hitPart.name(),
                        rawDamage,
                        finalDamage,
                        armorStack.isEmpty() ? "Нет" : armorStack.getHoverName().getString(),
                        armorStack.isEmpty() ? "0" : (armorStack.getMaxDamage() - armorStack.getDamageValue()) + "/" + armorStack.getMaxDamage()
                    );

                    // Лог в чат (если включен debugMode)
                    if (Condition.debugMode) {
                        // Сообщение цели
                        String targetMessage = String.format("§d[Condition Debug] §fПолучено попадание в §e%s §fот §a%s §fна §c%.1f §fурона (после брони: §c%.1f§f)", 
                            hitPart.name(),
                            attacker != null ? attacker.getName().getString() : "Неизвестно",
                            rawDamage,
                            finalDamage
                        );
                        player.sendSystemMessage(Component.literal(targetMessage));

                        // Сообщение стрелку (если это игрок)
                        if (attacker instanceof Player attackerPlayer) {
                            String msg = String.format("§b[Condition Debug] §fПопадание в §e%s §fигрока §a%s §fна §c%.1f §fурона (после брони: §c%.1f§f, броня: %s)", 
                                hitPart.name(),
                                player.getName().getString(),
                                rawDamage,
                                finalDamage,
                                armorStack.isEmpty() ? "§7Нет§f" : "§6" + armorStack.getHoverName().getString() + " §7(" + (armorStack.getMaxDamage() - armorStack.getDamageValue()) + "/" + armorStack.getMaxDamage() + ")§f"
                            );
                            attackerPlayer.sendSystemMessage(Component.literal(msg));
                        }
                    }
                });
            }
        }
    }

    /**
     * Возвращает соответствующий слот брони для части тела.
     */
    private EquipmentSlot getArmorSlotForPart(EnumPlayerPart part) {
        switch (part) {
            case HEAD: 
                return EquipmentSlot.HEAD;
            case BODY:
            case LEFT_ARM:
            case RIGHT_ARM: 
                return EquipmentSlot.CHEST;
            case LEFT_LEG:
            case RIGHT_LEG: 
                return EquipmentSlot.LEGS;
            default: 
                return EquipmentSlot.CHEST;
        }
    }
}
