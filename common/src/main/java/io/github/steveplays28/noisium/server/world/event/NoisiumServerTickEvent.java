package io.github.steveplays28.noisium.server.world.event;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;

public interface NoisiumServerTickEvent {
	/**
	 * @see ServerEntityMovementTick
	 */
	Event<ServerEntityMovementTick> SERVER_ENTITY_MOVEMENT_TICK = EventFactory.createLoop();

	@FunctionalInterface
	interface ServerEntityMovementTick {
		/**
		 * Invoked before the server starts processing entity movement ticks.
		 */
		void onServerEntityMovementTick();
	}
}