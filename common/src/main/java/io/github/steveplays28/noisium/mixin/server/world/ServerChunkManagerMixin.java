package io.github.steveplays28.noisium.mixin.server.world;

import io.github.steveplays28.noisium.extension.world.server.NoisiumServerWorldExtension;
import io.github.steveplays28.noisium.server.world.event.NoisiumServerTickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

// FIXME: Remove this mixin once the server chunk manager is fully replaced
@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin {
	@Shadow
	public abstract World getWorld();

	@Inject(method = "executeQueuedTasks", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$stopServerChunkManagerFromRunningTasks(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}

	@Inject(method = "tick(Ljava/util/function/BooleanSupplier;Z)V", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$stopServerChunkManagerFromTicking(BooleanSupplier shouldKeepTicking, boolean tickChunks, CallbackInfo ci) {
		NoisiumServerTickEvent.SERVER_ENTITY_MOVEMENT_TICK.invoker().onServerEntityMovementTick();
		ci.cancel();
	}

	@Inject(method = "getWorldChunk", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getWorldChunkFromNoisiumServerWorldChunkManager(int chunkX, int chunkZ, CallbackInfoReturnable<WorldChunk> cir) {
		((NoisiumServerWorldExtension) this.getWorld()).noisium$getServerWorldChunkManager().getChunkAsync(
				new ChunkPos(chunkX, chunkZ)).whenComplete((worldChunk, throwable) -> cir.setReturnValue(worldChunk));
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
}
