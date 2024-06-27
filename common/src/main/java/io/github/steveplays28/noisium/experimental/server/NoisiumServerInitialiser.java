package io.github.steveplays28.noisium.experimental.server;

import dev.architectury.event.events.common.LifecycleEvent;
import io.github.steveplays28.noisium.experimental.server.player.NoisiumServerPlayerBlockUpdater;

public class NoisiumServerInitialiser {
	/**
	 * Keeps a reference to the {@link NoisiumServerPlayerBlockUpdater}, to make sure it doesn't get garbage collected until the object is no longer necessary.
	 */
	@SuppressWarnings("unused")
	private static NoisiumServerPlayerBlockUpdater serverPlayerBlockUpdater;

	public static void initialise() {
		LifecycleEvent.SERVER_STARTED.register(instance -> serverPlayerBlockUpdater = new NoisiumServerPlayerBlockUpdater());
		LifecycleEvent.SERVER_STOPPING.register(instance -> serverPlayerBlockUpdater = null);
	}
}
