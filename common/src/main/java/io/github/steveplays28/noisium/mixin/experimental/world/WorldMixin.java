package io.github.steveplays28.noisium.mixin.experimental.world;

import io.github.steveplays28.noisium.experimental.extension.world.server.NoisiumServerWorldExtension;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldMixin {
	@Shadow
	public abstract boolean isClient();

	@Inject(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getChunkFromNoisiumServerChunkManager(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<Chunk> cir) {
		if (this.isClient()) {
			return;
		}

		var noisiumServerWorldChunkManager = ((NoisiumServerWorldExtension) this).noisium$getServerWorldChunkManager();
		var chunkPosition = new ChunkPos(chunkX, chunkZ);
		if (!noisiumServerWorldChunkManager.isChunkLoaded(chunkPosition)) {
			cir.setReturnValue(null);
			return;
		}

		cir.setReturnValue(noisiumServerWorldChunkManager.getChunk(chunkPosition));
	}

	@Inject(method = "getBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getChunk(II)Lnet/minecraft/world/chunk/WorldChunk;"), cancellable = true)
	private void noisium$getBlockStateGetChunkFromNoisiumServerChunkManager(@NotNull BlockPos blockPosition, @NotNull CallbackInfoReturnable<BlockState> cir) {
		if (this.isClient()) {
			return;
		}

		var noisiumServerWorldChunkManager = ((NoisiumServerWorldExtension) this).noisium$getServerWorldChunkManager();
		var chunkPosition = new ChunkPos(blockPosition);
		if (!noisiumServerWorldChunkManager.isChunkLoaded(chunkPosition)) {
			cir.setReturnValue(Blocks.BARRIER.getDefaultState());
			return;
		}

		cir.setReturnValue(noisiumServerWorldChunkManager.getChunk(chunkPosition).getBlockState(blockPosition));
	}

	@Inject(method = "getFluidState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getWorldChunk(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/chunk/WorldChunk;"), cancellable = true)
	private void noisium$getFluidStateGetChunkFromNoisiumServerChunkManager(@NotNull BlockPos blockPosition, @NotNull CallbackInfoReturnable<FluidState> cir) {
		if (this.isClient()) {
			return;
		}

		var noisiumServerWorldChunkManager = ((NoisiumServerWorldExtension) this).noisium$getServerWorldChunkManager();
		var chunkPosition = new ChunkPos(blockPosition);
		if (!noisiumServerWorldChunkManager.isChunkLoaded(chunkPosition)) {
			cir.setReturnValue(Fluids.EMPTY.getDefaultState());
			return;
		}

		cir.setReturnValue(noisiumServerWorldChunkManager.getChunk(chunkPosition).getFluidState(blockPosition));
	}
}
