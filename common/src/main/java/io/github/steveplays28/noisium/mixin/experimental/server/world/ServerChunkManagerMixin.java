package io.github.steveplays28.noisium.mixin.experimental.server.world;

import io.github.steveplays28.noisium.experimental.extension.world.server.NoisiumServerWorldExtension;
import io.github.steveplays28.noisium.experimental.server.world.chunk.event.NoisiumServerChunkEvent;
import io.github.steveplays28.noisium.experimental.server.world.event.NoisiumServerTickEvent;
import io.github.steveplays28.noisium.experimental.util.world.chunk.networking.packet.PacketUtil;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
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

	// TODO: Fix infinite loop
	@Inject(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getChunkFromNoisiumServerWorldChunkManager(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<Chunk> cir) {
		((NoisiumServerWorldExtension) this.getWorld()).noisium$getServerWorldChunkManager().getChunkAsync(
				new ChunkPos(chunkX, chunkZ)
		).whenComplete((worldChunk, throwable) -> cir.setReturnValue(worldChunk));
	}

	@Inject(method = "getWorldChunk", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getWorldChunkFromNoisiumServerWorldChunkManager(int chunkX, int chunkZ, CallbackInfoReturnable<WorldChunk> cir) {
		((NoisiumServerWorldExtension) this.getWorld()).noisium$getServerWorldChunkManager().getChunkAsync(
				new ChunkPos(chunkX, chunkZ)
		).whenComplete((worldChunk, throwable) -> cir.setReturnValue(worldChunk));
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
	private void noisium$isChunkLoadedInNoisiumServerChunkManager(int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(((NoisiumServerWorldExtension) this.getWorld()).noisium$getServerWorldChunkManager().isChunkLoaded(
				new ChunkPos(chunkX, chunkZ)));
	}
}
