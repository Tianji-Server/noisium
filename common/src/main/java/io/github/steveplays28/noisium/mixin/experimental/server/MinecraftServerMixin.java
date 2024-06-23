package io.github.steveplays28.noisium.mixin.experimental.server;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin<T> {
//	@Shadow
//	public abstract ServerWorld getOverworld();
//
//	@Redirect(method = "prepareStartRegion", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerChunkManager;addTicket(Lnet/minecraft/server/world/ChunkTicketType;Lnet/minecraft/util/math/ChunkPos;ILjava/lang/Object;)V"))
//	private void noisium$prepareStartRegionAddTicketForSpawnChunksToNoisiumServerWorldChunkManager(ServerChunkManager instance, ChunkTicketType<T> ticketType, ChunkPos chunkPos, int radius, T argument) {
//		((NoisiumServerWorldExtension) getOverworld()).noisium$getServerWorldChunkManager().getChunksInRadius(chunkPos, radius);
//	}
//
//	@Redirect(method = "prepareStartRegion", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerChunkManager;getTotalChunksLoadedCount()I"))
//	private int noisium$prepareStartRegionRedirectTotalLoadedChunksToNoisiumServerWorldChunkManager(ServerChunkManager instance) {
//		// TODO: Make the loadedWorldChunks field private again and remove this whole vanilla Minecraft system
//		return ((NoisiumServerWorldExtension) getOverworld()).noisium$getServerWorldChunkManager().loadedWorldChunks.size();
//	}
//
//	@ModifyConstant(method = "prepareStartRegion", constant = @Constant(intValue = 441))
//	private int noisium$prepareStartRegionChangeTheAmountOfSpawnChunks(int original) {
//		return 484;
//	}
}
