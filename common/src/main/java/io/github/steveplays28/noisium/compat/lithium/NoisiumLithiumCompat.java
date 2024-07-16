package io.github.steveplays28.noisium.compat.lithium;

import io.github.steveplays28.noisium.util.ModLoaderUtil;

public class NoisiumLithiumCompat {
	public static final String LITHIUM_MOD_ID = "lithium";
	public static final String CANARY_MOD_ID = "canary";
	public static final String RADIUM_MOD_ID = "radium";

	/**
	 * @return If Lithium, or a (Neo)Forge fork, is loaded.
	 */
	public static boolean isLithiumLoaded() {
		return ModLoaderUtil.isModPresent(LITHIUM_MOD_ID) || ModLoaderUtil.isModPresent(CANARY_MOD_ID) || ModLoaderUtil.isModPresent(RADIUM_MOD_ID);
	}
}
