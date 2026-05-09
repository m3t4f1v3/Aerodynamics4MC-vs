package com.aerodynamics4mc.mixin;

import java.util.ArrayDeque;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.aerodynamics4mc.runtime.AeroServerRuntime;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

@Mixin(Level.class)
abstract class ServerLevelBlockStateMixin {
    @Unique
    private static final ThreadLocal<ArrayDeque<ChangeContext>> A4MC_BLOCK_CHANGE_STACK =
        ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD")
    )
    private void a4mc$captureOldState(
        BlockPos pos,
        BlockState state,
        int flags,
        int maxUpdateDepth,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!((Object) this instanceof ServerLevel level)) {
            return;
        }
        A4MC_BLOCK_CHANGE_STACK.get().push(new ChangeContext(pos.immutable(), level.getBlockState(pos)));
    }

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("RETURN")
    )
    private void a4mc$notifyBlockChange(
        BlockPos pos,
        BlockState state,
        int flags,
        int maxUpdateDepth,
        CallbackInfoReturnable<Boolean> cir
    ) {
        ArrayDeque<ChangeContext> stack = A4MC_BLOCK_CHANGE_STACK.get();
        ChangeContext context = stack.isEmpty() ? null : stack.pop();
        if (stack.isEmpty()) {
            A4MC_BLOCK_CHANGE_STACK.remove();
        }
        if (context == null || !cir.getReturnValueZ()) {
            return;
        }
        if (!((Object) this instanceof ServerLevel level)) {
            return;
        }
        AeroServerRuntime.notifyBlockStateChanged(level, context.pos(), context.oldState(), level.getBlockState(context.pos()));
    }

    @Unique
    private record ChangeContext(BlockPos pos, BlockState oldState) {
    }
}
