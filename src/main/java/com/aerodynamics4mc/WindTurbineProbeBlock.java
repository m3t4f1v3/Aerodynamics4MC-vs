package com.aerodynamics4mc;

import java.util.Locale;

import com.aerodynamics4mc.api.GameplayWindSample;
import com.mojang.serialization.MapCodec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class WindTurbineProbeBlock extends BlockWithEntity {
    public static final MapCodec<WindTurbineProbeBlock> CODEC = createCodec(WindTurbineProbeBlock::new);

    public WindTurbineProbeBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new WindTurbineProbeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) {
            return null;
        }
        return validateTicker(type, ModBlocks.WIND_TURBINE_PROBE_BLOCK_ENTITY, WindTurbineProbeBlockEntity::tick);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return showStatus(state, world, pos, player);
    }

    @Override
    protected ActionResult onUseWithItem(
        ItemStack stack,
        BlockState state,
        World world,
        BlockPos pos,
        PlayerEntity player,
        Hand hand,
        BlockHitResult hit
    ) {
        return showStatus(state, world, pos, player);
    }

    @Override
    protected boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return redstonePower(world, pos);
    }

    @Override
    protected int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return redstonePower(world, pos);
    }

    private ActionResult showStatus(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.PASS;
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof WindTurbineProbeBlockEntity probe)) {
            return ActionResult.PASS;
        }
        probe.sampleNow(serverWorld, state);
        if (!probe.hasSample()) {
            player.sendMessage(Text.translatable("message.aerodynamics4mc.wind_turbine_probe.no_flow").formatted(Formatting.GRAY), false);
            return ActionResult.SUCCESS_SERVER;
        }

        GameplayWindSample sample = probe.lastSample();
        player.sendMessage(
            Text.translatable(
                "message.aerodynamics4mc.wind_turbine_probe.status",
                format(probe.lastPowerWatts()),
                probe.redstonePower()
            ).formatted(Formatting.GOLD),
            false
        );
        player.sendMessage(
            Text.translatable(
                "message.aerodynamics4mc.wind_turbine_probe.wind",
                format(sample.effectiveSpeedMetersPerSecond()),
                format(sample.meanSpeedMetersPerSecond()),
                format(sample.gustVelocity().length()),
                signed(sample.updraftMetersPerSecond())
            ).formatted(Formatting.AQUA),
            false
        );
        player.sendMessage(
            Text.translatable(
                "message.aerodynamics4mc.wind_turbine_probe.source",
                sample.sourceLevel().name(),
                sample.authority().name(),
                percent(sample.confidence()),
                percent(sample.shelterFactor()),
                format(sample.turbulenceIntensity())
            ).formatted(Formatting.GRAY),
            false
        );
        return ActionResult.SUCCESS_SERVER;
    }

    private static int redstonePower(BlockView world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof WindTurbineProbeBlockEntity probe) {
            return probe.redstonePower();
        }
        return 0;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String signed(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    private static String percent(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
        return String.format(Locale.ROOT, "%.0f%%", clamped * 100.0);
    }
}
