package io.github.steveplays28.noisium.mixin.server.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.DataFixer;
import dev.architectury.event.events.common.PlayerEvent;
import io.github.steveplays28.noisium.extension.world.server.NoisiumServerWorldExtension;
import io.github.steveplays28.noisium.server.world.NoisiumServerWorldChunkManager;
import io.github.steveplays28.noisium.server.world.chunk.event.NoisiumServerChunkEvent;
import io.github.steveplays28.noisium.server.world.entity.NoisiumServerWorldEntityTracker;
import io.github.steveplays28.noisium.util.world.chunk.networking.packet.PacketUtil;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.RandomSequencesState;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.Executor;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin implements NoisiumServerWorldExtension {
	@Shadow
	@Final
	private ServerEntityManager<Entity> entityManager;

	@Shadow
	public abstract boolean isChunkLoaded(long chunkPos);

	@Unique
	private NoisiumServerWorldChunkManager noisium$serverWorldChunkManager;
	@Unique
	private NoisiumServerWorldEntityTracker noisium$serverWorldEntityManager;

	@Inject(method = "<init>", at = @At(value = "TAIL"))
	private void noisium$constructorCreateServerWorldChunkManager(MinecraftServer server, Executor workerExecutor, LevelStorage.Session session, ServerWorldProperties properties, RegistryKey<World> worldKey, DimensionOptions dimensionOptions, WorldGenerationProgressListener worldGenerationProgressListener, boolean debugWorld, long seed, List<?> spawners, boolean shouldTickTime, RandomSequencesState randomSequencesState, CallbackInfo ci, @Local DataFixer dataFixer) {
		var serverWorld = ((ServerWorld) (Object) this);

		this.noisium$serverWorldChunkManager = new NoisiumServerWorldChunkManager(
				serverWorld, dimensionOptions.chunkGenerator(), session.getWorldDirectory(worldKey), dataFixer);
		this.noisium$serverWorldEntityManager = new NoisiumServerWorldEntityTracker(
				packet -> PacketUtil.sendPacketToPlayers(serverWorld.getPlayers(), packet));

		// TODO: Redo the server entity manager entirely, in an event-based way
		//  Also remove this line when that's done, since this doesn't belong here
		PlayerEvent.PLAYER_JOIN.register(player -> this.entityManager.addEntity(player));

		// TODO: Move this event listener registration to ServerEntityManagerMixin
		//  or (when it's finished and able to completely replace the vanilla class) to NoisiumServerWorldEntityTracker
		//  More efficient methods can be used when registering the event listener directly in the server entity manager
		NoisiumServerChunkEvent.WORLD_CHUNK_GENERATED.register(
				worldChunk -> this.entityManager.updateTrackingStatus(worldChunk.getPos(), ChunkLevelType.ENTITY_TICKING));
	}

	@Inject(method = "isTickingFutureReady", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$checkIfTickingFutureIsReadyByCheckingIfTheChunkIsLoaded(long chunkPos, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(this.isChunkLoaded(chunkPos));
	}

	@SuppressWarnings("unused")
	@ModifyExpressionValue(method = "method_31420", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ChunkTicketManager;shouldTickEntities(J)Z"))
	private boolean noisium$redirectShouldTickEntitiesToEntityManager(boolean original, Profiler profiler, Entity entity) {
		return this.entityManager.shouldTick(entity.getChunkPos());
	}

	@Override
	public NoisiumServerWorldChunkManager noisium$getServerWorldChunkManager() {
		return noisium$serverWorldChunkManager;
	}
}
