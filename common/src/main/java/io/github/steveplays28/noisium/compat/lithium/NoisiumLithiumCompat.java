package io.github.steveplays28.noisium.compat.lithium;

import net.fabricmc.loader.api.FabricLoader;

public class NoisiumLithiumCompat {
	public static final String LITHIUM_MOD_ID = "lithium";
	public static final String CANARY_MOD_ID = "canary";
	public static final String RADIUM_MOD_ID = "radium";

	/**
	 * @return If Lithium, or a (Neo)Forge fork, is loaded.
	 */
	public static boolean isLithiumLoaded() {
		var fabricLoaderInstance = FabricLoader.getInstance();
		return fabricLoaderInstance.isModLoaded(LITHIUM_MOD_ID) || fabricLoaderInstance.isModLoaded(
				CANARY_MOD_ID) || fabricLoaderInstance.isModLoaded(RADIUM_MOD_ID);
	}
}
