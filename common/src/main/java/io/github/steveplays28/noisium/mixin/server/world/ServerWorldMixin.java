package io.github.steveplays28.noisium.mixin.server.world;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.DataFixer;
import dev.architectury.event.events.common.PlayerEvent;
import io.github.steveplays28.noisium.extension.world.server.NoisiumServerWorldExtension;
import io.github.steveplays28.noisium.server.world.NoisiumServerWorldChunkManager;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.RandomSequencesState;
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

import java.util.List;
import java.util.concurrent.Executor;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin implements NoisiumServerWorldExtension {
	@Shadow
	public abstract ServerWorld toServerWorld();

	@Shadow @Final private ServerEntityManager<Entity> entityManager;
	@Unique
	private NoisiumServerWorldChunkManager noisium$serverWorldChunkManager;

	@Inject(method = "<init>", at = @At(value = "TAIL"))
	private void noisium$constructorCreateServerWorldChunkManager(MinecraftServer server, Executor workerExecutor, LevelStorage.Session session, ServerWorldProperties properties, RegistryKey<World> worldKey, DimensionOptions dimensionOptions, WorldGenerationProgressListener worldGenerationProgressListener, boolean debugWorld, long seed, List<?> spawners, boolean shouldTickTime, RandomSequencesState randomSequencesState, CallbackInfo ci, @Local DataFixer dataFixer) {
		this.noisium$serverWorldChunkManager = new NoisiumServerWorldChunkManager(
				this.toServerWorld(), dimensionOptions.chunkGenerator(), session.getWorldDirectory(worldKey), dataFixer);

		// TODO: Redo the server entity manager entirely, in an event-based way
		//  Also remove this line when that's done, since this doesn't belong here
		PlayerEvent.PLAYER_JOIN.register(player -> this.entityManager.addEntity(player));
	}

	@Override
	public NoisiumServerWorldChunkManager noisium$getServerWorldChunkManager() {
		return noisium$serverWorldChunkManager;
	}
}
