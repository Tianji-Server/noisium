package io.github.steveplays28.noisium.experimental.extension.world.server;

import io.github.steveplays28.noisium.experimental.server.world.NoisiumServerWorldChunkManager;
import net.minecraft.world.gen.noise.NoiseConfig;

public interface NoisiumServerWorldExtension {
	NoisiumServerWorldChunkManager noisium$getServerWorldChunkManager();

	NoiseConfig noisium$getNoiseConfig();
}
