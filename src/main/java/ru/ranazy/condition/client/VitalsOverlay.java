package ru.ranazy.condition.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.ranazy.condition.Condition;
import ru.ranazy.condition.common.WeightManager;

/**
 * Клиентский HUD оверлей для постоянного отображения выносливости, веса
 * и отладочной информации в углу экрана при включенном режиме отладки.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Condition.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class VitalsOverlay {

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("vitals_overlay", OVERLAY);
    }

    private static final IGuiOverlay OVERLAY = (ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }

        Font font = mc.font;

        // ------------------ 1. РЕНДЕРИНГ ВЫНОСЛИВОСТИ ------------------
        float stamina = ClientVitalsCache.stamina;
        float maxStamina = ClientVitalsCache.maxStamina;
        if (maxStamina <= 0) {
            maxStamina = 200.0f;
        }

        int barWidth = 182;
        int barHeight = 3;
        int x = screenWidth / 2 - 91;
        int y = screenHeight - 50; // Высота над хотбаром

        // Рисуем темную полупрозрачную подложку
        graphics.fill(x, y, x + barWidth, y + barHeight, 0x80000000);

        // Рассчитываем заполнение
        int fillWidth = (int) (barWidth * (stamina / maxStamina));
        fillWidth = Math.max(0, Math.min(barWidth, fillWidth));

        // Выбираем цвет: если выносливость заблокирована (на нуле), делаем шкалу серой
        int staminaColor = (stamina <= 0.05f) ? 0xFF777777 : 0xFFFF9900; // Серый или Оранжевый

        // Рисуем заполнение
        graphics.fill(x, y, x + fillWidth, y + barHeight, staminaColor);

        // ------------------ 2. ПОСТОЯННЫЙ UI ВЕСА ------------------
        float weight = ClientVitalsCache.weight;
        float maxWeight = WeightManager.MAX_WEIGHT;

        // Форматируем текст
        String weightText = String.format("%.1f / %.1f кг", weight, maxWeight);
        int weightTextWidth = font.width(weightText);

        // Выравниваем по правому краю хотбара, на 12 пикселей выше полоски стамины
        int weightX = screenWidth / 2 + 91 - weightTextWidth;
        int weightY = screenHeight - 62;

        // Выбираем цвет в зависимости от нагрузки
        int weightColor = 0xFFFFFFFF; // Белый по умолчанию
        float loadRatio = weight / maxWeight;
        if (loadRatio >= 1.0f) {
            weightColor = 0xFFFF5555; // Красный при перегрузе (100%+)
        } else if (loadRatio >= 0.6f) {
            weightColor = 0xFFFFFF55; // Желтый при высокой нагрузке (60%+)
        }

        // Рисуем текст с тенью
        graphics.drawString(font, weightText, weightX, weightY, weightColor, true);

        // ------------------ 3. ОТЛАДКА В УГЛУ ЭКРАНА ------------------
        if (Condition.debugMode) {
            int debugX = 10;
            int debugY = 30; // Смещение вниз, чтобы не перекрывать лог дебага Crossfire

            String debugTitle = "§b[Condition Debug Mode]";
            String debugStamina = String.format("§fStamina: §e%.1f / %.1f", stamina, maxStamina);
            String debugWeight = String.format("§fWeight: §e%.1f / %.1f kg §7(%.1f%%)", weight, maxWeight, loadRatio * 100);

            // Отрисовываем темную рамку подложки
            graphics.fill(debugX - 4, debugY - 4, debugX + 160, debugY + 36, 0x90000000);

            // Выводим текст
            graphics.drawString(font, debugTitle, debugX, debugY, 0xFFFFFFFF, false);
            graphics.drawString(font, debugStamina, debugX, debugY + 12, 0xFFFFFFFF, false);
            graphics.drawString(font, debugWeight, debugX, debugY + 24, 0xFFFFFFFF, false);
        }
    };
}
