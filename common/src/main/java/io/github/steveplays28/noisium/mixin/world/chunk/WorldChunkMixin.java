package io.github.steveplays28.noisium.mixin.world.chunk;

import io.github.steveplays28.noisium.extension.world.chunk.NoisiumWorldChunkExtension;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.BitSet;

@Mixin(WorldChunk.class)
public class WorldChunkMixin implements NoisiumWorldChunkExtension {
	@Unique
	private final BitSet noisium$blockLightBits = new BitSet();
	@Unique
	private final BitSet noisium$skyLightBits = new BitSet();

	@Override
	public BitSet noisium$getBlockLightBits() {
		return noisium$blockLightBits;
	}

	@Override
	public BitSet noisium$getSkyLightBits() {
		return noisium$skyLightBits;
	}
}
