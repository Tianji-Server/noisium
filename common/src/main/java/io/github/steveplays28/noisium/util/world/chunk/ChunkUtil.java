package io.github.steveplays28.noisium.util.world.chunk;

import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChunkUtil {
	/**
	 * Sends a {@link WorldChunk} to all players in the specified world.
	 *
	 * @param serverWorld The world the {@link WorldChunk} resides in.
	 * @param worldChunk  The {@link WorldChunk}.
	 */
	public static void sendWorldChunkToPlayer(@NotNull ServerWorld serverWorld, @NotNull WorldChunk worldChunk) {
		var chunkDataS2CPacket = new ChunkDataS2CPacket(worldChunk, serverWorld.getLightingProvider(), null, null);

		for (int i = 0; i < serverWorld.getPlayers().size(); i++) {
			serverWorld.getPlayers().get(i).sendChunkPacket(worldChunk.getPos(), chunkDataS2CPacket);
		}
	}

	/**
	 * Sends a {@link List} of {@link WorldChunk}s to all players in the specified world.
	 * WARNING: This method blocks the server thread. Prefer using {@link ChunkUtil#sendWorldChunksToPlayerAsync(ServerWorld, List)} instead.
	 *
	 * @param serverWorld The world the {@link WorldChunk} resides in.
	 * @param worldChunks The {@link List} of {@link WorldChunk}s.
	 */
	@SuppressWarnings("ForLoopReplaceableByForEach")
	public static void sendWorldChunksToPlayer(@NotNull ServerWorld serverWorld, @NotNull List<WorldChunk> worldChunks) {
		// TODO: Send a whole batch of chunks to the player at once to save on network traffic
		for (int i = 0; i < worldChunks.size(); i++) {
			sendWorldChunkToPlayer(serverWorld, worldChunks.get(i));
		}
	}

	/**
	 * Sends a {@link List} of {@link CompletableFuture<WorldChunk>}s to all players in the specified world.
	 * This method is ran asynchronously.
	 *
	 * @param serverWorld       The world the {@link WorldChunk} resides in.
	 * @param worldChunkFutures The {@link List} of {@link CompletableFuture<WorldChunk>}s
	 */
	@SuppressWarnings("ForLoopReplaceableByForEach")
	public static void sendWorldChunksToPlayerAsync(@NotNull ServerWorld serverWorld, @NotNull List<CompletableFuture<WorldChunk>> worldChunkFutures) {
		// TODO: Send a whole batch of chunks to the player at once to save on network traffic
		for (int i = 0; i < worldChunkFutures.size(); i++) {
			worldChunkFutures.get(i).whenCompleteAsync((worldChunk, throwable) -> sendWorldChunkToPlayer(serverWorld, worldChunk));
		}
	}

	/**
	 * Sends a light update to a {@link List} of players.
	 *
	 * @param players          The {@link List} of players.
	 * @param lightingProvider The {@link LightingProvider} of the world.
	 * @param chunkPos         The {@link ChunkPos} at which the light update happened.
	 * @param skyLightBits     The skylight {@link BitSet}.
	 * @param blockLightBits   The blocklight {@link BitSet}.
	 */
	@SuppressWarnings("ForLoopReplaceableByForEach")
	public static void sendLightUpdateToPlayers(@NotNull List<ServerPlayerEntity> players, @NotNull LightingProvider lightingProvider, @NotNull ChunkPos chunkPos, @NotNull BitSet skyLightBits, @NotNull BitSet blockLightBits) {
		for (int i = 0; i < players.size(); i++) {
			players.get(i).networkHandler.sendPacket(new LightUpdateS2CPacket(chunkPos, lightingProvider, skyLightBits, blockLightBits));
		}
	}

	/**
	 * Sends a block update to a {@link List} of players.
	 *
	 * @param players    The {@link List} of players.
	 * @param blockPos   The {@link BlockPos} of the block update that should be sent to the {@link List} of players.
	 * @param blockState The {@link BlockState} at the specified {@link BlockPos} of the block update that should be sent to the {@link List} of players.
	 */
	@SuppressWarnings("ForLoopReplaceableByForEach")
	public static void sendBlockUpdateToPlayers(@NotNull List<ServerPlayerEntity> players, @NotNull BlockPos blockPos, @NotNull BlockState blockState) {
		for (int i = 0; i < players.size(); i++) {
			players.get(i).networkHandler.sendPacket(new BlockUpdateS2CPacket(blockPos, blockState));
		}
	}
}
