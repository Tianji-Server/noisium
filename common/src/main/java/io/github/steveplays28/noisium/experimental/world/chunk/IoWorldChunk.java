package io.github.steveplays28.noisium.experimental.world.chunk;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.gen.chunk.BlendingData;
import net.minecraft.world.tick.BasicTickScheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link ServerWorld} {@link Chunk} with disk (IO) access.
 */
public class IoWorldChunk extends Chunk {
	public IoWorldChunk(@NotNull ChunkPos chunkPosition, @NotNull HeightLimitView heightLimitView, @NotNull Registry<Biome> biomeRegistry, @Nullable BlendingData blendingData) {
		super(chunkPosition, UpgradeData.NO_UPGRADE_DATA, heightLimitView, biomeRegistry, 0L, null, blendingData);
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
	public ChunkStatus getStatus() {
		return ChunkStatus.FULL;
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

	@Override
	public void addEntity(Entity entity) {

	}
}
