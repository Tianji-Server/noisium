package io.github.steveplays28.noisium.mixin.experimental.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkRegion.class)
public abstract class ChunkRegionMixin implements StructureWorldAccess {
	@Shadow
	public abstract boolean isChunkLoaded(int chunkX, int chunkZ);

	@Shadow
	public abstract int getSeaLevel();

	@Inject(method = "needsBlending", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$cancelBlending(@NotNull ChunkPos chunkPosition, int checkRadius, @NotNull CallbackInfoReturnable<Boolean> cir) {
		// TODO: Reimplement chunk blending
		cir.setReturnValue(false);
	}

	@Inject(method = "getBlockState", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$returnAirBlockStateIfChunkIsUnloaded(@NotNull BlockPos blockPos, @NotNull CallbackInfoReturnable<BlockState> cir) {
		if (!isChunkLoaded(ChunkSectionPos.getSectionCoord(blockPos.getX()), ChunkSectionPos.getSectionCoord(blockPos.getZ()))) {
			cir.setReturnValue(Blocks.AIR.getDefaultState());
		}
	}

	@Inject(method = "setBlockState", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$setBlockStateToSaveDataIfChunkIsUnloaded(@NotNull BlockPos blockPos, @NotNull BlockState blockState, int flags, int maxUpdateDepth, @NotNull CallbackInfoReturnable<Boolean> cir) {
		if (!isChunkLoaded(ChunkSectionPos.getSectionCoord(blockPos.getX()), ChunkSectionPos.getSectionCoord(blockPos.getZ()))) {
			// TODO: Set blockstate in save data
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getFluidState", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$returnEmptyFluidStateIfChunkIsUnloaded(@NotNull BlockPos blockPos, @NotNull CallbackInfoReturnable<FluidState> cir) {
		if (!isChunkLoaded(ChunkSectionPos.getSectionCoord(blockPos.getX()), ChunkSectionPos.getSectionCoord(blockPos.getZ()))) {
			cir.setReturnValue(Fluids.EMPTY.getDefaultState());
		}
	}

	@Inject(method = "getTopY", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getTopYReturnSeaLevelIfChunkIsUnloaded(@NotNull Heightmap.Type heightmap, int blockPositionX, int blockPositionZ, @NotNull CallbackInfoReturnable<Integer> cir) {
		if (!isChunkLoaded(ChunkSectionPos.getSectionCoord(blockPositionX), ChunkSectionPos.getSectionCoord(blockPositionZ))) {
			cir.setReturnValue(this.getSeaLevel());
		}
	}

	@Inject(method = "getBlockEntity", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$returnEmptyBlockEntityIfChunkIsUnloaded(@NotNull BlockPos blockPos, @NotNull CallbackInfoReturnable<BlockEntity> cir) {
		if (!isChunkLoaded(ChunkSectionPos.getSectionCoord(blockPos.getX()), ChunkSectionPos.getSectionCoord(blockPos.getZ()))) {
			cir.setReturnValue(null);
		}
	}
}
