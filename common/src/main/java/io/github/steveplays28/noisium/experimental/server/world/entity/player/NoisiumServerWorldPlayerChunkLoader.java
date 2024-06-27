package io.github.steveplays28.noisium.experimental.server.world.entity.player;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import io.github.steveplays28.noisium.experimental.util.world.chunk.ChunkUtil;
import net.minecraft.network.packet.s2c.play.ChunkRenderDistanceCenterS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

// TODO: Dynamically enable/disable instances of this class if the world this class is tied to gets loaded/unloaded
public class NoisiumServerWorldPlayerChunkLoader {
	private final @NotNull ServerWorld serverWorld;
	private final @NotNull BiFunction<ChunkPos, Integer, Map<ChunkPos, CompletableFuture<WorldChunk>>> worldChunksSupplier;

	private final @NotNull Executor threadPoolExecutor;
	private final @NotNull Map<Integer, Vec3d> previousPlayerPositions;

	public NoisiumServerWorldPlayerChunkLoader(@NotNull ServerWorld serverWorld, @NotNull BiFunction<ChunkPos, Integer, Map<ChunkPos, CompletableFuture<WorldChunk>>> worldChunksSupplier) {
		this.serverWorld = serverWorld;
		this.worldChunksSupplier = worldChunksSupplier;

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
		var players = serverWorld.getPlayers();
		if (players.isEmpty() || previousPlayerPositions.isEmpty()) {
			return;
		}

		for (int i = 0; i < players.size(); i++) {
			var player = players.get(i);
			var playerBlockPos = player.getBlockPos();
			if (playerBlockPos.isWithinDistance(previousPlayerPositions.get(player.getId()), 16d)) {
				continue;
			}

			var worldChunks = worldChunksSupplier.apply(new ChunkPos(playerBlockPos), 6);
			ChunkUtil.sendWorldChunksToPlayerAsync(serverWorld, new ArrayList<>(worldChunks.values()), threadPoolExecutor);
			CompletableFuture.runAsync(() -> player.networkHandler.sendPacket(
					new ChunkRenderDistanceCenterS2CPacket(
							ChunkSectionPos.getSectionCoord(playerBlockPos.getX()),
							ChunkSectionPos.getSectionCoord(playerBlockPos.getZ())
					)), threadPoolExecutor);
			previousPlayerPositions.put(player.getId(), player.getPos());
		}
	}
}
