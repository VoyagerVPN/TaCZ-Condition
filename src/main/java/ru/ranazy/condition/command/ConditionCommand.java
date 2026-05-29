package ru.ranazy.condition.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.ranazy.condition.Condition;
import ru.ranazy.condition.network.NetworkHandler;
import ru.ranazy.condition.network.SyncDebugModePacket;

/**
 * Регистрация и обработка команд мода Condition.
 */
@Mod.EventBusSubscriber(modid = Condition.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ConditionCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("condition")
            .requires(source -> source.hasPermission(2)) // Требуются права оператора
            .then(Commands.literal("debug")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(context -> {
                        boolean enabled = BoolArgumentType.getBool(context, "enabled");
                        Condition.debugMode = enabled;

                        // Рассылаем пакет всем игрокам на сервере
                        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                            NetworkHandler.sendToPlayer(new SyncDebugModePacket(enabled), player);
                        }

                        context.getSource().sendSuccess(() -> Component.literal("§a[Condition] Режим отладки установлен в: " + enabled), true);
                        return 1;
                    })
                )
            )
        );
    }
}
