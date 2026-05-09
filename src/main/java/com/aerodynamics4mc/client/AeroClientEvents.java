package com.aerodynamics4mc.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "aerodynamics4mc", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AeroClientEvents {

    private AeroClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> render = Commands.literal("render")
            .executes(ctx -> { withInstance(AeroClientMod::renderStatus); return 1; })
            .then(renderToggle("vectors",
                AeroClientMod::setRenderVelocityVectors))
            .then(renderToggle("streamlines",
                AeroClientMod::setRenderStreamlines));

        dispatcher.register(Commands.literal("aero")
            .executes(ctx -> { withInstance(AeroClientMod::renderStatus); return 1; })
            .then(render));

        dispatcher.register(Commands.literal("aero_client_l2")
            .executes(ctx -> { withInstance(AeroClientMod::clientL2Status); return 1; })
            .then(Commands.literal("on")
                .executes(ctx -> { withInstance(m -> m.setClientL2Experimental(true)); return 1; }))
            .then(Commands.literal("off")
                .executes(ctx -> { withInstance(m -> m.setClientL2Experimental(false)); return 1; })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> renderToggle(
            String name, java.util.function.BiConsumer<AeroClientMod, Boolean> setter) {
        return Commands.literal(name)
            .then(Commands.literal("on")
                .executes(ctx -> { withInstance(m -> setter.accept(m, true)); return 1; }))
            .then(Commands.literal("off")
                .executes(ctx -> { withInstance(m -> setter.accept(m, false)); return 1; }));
    }

    private static void withInstance(java.util.function.Consumer<AeroClientMod> action) {
        AeroClientMod instance = AeroClientMod.instance;
        if (instance != null) {
            action.accept(instance);
        }
    }
}
