package io.github.steveplays28.noisium.mixin.experimental.server.world;

import com.mojang.datafixers.DataFixer;
import io.github.steveplays28.noisium.experimental.extension.world.server.NoisiumServerWorldExtension;
import io.github.steveplays28.noisium.experimental.server.world.chunk.event.NoisiumServerChunkEvent;
import io.github.steveplays28.noisium.experimental.server.world.event.NoisiumServerTickEvent;
import io.github.steveplays28.noisium.experimental.util.world.chunk.networking.packet.PacketUtil;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ChunkStatusChangeListener;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.storage.NbtScannable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * {@link Mixin} into {@link ServerChunkManager}.
 * This {@link Mixin} redirects all method calls from the {@link ServerWorld}'s {@link ServerChunkManager} to the {@link ServerWorld}'s {@link io.github.steveplays28.noisium.experimental.server.world.NoisiumServerWorldChunkManager}.
 */
@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin {
	@Shadow
	public abstract World getWorld();

	@Mutable
	@Shadow
	@Final
	public ThreadedAnvilChunkStorage threadedAnvilChunkStorage;

	@Shadow
	public abstract @NotNull ChunkGenerator getChunkGenerator();

	@Shadow
	public abstract @NotNull NoiseConfig getNoiseConfig();

	@Unique
	private ChunkGenerator noisium$chunkGenerator;
	@Unique
	private StructurePlacementCalculator noisium$structurePlacementCalculator;

	@Inject(method = "<init>", at = @At(value = "TAIL"))
	private void noisium$constructorInject(ServerWorld world, LevelStorage.Session session, DataFixer dataFixer, StructureTemplateManager structureTemplateManager, Executor workerExecutor, @NotNull ChunkGenerator chunkGenerator, int viewDistance, int simulationDistance, boolean dsync, WorldGenerationProgressListener worldGenerationProgressListener, ChunkStatusChangeListener chunkStatusChangeListener, Supplier<PersistentStateManager> persistentStateManagerFactory, CallbackInfo ci) {
		noisium$chunkGenerator = chunkGenerator;
		noisium$structurePlacementCalculator = this.getChunkGenerator().createStructurePlacementCalculator(
				this.getWorld().getRegistryManager().getWrapperOrThrow(RegistryKeys.STRUCTURE_SET), this.getNoiseConfig(),
				((ServerWorld) this.getWorld()).getSeed()
		);
		threadedAnvilChunkStorage = null;
	}

	@Inject(method = "executeQueuedTasks", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$stopServerChunkManagerFromRunningTasks(@NotNull CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}

	@Inject(method = "tick(Ljava/util/function/BooleanSupplier;Z)V", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$stopServerChunkManagerFromTicking(@NotNull BooleanSupplier shouldKeepTicking, boolean tickChunks, @NotNull CallbackInfo ci) {
		NoisiumServerTickEvent.SERVER_ENTITY_MOVEMENT_TICK.invoker().onServerEntityMovementTick();
		ci.cancel();
	}

	@Inject(method = "close", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ThreadedAnvilChunkStorage;close()V", shift = At.Shift.BEFORE), cancellable = true)
	private void noisium$cancelRemoveThreadedAnvilChunkStorageClose(@NotNull CallbackInfo ci) {
		ci.cancel();
	}

	// TODO: Fix infinite loop
	@Inject(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getChunkFromNoisiumServerWorldChunkManager(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<Chunk> cir) {
		var noisiumServerWorldChunkManager = ((NoisiumServerWorldExtension) this.getWorld()).noisium$getServerWorldChunkManager();
		var chunkPosition = new ChunkPos(chunkX, chunkZ);
		if (!noisiumServerWorldChunkManager.isChunkLoaded(chunkPosition)) {
			cir.setReturnValue(null);
			return;
		}

		cir.setReturnValue(noisiumServerWorldChunkManager.getChunk(chunkPosition));
	}

	@Inject(method = "getChunk(II)Lnet/minecraft/world/chunk/light/LightSourceView;", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getChunkFromNoisiumServerWorldChunkManager(int chunkX, int chunkZ, CallbackInfoReturnable<WorldChunk> cir) {
		var noisiumServerWorldChunkManager = ((NoisiumServerWorldExtension) this.getWorld()).noisium$getServerWorldChunkManager();
		var chunkPosition = new ChunkPos(chunkX, chunkZ);
		if (!noisiumServerWorldChunkManager.isChunkLoaded(chunkPosition)) {
			cir.setReturnValue(null);
			return;
		}

		cir.setReturnValue(noisiumServerWorldChunkManager.getChunk(chunkPosition));
	}

	@Inject(method = "getWorldChunk", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getWorldChunkFromNoisiumServerWorldChunkManager(int chunkX, int chunkZ, CallbackInfoReturnable<WorldChunk> cir) {
		var noisiumServerWorldChunkManager = ((NoisiumServerWorldExtension) this.getWorld()).noisium$getServerWorldChunkManager();
		var chunkPosition = new ChunkPos(chunkX, chunkZ);
		if (!noisiumServerWorldChunkManager.isChunkLoaded(chunkPosition)) {
			cir.setReturnValue(null);
			return;
		}

		cir.setReturnValue(noisiumServerWorldChunkManager.getChunk(chunkPosition));
	}

	// TODO: Don't send this packet to players out of range, to save on bandwidth
	@SuppressWarnings("ForLoopReplaceableByForEach")
	@Inject(method = "sendToNearbyPlayers", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$sendToNearbyPlayersViaNoisiumServerWorldChunkManager(Entity entity, Packet<?> packet, CallbackInfo ci) {
		var server = entity.getServer();
		if (server == null) {
			return;
		}

		var players = server.getPlayerManager().getPlayerList();
		for (int i = 0; i < players.size(); i++) {
			players.get(i).networkHandler.sendPacket(packet);
		}

		ci.cancel();
	}

	// TODO: Don't send this packet to players out of range, to save on bandwidth
	@SuppressWarnings("ForLoopReplaceableByForEach")
	@Inject(method = "sendToOtherNearbyPlayers", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$sendToOtherNearbyPlayersViaNoisiumServerWorldChunkManager(Entity entity, Packet<?> packet, CallbackInfo ci) {
		var server = entity.getServer();
		if (server == null) {
			return;
		}

		var players = server.getPlayerManager().getPlayerList();
		for (int i = 0; i < players.size(); i++) {
			var player = players.get(i);
			if (player.equals(entity)) {
				continue;
			}

			player.networkHandler.sendPacket(packet);
		}

		ci.cancel();
	}

	@Inject(method = {"loadEntity", "unloadEntity"}, at = @At(value = "HEAD"), cancellable = true)
	private void noisium$cancelEntityLoadingAndUnloading(Entity entity, CallbackInfo ci) {
		ci.cancel();
	}

	@Inject(method = "updatePosition", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$cancelPlayerPositionUpdating(ServerPlayerEntity player, CallbackInfo ci) {
		ci.cancel();
	}

	@Inject(method = "markForUpdate", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$markForUpdateViaNoisiumServerWorldChunkManager(BlockPos blockPos, CallbackInfo ci) {
		// TODO: Optimise using a pending update queue and ChunkDeltaUpdateS2CPacket
		// TODO: Implement block entity update packet sending
		var serverWorld = (ServerWorld) this.getWorld();
		PacketUtil.sendPacketToPlayers(serverWorld.getPlayers(), new BlockUpdateS2CPacket(blockPos, serverWorld.getBlockState(blockPos)));
		ci.cancel();
	}

	@Inject(method = "onLightUpdate", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$updateLightingViaNoisiumServerWorldChunkManager(LightType lightType, ChunkSectionPos chunkSectionPos, CallbackInfo ci) {
		NoisiumServerChunkEvent.LIGHT_UPDATE.invoker().onLightUpdate(lightType, chunkSectionPos);
		ci.cancel();
	}

	@Inject(method = "save", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$cancelSave(boolean flush, CallbackInfo ci) {
		ci.cancel();
	}

	@Inject(method = "isChunkLoaded", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$isChunkLoadedInNoisiumServerWorldChunkManager(int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(((NoisiumServerWorldExtension) this.getWorld()).noisium$getServerWorldChunkManager().isChunkLoaded(
				new ChunkPos(chunkX, chunkZ)));
	}

	@Inject(method = "getStructurePlacementCalculator", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getStructurePlacementCalculatorFromServerChunkManager(CallbackInfoReturnable<StructurePlacementCalculator> cir) {
		cir.setReturnValue(noisium$structurePlacementCalculator);
	}

	@Inject(method = "getChunkGenerator", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getChunkGeneratorFromServerChunkManager(@NotNull CallbackInfoReturnable<ChunkGenerator> cir) {
		cir.setReturnValue(noisium$chunkGenerator);
	}

	@Inject(method = "getNoiseConfig", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getNoiseConfigFromServerChunkManager(@NotNull CallbackInfoReturnable<NoiseConfig> cir) {
		cir.setReturnValue(((NoisiumServerWorldExtension) this.getWorld()).noisium$getNoiseConfig());
	}

	@Inject(method = "getChunkIoWorker", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getChunkIoWorkerFromNoisiumServerWorldChunkManager(@NotNull CallbackInfoReturnable<NbtScannable> cir) {
		cir.setReturnValue(((NoisiumServerWorldExtension) this.getWorld()).noisium$getServerWorldChunkManager().getChunkIoWorker());
	}

	@Inject(method = {"getLoadedChunkCount", "getTotalChunksLoadedCount"}, at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getTotalChunksLoadedCountFromNoisiumServerWorldChunkManager(@NotNull CallbackInfoReturnable<Integer> cir) {
		// TODO: Remove the method call for 441 start chunks, replace the 2 method calls for client/server chunk count debugging with an event listener and remove this mixin injection
		cir.setReturnValue(0);
	}

	@Inject(method = {"applyViewDistance", "applySimulationDistance"}, at = @At(value = "HEAD"), cancellable = true)
	private void noisium$cancelApplyViewAndSimulationDistance(int distance, @NotNull CallbackInfo ci) {
		ci.cancel();
	}
}
