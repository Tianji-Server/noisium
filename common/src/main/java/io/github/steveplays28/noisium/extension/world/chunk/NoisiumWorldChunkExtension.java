package io.github.steveplays28.noisium.extension.world.chunk;

import java.util.BitSet;

public interface NoisiumWorldChunkExtension {
	BitSet noisium$getBlockLightBits();

	BitSet noisium$getSkyLightBits();
}
