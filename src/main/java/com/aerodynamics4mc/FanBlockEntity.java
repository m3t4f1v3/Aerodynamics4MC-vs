package com.aerodynamics4mc;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

public class FanBlockEntity extends BlockEntity {
    public FanBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.FAN_BLOCK_ENTITY.get(), pos, state);
    }
}
