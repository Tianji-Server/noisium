package io.github.steveplays28.noisium.experimental.server.world;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.datafixers.DataFixer;
import dev.architectury.event.events.common.TickEvent;
import io.github.steveplays28.noisium.Noisium;
import io.github.steveplays28.noisium.experimental.extension.world.chunk.NoisiumWorldChunkExtension;
import io.github.steveplays28.noisium.experimental.server.world.chunk.event.NoisiumServerChunkEvent;
import io.github.steveplays28.noisium.experimental.util.world.chunk.ChunkUtil;
import io.github.steveplays28.noisium.mixin.experimental.accessor.NoiseChunkGeneratorAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
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
import net.minecraft.world.poi.PointOfInterestType;
import net.minecraft.world.poi.PointOfInterestTypes;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

// TODO: Fix canTickBlockEntities() check
//  The check needs to be changed to point to the server world's isChunkLoaded() method
// TODO: Implement chunk ticking
// TODO: Save all chunks when save event is called
public class NoisiumServerWorldChunkManager {
	private final ServerWorld serverWorld;
	private final ChunkGenerator chunkGenerator;
	private final PointOfInterestStorage pointOfInterestStorage;
	private final VersionedChunkStorage versionedChunkStorage;
	private final NoiseConfig noiseConfig;
	private final Executor threadPoolExecutor;
	private final ConcurrentMap<ChunkPos, CompletableFuture<WorldChunk>> loadingWorldChunks;
	private final Map<ChunkPos, WorldChunk> loadedWorldChunks;

	public NoisiumServerWorldChunkManager(@NotNull ServerWorld serverWorld, @NotNull ChunkGenerator chunkGenerator, @NotNull Path worldDirectoryPath, DataFixer dataFixer) {
		this.serverWorld = serverWorld;
		this.chunkGenerator = chunkGenerator;

		this.pointOfInterestStorage = new PointOfInterestStorage(
				worldDirectoryPath.resolve("poi"), dataFixer, true, serverWorld.getRegistryManager(), serverWorld);
		this.versionedChunkStorage = new NoisiumServerVersionedChunkStorage(worldDirectoryPath.resolve("region"), dataFixer, true);
		this.threadPoolExecutor = Executors.newFixedThreadPool(
				1, new ThreadFactoryBuilder().setNameFormat("Noisium Server World Chunk Manager %d").build());
		this.loadingWorldChunks = new ConcurrentHashMap<>();
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
		NoisiumServerChunkEvent.BLOCK_CHANGE.register(this::onBlockChange);
		TickEvent.SERVER_LEVEL_POST.register(instance -> {
			if (!instance.equals(serverWorld)) {
				return;
			}

			((ServerLightingProvider) serverWorld.getLightingProvider()).tick();
		});
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
		} else if (loadingWorldChunks.containsKey(chunkPos)) {
			return loadingWorldChunks.get(chunkPos);
		}

		var worldChunkCompletableFuture = CompletableFuture.supplyAsync(() -> {
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
			loadingWorldChunks.remove(chunkPos);
			loadedWorldChunks.put(chunkPos, fetchedWorldChunk);
			NoisiumServerChunkEvent.WORLD_CHUNK_GENERATED.invoker().onWorldChunkGenerated(fetchedWorldChunk);
		});
		loadingWorldChunks.put(chunkPos, worldChunkCompletableFuture);
		return worldChunkCompletableFuture;
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
		// TODO: Check if loadingWorldChunks.containsKey(chunkPos)
		// TODO: Add to and remove from loadingWorldChunks when generating a WorldChunk

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

	public boolean isChunkLoaded(ChunkPos chunkPos) {
		return this.loadedWorldChunks.containsKey(chunkPos);
	}

	/**
	 * Updates the chunk's lighting at the specified {@link ChunkSectionPos}.
	 * This method is ran asynchronously.
	 *
	 * @param lightType            The {@link LightType} that should be updated for this {@link WorldChunk}.
	 * @param chunkSectionPosition The {@link ChunkSectionPos} of the {@link WorldChunk}.
	 */
	private void onLightUpdateAsync(@NotNull LightType lightType, @NotNull ChunkSectionPos chunkSectionPosition) {
		var lightingProvider = serverWorld.getLightingProvider();
		int bottomY = lightingProvider.getBottomY();
		int topY = lightingProvider.getTopY();
		var chunkSectionYPosition = chunkSectionPosition.getSectionY();
		if (chunkSectionYPosition < bottomY || chunkSectionYPosition > topY) {
			return;
		}

		var chunkPosition = chunkSectionPosition.toChunkPos();
		getChunkAsync(chunkPosition).whenCompleteAsync((worldChunk, throwable) -> {
			var worldChunkExtension = (NoisiumWorldChunkExtension) worldChunk;
			var skyLightBits = worldChunkExtension.noisium$getBlockLightBits();
			var blockLightBits = worldChunkExtension.noisium$getSkyLightBits();
			int chunkSectionYPositionDifference = chunkSectionYPosition - bottomY;

			skyLightBits.clear();
			blockLightBits.clear();
			if (lightType == LightType.SKY) {
				skyLightBits.set(chunkSectionYPositionDifference);
			} else {
				blockLightBits.set(chunkSectionYPositionDifference);
			}
			ChunkUtil.sendLightUpdateToPlayers(serverWorld.getPlayers(), lightingProvider, chunkPosition, skyLightBits, blockLightBits);
		});
	}

	// TODO: Check if this can be ran asynchronously
	@SuppressWarnings("OptionalIsPresent")
	private void onBlockChange(@NotNull BlockPos blockPos, @NotNull BlockState oldBlockState, @NotNull BlockState newBlockState) {
		Optional<RegistryEntry<PointOfInterestType>> oldBlockStatePointOfInterestTypeOptional = PointOfInterestTypes.getTypeForState(
				oldBlockState);
		Optional<RegistryEntry<PointOfInterestType>> newBlockStatePointOfInterestTypeOptional = PointOfInterestTypes.getTypeForState(
				newBlockState);
		if (oldBlockStatePointOfInterestTypeOptional.equals(newBlockStatePointOfInterestTypeOptional)) {
			return;
		}

		BlockPos immutableBlockPos = blockPos.toImmutable();
		if (oldBlockStatePointOfInterestTypeOptional.isPresent()) {
			pointOfInterestStorage.remove(immutableBlockPos);
			// TODO: Add sendPoiRemoval method call into DebugInfoSenderMixin using an event
		}
		if (newBlockStatePointOfInterestTypeOptional.isPresent()) {
			pointOfInterestStorage.add(immutableBlockPos, newBlockStatePointOfInterestTypeOptional.get());
			// TODO: Add sendPoiRemoval method call into DebugInfoSenderMixin using an event
		}
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
		var protoChunk = new ProtoChunk(chunkPos, UpgradeData.NO_UPGRADE_DATA, serverWorld,
				serverWorld.getRegistryManager().get(RegistryKeys.BIOME), null
		);
		// TODO: Fix Minecraft so it can generate 1 chunk at a time
		List<Chunk> chunkRegionChunks = List.of(protoChunk);
		var chunkRegion = new ChunkRegion(serverWorld, chunkRegionChunks, ChunkStatus.FULL, 1);
		var blender = Blender.getBlender(chunkRegion);
		var chunkRegionStructureAccessor = serverWorld.getStructureAccessor().forRegion(chunkRegion);

		protoChunk.setStatus(ChunkStatus.STRUCTURE_STARTS);
		// TODO: Move the structure placement calculator into NoisiumServerWorldChunkManager
		// TODO: Pass the structure template manager into NoisiumServerWorldChunkManager
		// TODO: Pass the shouldGenerateStructures boolean into NoisiumServerWorldChunkManager
		if (serverWorld.getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures()) {
			chunkGenerator.setStructureStarts(
					serverWorld.getRegistryManager(), serverWorld.getChunkManager().getStructurePlacementCalculator(),
					chunkRegionStructureAccessor, protoChunk, serverWorld.getServer().getStructureTemplateManager()
			);
		}
		serverWorld.cacheStructures(protoChunk);

		protoChunk.setStatus(ChunkStatus.STRUCTURE_REFERENCES);
		chunkGenerator.addStructureReferences(chunkRegion, chunkRegionStructureAccessor, protoChunk);

		protoChunk.setStatus(ChunkStatus.BIOMES);
		protoChunk.populateBiomes(chunkGenerator.getBiomeSource(), noiseConfig.getMultiNoiseSampler());

		protoChunk.setStatus(ChunkStatus.NOISE);
		// TODO: Remove the cast to NoiseChunkGenerator
		//  Or add some other way to support any type of ChunkGenerator
		var generationShapeConfig = ((NoiseChunkGenerator) chunkGenerator).getSettings().value().generationShapeConfig().trimHeight(
				protoChunk.getHeightLimitView());
		int minimumY = generationShapeConfig.minimumY();
		int minimumCellY = MathHelper.floorDiv(minimumY, generationShapeConfig.verticalCellBlockCount());
		int cellHeight = MathHelper.floorDiv(generationShapeConfig.height(), generationShapeConfig.verticalCellBlockCount());
		// TODO: Remove the cast to NoiseChunkGeneratorAccessor
		//  Or add some other way to support any type of ChunkGenerator
		((NoiseChunkGeneratorAccessor) chunkGenerator).invokePopulateNoise(
				blender, chunkRegionStructureAccessor, noiseConfig, protoChunk, minimumCellY, cellHeight);

		protoChunk.setStatus(ChunkStatus.SURFACE);
		chunkGenerator.buildSurface(chunkRegion, chunkRegionStructureAccessor, noiseConfig, protoChunk);

		protoChunk.setStatus(ChunkStatus.CARVERS);
		chunkGenerator.carve(
				chunkRegion, chunkRegion.getSeed(), noiseConfig, chunkRegion.getBiomeAccess(), chunkRegionStructureAccessor, protoChunk,
				GenerationStep.Carver.AIR
		);

		protoChunk.setStatus(ChunkStatus.FEATURES);
		Heightmap.populateHeightmaps(
				protoChunk,
				EnumSet.of(
						Heightmap.Type.MOTION_BLOCKING, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
						Heightmap.Type.OCEAN_FLOOR, Heightmap.Type.WORLD_SURFACE
				)
		);
		chunkGenerator.generateFeatures(chunkRegion, protoChunk, chunkRegionStructureAccessor);
		Blender.tickLeavesAndFluids(chunkRegion, protoChunk);

		protoChunk.setStatus(ChunkStatus.INITIALIZE_LIGHT);
		protoChunk.refreshSurfaceY();
		serverLightingProvider.initializeLight(protoChunk, protoChunk.isLightOn());

		protoChunk.setStatus(ChunkStatus.LIGHT);
		serverLightingProvider.light(protoChunk, protoChunk.isLightOn());

		protoChunk.setStatus(ChunkStatus.FULL);
		versionedChunkStorage.setNbt(chunkPos, ChunkSerializer.serialize(serverWorld, protoChunk));
		return protoChunk;
	}
}
