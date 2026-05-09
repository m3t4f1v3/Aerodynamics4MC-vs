package com.aerodynamics4mc;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;


public class DuctBlock extends Block {
    public DuctBlock(Properties settings) {
        super(settings);
    }

    public DuctBlock() {
        this(BlockBehaviour.Properties.of());
    }

}
