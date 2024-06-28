package io.github.steveplays28.noisium.mixin.experimental.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.ChunkSectionCache;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.chunk.ChunkSection;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSectionCache.class)
public class ChunkSectionCacheMixin {
	@Shadow
	@Final
	private @NotNull WorldAccess world;

	@Inject(method = "getSection", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$returnNullIfChunkIsUnloaded(@NotNull BlockPos blockPos, @NotNull CallbackInfoReturnable<ChunkSection> cir) {
		if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(blockPos.getX()), ChunkSectionPos.getSectionCoord(blockPos.getZ()))) {
			cir.setReturnValue(null);
		}
	}
}
