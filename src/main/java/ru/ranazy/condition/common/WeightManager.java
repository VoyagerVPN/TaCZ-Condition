package ru.ranazy.condition.common;

import me.xjqsh.lesraisinsarmor.item.LrArmorItem;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.util.AttachmentDataUtils;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Менеджер веса игрока.
 * Рассчитывает общую массу экипировки и инвентаря, накладывая штраф на скорость передвижения.
 */
public class WeightManager {
    public static final float MAX_WEIGHT = 50.0f; // Максимальный лимит веса в кг
    public static final UUID WEIGHT_SPEED_MODIFIER_UUID = UUID.fromString("b33c166d-1579-4d6d-8968-3e54721415df");

    /**
     * Рассчитывает вес отдельного стака предметов в кг.
     */
    public static float getItemStackWeight(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0f;
        }

        // 1. Броня LesRaisins Armor (ванильная броня игнорируется)
        if (stack.getItem() instanceof LrArmorItem armorItem) {
            float baseWeight = 2.0f;
            if (armorItem.getType() == ArmorItem.Type.CHESTPLATE) {
                baseWeight = 8.0f;
            } else if (armorItem.getType() == ArmorItem.Type.LEGGINGS) {
                baseWeight = 6.0f;
            }
            float defense = armorItem.getDefense();
            float toughness = armorItem.getToughness();
            // Формула веса брони
            return (baseWeight + defense * 0.6f + toughness * 0.3f) * stack.getCount();
        }

        // 2. Оружие TacZ с обвесами
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun != null) {
            ResourceLocation gunId = iGun.getGunId(stack);
            return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> (float) AttachmentDataUtils.getWightWithAttachment(stack, index.getGunData()))
                .orElse(0.0f) * stack.getCount();
        }

        // 3. Патроны TacZ (20г на патрон)
        if (stack.getItem() instanceof IAmmo) {
            return 0.02f * stack.getCount();
        }

        // 4. Коробки патронов TacZ (2кг за коробку)
        if (stack.getItem() instanceof IAmmoBox) {
            return 2.0f * stack.getCount();
        }

        // 5. Блоки (50г на блок)
        if (stack.getItem() instanceof BlockItem) {
            return 0.05f * stack.getCount();
        }

        // 6. Остальные предметы по умолчанию (10г на штуку)
        return 0.01f * stack.getCount();
    }

    /**
     * Рассчитывает полный вес инвентаря игрока.
     */
    public static float getTotalWeight(Player player) {
        if (player == null) {
            return 0.0f;
        }
        float totalWeight = 0.0f;

        // Обходим основной инвентарь (36 слотов)
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            totalWeight += getItemStackWeight(player.getInventory().items.get(i));
        }

        // Обходим слоты брони (4 слота)
        for (int i = 0; i < player.getInventory().armor.size(); i++) {
            totalWeight += getItemStackWeight(player.getInventory().armor.get(i));
        }

        // Вторая рука (1 слот)
        for (int i = 0; i < player.getInventory().offhand.size(); i++) {
            totalWeight += getItemStackWeight(player.getInventory().offhand.get(i));
        }

        return totalWeight;
    }

    /**
     * Вычисляет модификатор скорости (от 0.0 до -0.90) на основе веса.
     * Штраф начинает действовать свыше 25 кг (50% от лимита) и линейно возрастает.
     * При 50 кг и выше скорость снижается до 10% от базовой (модификатор -0.90).
     */
    public static double getSpeedModifier(float totalWeight) {
        if (totalWeight <= 25.0f) {
            return 0.0;
        }
        if (totalWeight >= MAX_WEIGHT) {
            return -0.90; // Замедление на 90% (остается 10% скорости)
        }
        // Линейная интерполяция между 25 кг (0% штрафа) и 50 кг (90% штрафа)
        double ratio = (totalWeight - 25.0f) / (MAX_WEIGHT - 25.0f);
        return -0.90 * ratio;
    }

    /**
     * Обновляет атрибут скорости игрока на основе переносимого веса.
     */
    public static void updatePlayerSpeed(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }

        var speedAttribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttribute == null) {
            return;
        }

        float totalWeight = getTotalWeight(player);
        double modifierAmount = getSpeedModifier(totalWeight);

        AttributeModifier currentModifier = speedAttribute.getModifier(WEIGHT_SPEED_MODIFIER_UUID);

        if (modifierAmount == 0.0) {
            if (currentModifier != null) {
                speedAttribute.removeModifier(WEIGHT_SPEED_MODIFIER_UUID);
            }
        } else {
            if (currentModifier == null || currentModifier.getAmount() != modifierAmount) {
                speedAttribute.removeModifier(WEIGHT_SPEED_MODIFIER_UUID);
                speedAttribute.addTransientModifier(new AttributeModifier(
                    WEIGHT_SPEED_MODIFIER_UUID,
                    "Condition Weight Speed Penalty",
                    modifierAmount,
                    AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
        }
    }
}
