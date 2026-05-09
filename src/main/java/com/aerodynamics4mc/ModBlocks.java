package com.aerodynamics4mc;

import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    public static final String MOD_ID = "aerodynamics4mc";

    // Create deferred registries
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    // Fan block and item
    public static final RegistryObject<Block> FAN_BLOCK = BLOCKS.register("fan",
        () -> new FanBlock(BlockBehaviour.Properties.of().strength(1.5f))
    );
    public static final RegistryObject<Item> FAN_ITEM = ITEMS.register("fan",
        () -> new BlockItem(FAN_BLOCK.get(), new Item.Properties())
    );

    // Duct block and item
    public static final RegistryObject<Block> DUCT_BLOCK = BLOCKS.register("duct",
        () -> new DuctBlock(BlockBehaviour.Properties.of().strength(1.0f))
    );
    public static final RegistryObject<Item> DUCT_ITEM = ITEMS.register("duct",
        () -> new BlockItem(DUCT_BLOCK.get(), new Item.Properties())
    );

    // Wind meter item
    public static final RegistryObject<Item> WIND_METER_ITEM = ITEMS.register("wind_meter",
        () -> new WindMeterItem(new Item.Properties().stacksTo(1))
    );

    // Wind turbine probe block and item
    public static final RegistryObject<Block> WIND_TURBINE_PROBE_BLOCK = BLOCKS.register("wind_turbine_probe",
        () -> new WindTurbineProbeBlock(BlockBehaviour.Properties.of().strength(1.5f))
    );
    public static final RegistryObject<Item> WIND_TURBINE_PROBE_ITEM = ITEMS.register("wind_turbine_probe",
        () -> new BlockItem(WIND_TURBINE_PROBE_BLOCK.get(), new Item.Properties())
    );

    // Block entities
    public static final RegistryObject<BlockEntityType<FanBlockEntity>> FAN_BLOCK_ENTITY = BLOCK_ENTITIES.register("fan",
        () -> BlockEntityType.Builder.of(FanBlockEntity::new, FAN_BLOCK.get()).build(null)
    );
    public static final RegistryObject<BlockEntityType<WindTurbineProbeBlockEntity>> WIND_TURBINE_PROBE_BLOCK_ENTITY = BLOCK_ENTITIES.register("wind_turbine_probe",
        () -> BlockEntityType.Builder.of(WindTurbineProbeBlockEntity::new, WIND_TURBINE_PROBE_BLOCK.get()).build(null)
    );

    private ModBlocks() {
    }
}
