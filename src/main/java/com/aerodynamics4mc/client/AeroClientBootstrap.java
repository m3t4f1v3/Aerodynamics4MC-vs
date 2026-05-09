package com.aerodynamics4mc.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "aerodynamics4mc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AeroClientBootstrap {
    private AeroClientBootstrap() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            AeroClientMod clientMod = new AeroClientMod();
            clientMod.onInitializeClient();
        });
    }
}
