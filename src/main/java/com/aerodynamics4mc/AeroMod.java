package com.aerodynamics4mc;

import com.aerodynamics4mc.net.AeroNetworking;
import com.aerodynamics4mc.runtime.AeroServerRuntime;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Predicate;
import java.util.function.Supplier;

@Mod("aerodynamics4mc")
public class AeroMod {
    public static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel CHANNEL;
    
    public AeroMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Register the DeferredRegisters
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModBlocks.BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // create network channel and register messages
            CHANNEL = NetworkRegistry.newSimpleChannel(
                ResourceLocation.fromNamespaceAndPath(ModBlocks.MOD_ID, "main"),
                (Supplier<String>) () -> PROTOCOL_VERSION,
                (Predicate<String>) PROTOCOL_VERSION::equals,
                (Predicate<String>) PROTOCOL_VERSION::equals
            );
            AeroNetworking.registerChannel(CHANNEL);
            AeroServerRuntime.init();
        });
    }
}
