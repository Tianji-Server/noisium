package io.github.steveplays28.noisium.mixin.experimental.world.gen.chunk;

import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(NoiseChunkGenerator.class)
public class NoiseChunkGeneratorMixin {
	@ModifyConstant(method = "carve", constant = @Constant(intValue = 8))
	private int noisium$modifyCarvingChunkRadiusPositiveToFixAnInfiniteLoop(int chunkRadius) {
		return 0;
	}

	@ModifyConstant(method = "carve", constant = @Constant(intValue = -8))
	private int noisium$modifyCarvingChunkRadiusNegativeToFixAnInfiniteLoop(int chunkRadius) {
		return 0;
	}
}
