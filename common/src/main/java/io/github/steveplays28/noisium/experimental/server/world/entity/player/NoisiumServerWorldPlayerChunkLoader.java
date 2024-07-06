package io.github.steveplays28.noisium.experimental.server.world.entity.player;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import io.github.steveplays28.noisium.experimental.util.world.chunk.ChunkUtil;
import net.minecraft.network.packet.s2c.play.ChunkRenderDistanceCenterS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class NoisiumServerWorldPlayerChunkLoader {
	private final @NotNull ServerWorld serverWorld;
	private final @NotNull Function<ChunkPos, CompletableFuture<WorldChunk>> worldChunkLoadFunction;
	private final @NotNull Consumer<ChunkPos> worldChunkUnloadConsumer;
	private final @NotNull Supplier<Integer> serverViewDistanceSupplier;

	private final @NotNull Executor threadPoolExecutor;
	private final @NotNull Map<Integer, Vec3d> previousPlayerPositions;

	public NoisiumServerWorldPlayerChunkLoader(@NotNull ServerWorld serverWorld, @NotNull Function<ChunkPos, CompletableFuture<WorldChunk>> worldChunkLoadFunction, @NotNull Consumer<ChunkPos> worldChunkUnloadConsumer, @NotNull Supplier<Integer> serverViewDistanceSupplier) {
		this.serverWorld = serverWorld;
		this.worldChunkLoadFunction = worldChunkLoadFunction;
		this.worldChunkUnloadConsumer = worldChunkUnloadConsumer;
		this.serverViewDistanceSupplier = serverViewDistanceSupplier;

		this.threadPoolExecutor = Executors.newFixedThreadPool(
				1, new ThreadFactoryBuilder().setNameFormat("Noisium Server Player Chunk Loader %d").build());
		this.previousPlayerPositions = new HashMap<>();

		// TODO: Send chunks to player on join
		PlayerEvent.PLAYER_JOIN.register(player -> {
			if (!player.getServerWorld().equals(serverWorld)) {
				return;
			}

			previousPlayerPositions.put(player.getId(), player.getPos());
		});
		PlayerEvent.PLAYER_QUIT.register(player -> previousPlayerPositions.remove(player.getId()));
		TickEvent.SERVER_LEVEL_POST.register(instance -> {
			if (!instance.equals(serverWorld)) {
				return;
			}

			tick();
		});
	}

	// TODO: Enable ticking/update chunk tracking in ServerEntityManager
	@SuppressWarnings("ForLoopReplaceableByForEach")
	private void tick() {
		@NotNull var players = serverWorld.getPlayers();
		if (players.isEmpty() || previousPlayerPositions.isEmpty()) {
			return;
		}

		for (int i = 0; i < players.size(); i++) {
			@NotNull var player = players.get(i);
			@NotNull var playerBlockPos = player.getBlockPos();
			@NotNull var previousPlayerPos = previousPlayerPositions.get(player.getId());
			if (playerBlockPos.isWithinDistance(previousPlayerPos, 16d)) {
				continue;
			}

			// Send world chunks that should be loaded to the player asynchronously
			@NotNull var previousPlayerBlockPos = new BlockPos(
					Math.round((float) previousPlayerPos.getX()), Math.round((float) previousPlayerPos.getY()),
					Math.round((float) previousPlayerPos.getZ())
			);
			@NotNull var previousPlayerChunkPositionsInServerViewDistance = ChunkUtil.getChunkPositionsAtPositionInRadius(
					new ChunkPos(previousPlayerBlockPos), serverViewDistanceSupplier.get());
			@NotNull var playerChunkPositionsInServerViewDistance = ChunkUtil.getChunkPositionsAtPositionInRadius(
					new ChunkPos(playerBlockPos), serverViewDistanceSupplier.get());
			@NotNull final var chunkPositionsToLoad = ChunkUtil.getChunkPositionDifferences(
					playerChunkPositionsInServerViewDistance, previousPlayerChunkPositionsInServerViewDistance);
			for (int chunkPositionsToLoadIndex = 0; chunkPositionsToLoadIndex < chunkPositionsToLoad.size(); chunkPositionsToLoadIndex++) {
				ChunkUtil.sendWorldChunkToPlayerAsync(
						serverWorld, worldChunkLoadFunction.apply(chunkPositionsToLoad.get(chunkPositionsToLoadIndex)), threadPoolExecutor);
			}

			// Send new render distance center to the player asynchronously
			CompletableFuture.runAsync(() -> player.networkHandler.sendPacket(
					new ChunkRenderDistanceCenterS2CPacket(
							ChunkSectionPos.getSectionCoord(playerBlockPos.getX()),
							ChunkSectionPos.getSectionCoord(playerBlockPos.getZ())
					)), threadPoolExecutor);

			// Unload world chunks that aren't required anymore asynchronously
			// TODO: Check if other players are still in range of these chunks
			//  Unload chunks at the end of the players fori loop instead
			@NotNull final var chunkPositionsToUnload = ChunkUtil.getChunkPositionDifferences(
					previousPlayerChunkPositionsInServerViewDistance, playerChunkPositionsInServerViewDistance);
			for (int chunkPositionsToUnloadIndex = 0; chunkPositionsToUnloadIndex < chunkPositionsToUnload.size(); chunkPositionsToUnloadIndex++) {
				worldChunkUnloadConsumer.accept(chunkPositionsToUnload.get(chunkPositionsToUnloadIndex));
			}

			previousPlayerPositions.put(player.getId(), player.getPos());
		}
	}
}
