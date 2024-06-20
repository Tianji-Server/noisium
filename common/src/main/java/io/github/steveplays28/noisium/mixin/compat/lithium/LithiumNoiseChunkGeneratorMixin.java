package io.github.steveplays28.noisium.mixin.compat.lithium;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.util.Util;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(NoiseChunkGenerator.class)
public abstract class LithiumNoiseChunkGeneratorMixin extends ChunkGenerator {
	@Shadow
	protected abstract Chunk populateNoise(Blender blender, StructureAccessor structureAccessor, NoiseConfig noiseConfig, Chunk chunk, int minimumCellY, int cellHeight);

	public LithiumNoiseChunkGeneratorMixin(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Redirect(method = "populateNoise(Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;II)Lnet/minecraft/world/chunk/Chunk;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/ChunkSection;setBlockState(IIILnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;"))
	private BlockState noisium$populateNoiseWrapSetBlockStateOperation(ChunkSection chunkSection, int chunkSectionBlockPosX, int chunkSectionBlockPosY, int chunkSectionBlockPosZ, BlockState blockState, boolean lock) {
		// Set the blockstate in the palette storage directly to improve performance
		var blockStateId = chunkSection.blockStateContainer.data.palette.index(blockState);
		chunkSection.blockStateContainer.data.storage().set(
				chunkSection.blockStateContainer.paletteProvider.computeIndex(chunkSectionBlockPosX, chunkSectionBlockPosY,
						chunkSectionBlockPosZ
				), blockStateId);

		return blockState;
	}

	@Inject(method = "populateNoise(Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;)Ljava/util/concurrent/CompletableFuture;", at = @At(value = "RETURN", shift = At.Shift.BEFORE), cancellable = true)
	private void noisium$populateNoiseInject(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk, CallbackInfoReturnable<CompletableFuture<Chunk>> cir, @Local GenerationShapeConfig generationShapeConfig, @Local(ordinal = 0) int minimumY, @Local(ordinal = 1) int minimumYFloorDiv, @Local(ordinal = 2) int generationShapeHeightFloorDiv) {
		if (generationShapeHeightFloorDiv <= 0) {
			cir.setReturnValue(CompletableFuture.completedFuture(chunk));
			return;
		}

		cir.setReturnValue(CompletableFuture.supplyAsync(Util.debugSupplier("wgen_fill_noise", () -> {
			int startingChunkSectionIndex = chunk.getSectionIndex(
					generationShapeHeightFloorDiv * generationShapeConfig.verticalCellBlockCount() - 1 + minimumY);
			int minimumYChunkSectionIndex = chunk.getSectionIndex(minimumY);
			var chunkSections = chunk.getSectionArray();
			for (int chunkSectionIndex = startingChunkSectionIndex; chunkSectionIndex >= minimumYChunkSectionIndex; --chunkSectionIndex) {
				chunkSections[chunkSectionIndex].lock();
			}

			Chunk chunkWithNoise;
			try {
				chunkWithNoise = this.populateNoise(
						blender, structureAccessor, noiseConfig, chunk, minimumYFloorDiv, generationShapeHeightFloorDiv);
			} finally {
				// Replace an enhanced for loop with a fori loop
				// Also run calculateCounts() on every chunk section to add Lithium compatibility
				for (int chunkSectionIndex = startingChunkSectionIndex; chunkSectionIndex >= minimumYChunkSectionIndex; --chunkSectionIndex) {
					var chunkSection = chunkSections[chunkSectionIndex];

					chunkSection.calculateCounts();
					chunkSection.unlock();
				}
			}

			return chunkWithNoise;
		}), Util.getMainWorkerExecutor()));
	}
}
