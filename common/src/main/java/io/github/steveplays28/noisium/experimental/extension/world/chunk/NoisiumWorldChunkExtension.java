package io.github.steveplays28.noisium.experimental.extension.world.chunk;

import java.util.BitSet;

public interface NoisiumWorldChunkExtension {
	BitSet noisium$getBlockLightBits();

	BitSet noisium$getSkyLightBits();
}
