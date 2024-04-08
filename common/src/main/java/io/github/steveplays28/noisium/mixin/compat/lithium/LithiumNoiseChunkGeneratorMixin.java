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
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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

	@SuppressWarnings("ForLoopReplaceableByForEach")
	@Inject(method = "populateNoise(Ljava/util/concurrent/Executor;Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;)Ljava/util/concurrent/CompletableFuture;", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/world/chunk/Chunk;getSectionIndex(I)I", ordinal = 1), cancellable = true)
	private void noisium$populateNoiseInject(Executor executor, Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk, CallbackInfoReturnable<CompletableFuture<Chunk>> cir, @Local(ordinal = 1) int minimumYFloorDiv, @Local(ordinal = 2) int generationShapeHeightFloorDiv, @Local(ordinal = 3) int startingChunkSectionIndex, @Local(ordinal = 4) int minimumYChunkSectionIndex) {
		ChunkSection[] chunkSections = new ChunkSection[startingChunkSectionIndex - minimumYChunkSectionIndex + 1];

		for (int chunkSectionIndex = startingChunkSectionIndex; chunkSectionIndex >= minimumYChunkSectionIndex; --chunkSectionIndex) {
			ChunkSection chunkSection = chunk.getSection(chunkSectionIndex);

			chunkSection.lock();
			chunkSections[chunkSectionIndex] = chunkSection;
		}

		cir.setReturnValue(CompletableFuture.supplyAsync(
				Util.debugSupplier(
						"wgen_fill_noise",
						() -> this.populateNoise(blender, structureAccessor, noiseConfig, chunk, minimumYFloorDiv,
								generationShapeHeightFloorDiv
						)
				), Util.getMainWorkerExecutor()).whenCompleteAsync((chunk2, throwable) -> {
			// Replace an enhanced for loop with a fori loop
			// Also run calculateCounts() on every chunk section to add Lithium compatibility
			for (int i = 0; i < chunkSections.length; i++) {
				var chunkSection = chunkSections[i];

				chunkSection.calculateCounts();
				chunkSection.unlock();
			}
		}, executor));
	}
}
