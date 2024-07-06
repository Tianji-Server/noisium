package io.github.steveplays28.noisium.experimental.world.chunk;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.tick.BasicTickScheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link ServerWorld} {@link WorldChunk} with disk (IO) access.
 */
public class IoWorldChunk extends WorldChunk {
	public IoWorldChunk(@NotNull World world, @NotNull ChunkPos chunkPosition) {
		super(world, chunkPosition);
	}

	@Override
	public @Nullable BasicTickScheduler<Block> getBlockTickScheduler() {
		return null;
	}

	@Override
	public @Nullable BasicTickScheduler<Fluid> getFluidTickScheduler() {
		return null;
	}

	@Override
	public @Nullable TickSchedulers getTickSchedulers() {
		return null;
	}

	@Override
	public @NotNull BlockState getBlockState(BlockPos blockPosition) {
		return Blocks.BARRIER.getDefaultState();
	}

	@Override
	public @Nullable BlockState setBlockState(@NotNull BlockPos blockPosition, @Nullable BlockState blockState, boolean moved) {
		return blockState;
	}

	@Override
	public @NotNull FluidState getFluidState(BlockPos blockPosition) {
		return Fluids.EMPTY.getDefaultState();
	}

	@Override
	public void setBlockEntity(BlockEntity blockEntity) {

	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos blockPosition) {
		return null;
	}

	@Override
	public @NotNull NbtCompound getPackedBlockEntityNbt(@NotNull BlockPos blockPosition) {
		return new NbtCompound();
	}

	@Override
	public void removeBlockEntity(BlockPos blockPosition) {

	}
}
