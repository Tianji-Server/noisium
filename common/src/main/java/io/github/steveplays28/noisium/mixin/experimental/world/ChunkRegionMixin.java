package io.github.steveplays28.noisium.mixin.experimental.world;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChunkRegion.class)
public abstract class ChunkRegionMixin implements StructureWorldAccess {

	@Shadow
	@Final
	private ServerWorld world;

	@Shadow
	@Final
	private ChunkPos lowerCorner;

	@Shadow
	@Final
	private List<Chunk> chunks;

	@Shadow
	@Final
	private int width;

	/**
	 * @return The loaded {@link Chunk} at the specified chunk position, or a dummy {@link ProtoChunk} if the specified chunk position isn't loaded.
	 * @author Steveplays28
	 * @reason Workaround for there not being {@code null} checks when the specified chunk position isn't in this {@link ChunkRegion}'s bounds.
	 */
	@Overwrite
	public @Nullable Chunk getChunk(int chunkPosX, int chunkPosZ, @NotNull ChunkStatus leastChunkStatus, boolean create) {
		if (!this.isChunkLoaded(chunkPosX, chunkPosZ)) {
			// TODO: Replace the ProtoChunk instances with null and add null checks where needed using ASM
			var protoChunk = new ProtoChunk(
					new ChunkPos(chunkPosX, chunkPosZ), UpgradeData.NO_UPGRADE_DATA, this.world, this.world.getRegistryManager().get(
					RegistryKeys.BIOME), null);
			protoChunk.setLightingProvider(world.getLightingProvider());
			protoChunk.setStatus(ChunkStatus.FULL);
			return protoChunk;
		}

		int i = chunkPosX - this.lowerCorner.x;
		int j = chunkPosZ - this.lowerCorner.z;
		var chunk = this.chunks.get(i + j * this.width);
		if (chunk.getStatus().isAtLeast(leastChunkStatus)) {
			return chunk;
		}

		// TODO: Replace the ProtoChunk instances with null and add null checks where needed using ASM
		var protoChunk = new ProtoChunk(
				new ChunkPos(chunkPosX, chunkPosZ), UpgradeData.NO_UPGRADE_DATA, this.world, this.world.getRegistryManager().get(
				RegistryKeys.BIOME), null);
		protoChunk.setLightingProvider(world.getLightingProvider());
		protoChunk.setStatus(ChunkStatus.FULL);
		return protoChunk;
	}

	@Inject(method = "needsBlending", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$cancelBlending(ChunkPos chunkPosition, int checkRadius, CallbackInfoReturnable<Boolean> cir) {
		// TODO: Reimplement chunk blending
		cir.setReturnValue(false);
	}
}
