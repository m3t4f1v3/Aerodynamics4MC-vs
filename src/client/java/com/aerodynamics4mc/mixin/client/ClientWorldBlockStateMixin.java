package com.aerodynamics4mc.mixin.client;

import java.util.ArrayDeque;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.aerodynamics4mc.client.AeroClientMod;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Mixin(World.class)
abstract class ClientWorldBlockStateMixin {
    @Unique
    private static final ThreadLocal<ArrayDeque<ChangeContext>> A4MC_CLIENT_BLOCK_CHANGE_STACK =
        ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("HEAD")
    )
    private void a4mc$captureClientOldState(
        BlockPos pos,
        BlockState state,
        int flags,
        int maxUpdateDepth,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!((Object) this instanceof ClientWorld world)) {
            return;
        }
        A4MC_CLIENT_BLOCK_CHANGE_STACK.get().push(new ChangeContext(pos.toImmutable(), world.getBlockState(pos)));
    }

    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("RETURN")
    )
    private void a4mc$notifyClientBlockChange(
        BlockPos pos,
        BlockState state,
        int flags,
        int maxUpdateDepth,
        CallbackInfoReturnable<Boolean> cir
    ) {
        ArrayDeque<ChangeContext> stack = A4MC_CLIENT_BLOCK_CHANGE_STACK.get();
        ChangeContext context = stack.isEmpty() ? null : stack.pop();
        if (stack.isEmpty()) {
            A4MC_CLIENT_BLOCK_CHANGE_STACK.remove();
        }
        if (context == null || !cir.getReturnValueZ()) {
            return;
        }
        if (!((Object) this instanceof ClientWorld world)) {
            return;
        }
        AeroClientMod.notifyBlockStateChanged(world, context.pos(), context.oldState(), world.getBlockState(context.pos()));
    }

    @Unique
    private record ChangeContext(BlockPos pos, BlockState oldState) {
    }
}
