package io.github.steveplays28.noisium.server.world.chunk.event;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.NotNull;

public interface NoisiumServerChunkEvent {
	/**
	 * @see WorldChunkGenerated
	 */
	Event<WorldChunkGenerated> WORLD_CHUNK_GENERATED = EventFactory.createLoop();

	/**
	 * @see WorldChunkGenerated
	 */
	Event<LightUpdate> LIGHT_UPDATE = EventFactory.createLoop();

	/**
	 * @see WorldChunkGenerated
	 */
	Event<BlockChange> BLOCK_CHANGE = EventFactory.createLoop();

	@FunctionalInterface
	interface WorldChunkGenerated {
		/**
		 * Invoked after a {@link WorldChunk} has been generated by {@link io.github.steveplays28.noisium.server.world.NoisiumServerWorldChunkManager}.
		 *
		 * @param worldChunk The generated {@link WorldChunk}.
		 */
		void onWorldChunkGenerated(WorldChunk worldChunk);
	}

	@FunctionalInterface
	interface LightUpdate {
		/**
		 * Invoked before a {@link WorldChunk} has had a light update processed by {@link io.github.steveplays28.noisium.server.world.NoisiumServerWorldChunkManager}.
		 *
		 * @param lightType            The {@link LightType} of the {@link WorldChunk}.
		 * @param chunkSectionPosition The {@link ChunkSectionPos} of the {@link WorldChunk}.
		 */
		void onLightUpdate(@NotNull LightType lightType, @NotNull ChunkSectionPos chunkSectionPosition);
	}

	@FunctionalInterface
	interface BlockChange {
		/**
		 * Invoked before a {@link WorldChunk} has had a block change processed by {@link io.github.steveplays28.noisium.server.world.NoisiumServerWorldChunkManager}.
		 *
		 * @param blockPos      The {@link BlockPos} where the block change has happened.
		 * @param oldBlockState The old {@link BlockState} at the {@link BlockPos}.
		 * @param newBlockState The new {@link BlockState} at the {@link BlockPos}.
		 */
		void onBlockChange(@NotNull BlockPos blockPos, @NotNull BlockState oldBlockState, @NotNull BlockState newBlockState);
	}
}
