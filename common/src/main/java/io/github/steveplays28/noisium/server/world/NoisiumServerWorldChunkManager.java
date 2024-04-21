package io.github.steveplays28.noisium.server.world;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.datafixers.DataFixer;
import io.github.steveplays28.noisium.Noisium;
import io.github.steveplays28.noisium.extension.world.chunk.NoisiumWorldChunkExtension;
import io.github.steveplays28.noisium.mixin.accessor.NoiseChunkGeneratorAccessor;
import io.github.steveplays28.noisium.server.world.chunk.event.NoisiumServerChunkEvent;
import io.github.steveplays28.noisium.util.world.chunk.ChunkUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.*;
import net.minecraft.world.gen.GenerationStep;
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

// TODO: Fix canTickBlockEntities() check
//  The check needs to be changed to point to the server world's isChunkLoaded() method
// TODO: Implement chunk ticking
public class NoisiumServerWorldChunkManager {
	private final ServerWorld serverWorld;
	private final ChunkGenerator chunkGenerator;
	private final PointOfInterestStorage pointOfInterestStorage;
	private final VersionedChunkStorage versionedChunkStorage;
	private final NoiseConfig noiseConfig;
	private final Executor threadPoolExecutor;
	private final Map<ChunkPos, WorldChunk> loadedWorldChunks;

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

		NoisiumServerChunkEvent.LIGHT_UPDATE.register(this::onLightUpdateAsync);
	}

	/**
	 * Loads the chunk at the specified position, returning the loaded chunk when done.
	 * Returns the chunk from the {@link NoisiumServerWorldChunkManager#loadedWorldChunks} cache if available.
	 * This method is ran asynchronously.
	 *
	 * @param chunkPos The position at which to load the chunk.
	 * @return The loaded chunk.
	 */
	public @NotNull CompletableFuture<WorldChunk> getChunkAsync(ChunkPos chunkPos) {
		if (loadedWorldChunks.containsKey(chunkPos)) {
			return CompletableFuture.completedFuture(loadedWorldChunks.get(chunkPos));
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
			fetchedWorldChunk.loadEntities();
			loadedWorldChunks.put(chunkPos, fetchedWorldChunk);
			NoisiumServerChunkEvent.WORLD_CHUNK_GENERATED.invoker().onWorldChunkGenerated(fetchedWorldChunk);
		});
	}

	/**
	 * Loads the chunk at the specified position, returning the loaded {@link WorldChunk} when done.
	 * Returns the chunk from the {@link NoisiumServerWorldChunkManager#loadedWorldChunks} cache if available.
	 * WARNING: This method blocks the server thread. Prefer using {@link NoisiumServerWorldChunkManager#getChunkAsync} instead.
	 *
	 * @param chunkPos The position at which to load the {@link WorldChunk}.
	 * @return The loaded {@link WorldChunk}.
	 */
	public @NotNull WorldChunk getChunk(ChunkPos chunkPos) {
		if (loadedWorldChunks.containsKey(chunkPos)) {
			return loadedWorldChunks.get(chunkPos);
		}

		var fetchedNbtData = getNbtDataAtChunkPosition(chunkPos);
		if (fetchedNbtData == null) {
			// TODO: Schedule ProtoChunk worldgen and update loadedWorldChunks incrementally during worldgen steps
			var fetchedWorldChunk = new WorldChunk(serverWorld, generateChunk(chunkPos), null);
			loadedWorldChunks.put(chunkPos, fetchedWorldChunk);
			NoisiumServerChunkEvent.WORLD_CHUNK_GENERATED.invoker().onWorldChunkGenerated(fetchedWorldChunk);
			return fetchedWorldChunk;
		}

		var fetchedChunk = ChunkSerializer.deserialize(serverWorld, pointOfInterestStorage, chunkPos, fetchedNbtData);
		var fetchedWorldChunk = new WorldChunk(serverWorld, fetchedChunk,
				chunkToAddEntitiesTo -> serverWorld.addEntities(EntityType.streamFromNbt(fetchedChunk.getEntities(), serverWorld))
		);
		fetchedWorldChunk.addChunkTickSchedulers(serverWorld);
		fetchedWorldChunk.loadEntities();

		loadedWorldChunks.put(chunkPos, fetchedWorldChunk);
		NoisiumServerChunkEvent.WORLD_CHUNK_GENERATED.invoker().onWorldChunkGenerated(fetchedWorldChunk);
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

	/**
	 * Updates the chunk's lighting at the specified {@link ChunkSectionPos}.
	 * This method is ran asynchronously.
	 *
	 * @param lightType            The {@link LightType} that should be updated for this {@link WorldChunk}.
	 * @param chunkSectionPosition The {@link ChunkSectionPos} of the {@link WorldChunk}.
	 */
	private void onLightUpdateAsync(@NotNull LightType lightType, @NotNull ChunkSectionPos chunkSectionPosition) {
		CompletableFuture.runAsync(() -> {
			var lightingProvider = serverWorld.getLightingProvider();
			int bottomY = lightingProvider.getBottomY();
			int topY = lightingProvider.getTopY();
			var chunkSectionYPosition = chunkSectionPosition.getSectionY();
			if (chunkSectionYPosition < bottomY || chunkSectionYPosition > topY) {
				return;
			}

			var chunkPosition = chunkSectionPosition.toChunkPos();
			var worldChunk = (NoisiumWorldChunkExtension) getChunk(chunkPosition);
			var skyLightBits = worldChunk.noisium$getBlockLightBits();
			var blockLightBits = worldChunk.noisium$getSkyLightBits();
			int chunkSectionYPositionDifference = chunkSectionYPosition - bottomY;

			if (lightType == LightType.SKY) {
				skyLightBits.set(chunkSectionYPositionDifference);
			} else {
				blockLightBits.set(chunkSectionYPositionDifference);
			}
			ChunkUtil.sendLightUpdateToPlayers(serverWorld.getPlayers(), lightingProvider, chunkPosition, skyLightBits, blockLightBits);
		}, threadPoolExecutor);
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
		var regionStructureAccessor = serverWorld.getStructureAccessor().forRegion(chunkRegion);

		protoChunk.setStatus(ChunkStatus.BIOMES);
		protoChunk.populateBiomes(chunkGenerator.getBiomeSource(), noiseConfig.getMultiNoiseSampler());

		protoChunk.setStatus(ChunkStatus.NOISE);
		var generationShapeConfig = ((NoiseChunkGenerator) chunkGenerator).getSettings().value().generationShapeConfig().trimHeight(
				protoChunk.getHeightLimitView());
		int minimumY = generationShapeConfig.minimumY();
		int minimumCellY = MathHelper.floorDiv(minimumY, generationShapeConfig.verticalCellBlockCount());
		int cellHeight = MathHelper.floorDiv(generationShapeConfig.height(), generationShapeConfig.verticalCellBlockCount());
		((NoiseChunkGeneratorAccessor) chunkGenerator).invokePopulateNoise(
				blender, regionStructureAccessor, noiseConfig, protoChunk, minimumCellY, cellHeight);

		protoChunk.setStatus(ChunkStatus.SURFACE);
		chunkGenerator.buildSurface(chunkRegion, regionStructureAccessor, noiseConfig, protoChunk);

		protoChunk.setStatus(ChunkStatus.CARVERS);
		chunkGenerator.carve(
				chunkRegion, serverWorld.getSeed(), noiseConfig, chunkRegion.getBiomeAccess(), regionStructureAccessor, protoChunk,
				GenerationStep.Carver.AIR
		);

		protoChunk.setStatus(ChunkStatus.FEATURES);
		Heightmap.populateHeightmaps(
				protoChunk, EnumSet.of(Heightmap.Type.MOTION_BLOCKING, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Heightmap.Type.OCEAN_FLOOR,
						Heightmap.Type.WORLD_SURFACE
				)
		);
		chunkGenerator.generateFeatures(chunkRegion, protoChunk, regionStructureAccessor);
		Blender.tickLeavesAndFluids(chunkRegion, protoChunk);

		protoChunk.setStatus(ChunkStatus.FULL);
		versionedChunkStorage.setNbt(chunkPos, ChunkSerializer.serialize(serverWorld, protoChunk));
		return protoChunk;
	}
}
