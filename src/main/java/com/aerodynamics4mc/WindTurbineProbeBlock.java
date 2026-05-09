package com.aerodynamics4mc;

import java.util.Locale;

import com.aerodynamics4mc.api.GameplayWindSample;

import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class WindTurbineProbeBlock extends BaseEntityBlock {
    public WindTurbineProbeBlock(Properties settings) {
        super(settings);
    }

    public WindTurbineProbeBlock() {
        this(BlockBehaviour.Properties.of());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindTurbineProbeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlocks.WIND_TURBINE_PROBE_BLOCK_ENTITY.get(), WindTurbineProbeBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return showStatus(state, level, pos, player);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return redstonePower(level, pos);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return redstonePower(level, pos);
    }

    private InteractionResult showStatus(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof WindTurbineProbeBlockEntity probe)) {
            return InteractionResult.PASS;
        }
        probe.sampleNow(serverLevel, state);
        if (!probe.hasSample()) {
            player.displayClientMessage(Component.translatable("message.aerodynamics4mc.wind_turbine_probe.no_flow").withStyle(ChatFormatting.GRAY), false);
            return InteractionResult.SUCCESS;
        }

        GameplayWindSample sample = probe.lastSample();
        player.displayClientMessage(
            Component.translatable(
                "message.aerodynamics4mc.wind_turbine_probe.status",
                format(probe.lastPowerWatts()),
                probe.redstonePower()
            ).withStyle(ChatFormatting.GOLD),
            false
        );
        player.displayClientMessage(
            Component.translatable(
                "message.aerodynamics4mc.wind_turbine_probe.wind",
                format(sample.effectiveSpeedMetersPerSecond()),
                format(sample.meanSpeedMetersPerSecond()),
                format(sample.gustVelocity().length()),
                signed(sample.updraftMetersPerSecond())
            ).withStyle(ChatFormatting.AQUA),
            false
        );
        player.displayClientMessage(
            Component.translatable(
                "message.aerodynamics4mc.wind_turbine_probe.source",
                sample.sourceLevel().name(),
                sample.authority().name(),
                percent(sample.confidence()),
                percent(sample.shelterFactor()),
                format(sample.turbulenceIntensity())
            ).withStyle(ChatFormatting.GRAY),
            false
        );
        return InteractionResult.SUCCESS;
    }

    private static int redstonePower(BlockGetter level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
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
