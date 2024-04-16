package io.github.steveplays28.noisium.server.world.entity;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

// TODO: Reimplement the rest of ServerEntityManager and replace it
public class NoisiumServerWorldEntityTracker {
	private final Map<Integer, EntityTrackerEntry> entityTrackerEntries;
	private final Consumer<Packet<?>> packetSendConsumer;

	public NoisiumServerWorldEntityTracker(Consumer<Packet<?>> packetSendConsumer) {
		this.entityTrackerEntries = new HashMap<>();
		this.packetSendConsumer = packetSendConsumer;

		EntityEvent.ADD.register(this::onEntityAdded);
		TickEvent.SERVER_LEVEL_PRE.register(this::onTick);
	}

	@SuppressWarnings("ForLoopReplaceableByForEach")
	private EventResult onEntityAdded(@NotNull Entity entity, @NotNull World world) {
		if (world.isClient()) {
			return EventResult.pass();
		}

		var entityType = entity.getType();
		entityTrackerEntries.put(
				entity.getId(),
				new EntityTrackerEntry(
						(ServerWorld) world, entity, entityType.getTrackTickInterval(), entityType.alwaysUpdateVelocity(),
						packetSendConsumer
				)
		);

		var players = ((ServerWorld) world).getPlayers();
		for (int i = 0; i < players.size(); i++) {
			entityTrackerEntries.get(entity.getId()).startTracking(players.get(i));
		}

		return EventResult.pass();
	}

	@SuppressWarnings("ForLoopReplaceableByForEach")
	private EventResult onEntityRemoved(@NotNull Entity entity, @NotNull World world) {
		var players = ((ServerWorld) world).getPlayers();
		for (int i = 0; i < players.size(); i++) {
			entityTrackerEntries.get(entity.getId()).stopTracking(players.get(i));
		}
		entityTrackerEntries.remove(entity.getId());
		return EventResult.pass();
	}

	private void onTick(ServerWorld serverWorld) {
		for (var entityTrackerEntry : entityTrackerEntries.values()) {
			entityTrackerEntry.tick();
		}
	}
}
