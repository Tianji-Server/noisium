package io.github.steveplays28.noisium.mixin.world;

import io.github.steveplays28.noisium.extension.world.server.NoisiumServerWorldExtension;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldAccess.class)
public interface WorldAccessMixin {
	@Inject(method = "isChunkLoaded", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$getChunkFromNoisiumServerChunkManager(int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(((NoisiumServerWorldExtension) this).noisium$getServerWorldChunkManager().getChunkAsync(
				new ChunkPos(chunkX, chunkZ)).isDone());
	}
}
