package io.github.steveplays28.noisium.server.world;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.datafixers.DataFixer;
import io.github.steveplays28.noisium.Noisium;
import io.github.steveplays28.noisium.mixin.accessor.NoiseChunkGeneratorAccessor;
import io.github.steveplays28.noisium.server.world.chunk.ServerChunkData;
import io.github.steveplays28.noisium.util.world.chunk.ChunkUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.light.LightSourceView;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class NoisiumServerWorldChunkManager implements ChunkProvider {
	private final ServerWorld serverWorld;
	private final ChunkGenerator chunkGenerator;
	private final PointOfInterestStorage pointOfInterestStorage;
	private final VersionedChunkStorage versionedChunkStorage;
	private final NoiseConfig noiseConfig;
	private final Executor threadPoolExecutor;
	private final Map<ChunkPos, ServerChunkData> loadedWorldChunks;

	public NoisiumServerWorldChunkManager(@NotNull ServerWorld serverWorld, @NotNull ChunkGenerator chunkGenerator, @NotNull Path worldDirectoryPath, DataFixer dataFixer) {
		this.serverWorld = serverWorld;
		this.chunkGenerator = chunkGenerator;

		this.pointOfInterestStorage = new PointOfInterestStorage(
				worldDirectoryPath.resolve("poi"), dataFixer, true, serverWorld.getRegistryManager(), serverWorld);
		this.versionedChunkStorage = new NoisiumServerVersionedChunkStorage(worldDirectoryPath.resolve("region"), dataFixer, true);
		this.threadPoolExecutor = Executors.newFixedThreadPool(
				1, new ThreadFactoryBuilder().setNameFormat("Noisium Server World Chunk Manager %d").build());
		this.loadedWorldChunks = new HashMap<>();

		if (chunkGenerator instanceof NoiseChunkGenerator noiseChunkGenerator) {
			this.noiseConfig = NoiseConfig.create(
					noiseChunkGenerator.getSettings().value(),
					serverWorld.getRegistryManager().getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS), serverWorld.getSeed()
			);
		} else {
			this.noiseConfig = NoiseConfig.create(
					ChunkGeneratorSettings.createMissingSettings(),
					serverWorld.getRegistryManager().getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS), serverWorld.getSeed()
			);
		}
	}

	@Override
	public BlockView getWorld() {
		return serverWorld;
	}

	@Nullable
	@Override
	public LightSourceView getChunk(int chunkX, int chunkZ) {
		return getChunk(new ChunkPos(chunkX, chunkZ));
	}

	@Override
	public void onLightUpdate(LightType lightType, ChunkSectionPos chunkSectionPosition) {
		var lightingProvider = serverWorld.getLightingProvider();
		var chunkPos = chunkSectionPosition.toChunkPos();
		var chunkSectionYPosition = chunkSectionPosition.getSectionY();
		int bottomY = lightingProvider.getBottomY();
		int topY = lightingProvider.getTopY();

		if (chunkSectionYPosition >= bottomY && chunkSectionYPosition <= topY) {
			int yDifference = chunkSectionYPosition - bottomY;
			var serverChunkData = loadedWorldChunks.get(chunkPos);
			var skyLightBits = serverChunkData.skyLightBits();
			var blockLightBits = serverChunkData.skyLightBits();

			if (lightType == LightType.SKY) {
				skyLightBits.set(yDifference);
			} else {
				blockLightBits.set(yDifference);
			}
			ChunkUtil.sendLightUpdateToPlayers(serverWorld.getPlayers(), lightingProvider, chunkPos, skyLightBits, blockLightBits);
		}
	}

	/**
	 * Loads the chunk at the specified position, returning the loaded chunk when done. Returns the chunk from the <code>loadedChunks</code> cache if available.
	 * This method is ran asynchronously.
	 *
	 * @param chunkPos The position at which to load the chunk.
	 * @return The loaded chunk.
	 */
	public @NotNull CompletableFuture<WorldChunk> getChunkAsync(ChunkPos chunkPos) {
		if (loadedWorldChunks.containsKey(chunkPos)) {
			return CompletableFuture.completedFuture(loadedWorldChunks.get(chunkPos).worldChunk());
		}

		return CompletableFuture.supplyAsync(() -> {
			var fetchedNbtData = getNbtDataAtChunkPosition(chunkPos);
			if (fetchedNbtData == null) {
				// TODO: Schedule ProtoChunk worldgen and update loadedWorldChunks incrementally during worldgen steps
				return new WorldChunk(serverWorld, generateChunk(chunkPos), null);
			}

			var fetchedChunk = ChunkSerializer.deserialize(serverWorld, pointOfInterestStorage, chunkPos, fetchedNbtData);
			return new WorldChunk(serverWorld, fetchedChunk,
					chunkToAddEntitiesTo -> serverWorld.addEntities(EntityType.streamFromNbt(fetchedChunk.getEntities(), serverWorld))
			);
		}, threadPoolExecutor).whenComplete((fetchedWorldChunk, throwable) -> {
			if (throwable != null) {
				Noisium.LOGGER.error("Exception thrown while getting a chunk asynchronously:\n{}", ExceptionUtils.getStackTrace(throwable));
			}

			fetchedWorldChunk.addChunkTickSchedulers(serverWorld);
			loadedWorldChunks.put(chunkPos, new ServerChunkData(fetchedWorldChunk, (short) 0, new BitSet(), new BitSet()));
		});
	}

	/**
	 * Loads the chunk at the specified position, returning the loaded chunk when done. Returns the chunk from the <code>loadedChunks</code> cache if available.
	 * WARNING: This method blocks the server thread. Prefer using {@link NoisiumServerWorldChunkManager#getChunk(int, int)} instead.
	 *
	 * @param chunkPos The position at which to load the chunk.
	 * @return The loaded chunk.
	 */
	public @Nullable WorldChunk getChunk(ChunkPos chunkPos) {
		if (loadedWorldChunks.containsKey(chunkPos)) {
			return loadedWorldChunks.get(chunkPos).worldChunk();
		}

		var fetchedNbtData = getNbtDataAtChunkPosition(chunkPos);
		if (fetchedNbtData == null) {
			// TODO: Schedule ProtoChunk worldgen and update loadedWorldChunks incrementally during worldgen steps
			return new WorldChunk(serverWorld, generateChunk(chunkPos), null);
		}

		var fetchedChunk = ChunkSerializer.deserialize(serverWorld, pointOfInterestStorage, chunkPos, fetchedNbtData);
		var fetchedWorldChunk = new WorldChunk(serverWorld, fetchedChunk,
				chunkToAddEntitiesTo -> serverWorld.addEntities(EntityType.streamFromNbt(fetchedChunk.getEntities(), serverWorld))
		);
		fetchedWorldChunk.addChunkTickSchedulers(serverWorld);

		loadedWorldChunks.put(chunkPos, new ServerChunkData(fetchedWorldChunk, (short) 0, new BitSet(), new BitSet()));
		return fetchedWorldChunk;
	}

	/**
	 * Gets all {@link WorldChunk}s around the specified chunk, using a square radius.
	 * This method is ran asynchronously.
	 *
	 * @param chunkPos The center {@link ChunkPos}.
	 * @param radius   A square radius of chunks.
	 * @return All the {@link WorldChunk}s around the specified chunk, using a square radius.
	 */
	public @NotNull Map<@NotNull ChunkPos, @Nullable CompletableFuture<WorldChunk>> getChunksInRadiusAsync(@NotNull ChunkPos chunkPos, int radius) {
		var chunks = new HashMap<@NotNull ChunkPos, @Nullable CompletableFuture<WorldChunk>>();

		for (int chunkPosX = chunkPos.x - radius; chunkPosX < chunkPos.x + radius; chunkPosX++) {
			for (int chunkPosZ = chunkPos.z - radius; chunkPosZ < chunkPos.z + radius; chunkPosZ++) {
				var chunkPosThatShouldBeLoaded = new ChunkPos(chunkPosX, chunkPosZ);
				chunks.put(chunkPosThatShouldBeLoaded, getChunkAsync(chunkPosThatShouldBeLoaded));
			}
		}

		return chunks;
	}

	/**
	 * Gets all {@link WorldChunk}s around the specified chunk, using a square radius.
	 * WARNING: This method blocks the server thread. Prefer using {@link NoisiumServerWorldChunkManager#getChunksInRadiusAsync(ChunkPos, int)} instead.
	 *
	 * @param chunkPos The center {@link ChunkPos}.
	 * @param radius   A square radius of chunks.
	 * @return All the {@link WorldChunk}s around the specified chunk, using a square radius.
	 */
	public @NotNull Map<@NotNull ChunkPos, @Nullable WorldChunk> getChunksInRadius(@NotNull ChunkPos chunkPos, int radius) {
		var chunks = new HashMap<@NotNull ChunkPos, @Nullable WorldChunk>();

		for (int chunkPosX = chunkPos.x - radius; chunkPosX < chunkPos.x + radius; chunkPosX++) {
			for (int chunkPosZ = chunkPos.z - radius; chunkPosZ < chunkPos.z + radius; chunkPosZ++) {
				var chunkPosThatShouldBeLoaded = new ChunkPos(chunkPosX, chunkPosZ);
				chunks.put(chunkPosThatShouldBeLoaded, getChunk(chunkPosThatShouldBeLoaded));
			}
		}

		return chunks;
	}

	private @Nullable NbtCompound getNbtDataAtChunkPosition(ChunkPos chunkPos) {
		try {
			var fetchedNbtCompoundOptionalFuture = versionedChunkStorage.getNbt(chunkPos).get();
			if (fetchedNbtCompoundOptionalFuture.isPresent()) {
				return fetchedNbtCompoundOptionalFuture.get();
			}
		} catch (Exception ex) {
			Noisium.LOGGER.error("Error occurred while fetching NBT data for chunk at {}", chunkPos);
		}

		return null;
	}

	private @NotNull ProtoChunk generateChunk(@NotNull ChunkPos chunkPos) {
		var serverLightingProvider = (ServerLightingProvider) serverWorld.getLightingProvider();

		// TODO: Fix Minecraft so it can generate 1 chunk at a time
		var radius = 17;
		List<Chunk> chunkRegionChunks = new ArrayList<>();
		for (int chunkPosX = chunkPos.x - radius; chunkPosX < chunkPos.x + radius; chunkPosX++) {
			for (int chunkPosZ = chunkPos.z - radius; chunkPosZ < chunkPos.z + radius; chunkPosZ++) {
				var chunkPosThatShouldBeLoaded = new ChunkPos(chunkPosX, chunkPosZ);
				var protoChunk = new ProtoChunk(chunkPosThatShouldBeLoaded, UpgradeData.NO_UPGRADE_DATA, serverWorld,
						serverWorld.getRegistryManager().get(RegistryKeys.BIOME), null
				);
				protoChunk.setLightingProvider(serverLightingProvider);
				protoChunk.setStatus(ChunkStatus.FULL);
				chunkRegionChunks.add(protoChunk);
			}
		}

		var protoChunk = new ProtoChunk(chunkPos, UpgradeData.NO_UPGRADE_DATA, serverWorld,
				serverWorld.getRegistryManager().get(RegistryKeys.BIOME), null
		);
		var chunkRegion = new ChunkRegion(serverWorld, chunkRegionChunks, ChunkStatus.FULL, 0);
		var blender = Blender.getBlender(chunkRegion);
		var structureAccessor = serverWorld.getStructureAccessor();

		protoChunk.setStatus(ChunkStatus.BIOMES);
		protoChunk.populateBiomes(chunkGenerator.getBiomeSource(), noiseConfig.getMultiNoiseSampler());

		protoChunk.setStatus(ChunkStatus.NOISE);
		var generationShapeConfig = ((NoiseChunkGenerator) chunkGenerator).getSettings().value().generationShapeConfig().trimHeight(
				protoChunk.getHeightLimitView());
		int minimumY = generationShapeConfig.minimumY();
		int minimumCellY = MathHelper.floorDiv(minimumY, generationShapeConfig.verticalCellBlockCount());
		int cellHeight = MathHelper.floorDiv(generationShapeConfig.height(), generationShapeConfig.verticalCellBlockCount());
		((NoiseChunkGeneratorAccessor) chunkGenerator).invokePopulateNoise(
				blender, structureAccessor, noiseConfig, protoChunk, minimumCellY, cellHeight);

		protoChunk.setStatus(ChunkStatus.FULL);
		return protoChunk;
	}
}
