package io.github.steveplays28.noisium.mixin;

import com.google.common.collect.ImmutableMap;
import io.github.steveplays28.noisium.compat.lithium.NoisiumLithiumCompat;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static io.github.steveplays28.noisium.util.ModLoaderUtil.isModPresent;

public class NoisiumMixinPlugin implements IMixinConfigPlugin {
	private static final Supplier<Boolean> TRUE = () -> true;
	private static final @NotNull String DISTANT_HORIZONS_MOD_ID = "distanthorizons";

	private static final Map<String, Supplier<Boolean>> CONDITIONS = ImmutableMap.of(
			"io.github.steveplays28.noisium.mixin.NoiseChunkGeneratorMixin", () -> !NoisiumLithiumCompat.isLithiumLoaded(),
			"io.github.steveplays28.noisium.mixin.compat.lithium.LithiumNoiseChunkGeneratorMixin", NoisiumLithiumCompat::isLithiumLoaded,
			"io.github.steveplays28.noisium.mixin.compat.distanthorizons.common.wrappers.world.gen.DHBatchGenerationEnvironmentMixin",
			() -> isModPresent(DISTANT_HORIZONS_MOD_ID)
	);

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return CONDITIONS.getOrDefault(mixinClassName, TRUE).get();
	}

	@Override
	public void onLoad(String mixinPackage) {}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
