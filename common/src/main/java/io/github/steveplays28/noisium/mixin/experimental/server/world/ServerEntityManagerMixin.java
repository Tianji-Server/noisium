package io.github.steveplays28.noisium.mixin.experimental.server.world;

import io.github.steveplays28.noisium.experimental.server.world.entity.event.NoisiumServerEntityEvent;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.entity.EntityLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerEntityManager.class, priority = 500)
public class ServerEntityManagerMixin<T extends EntityLike> {
	@Inject(method = "stopTracking", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$entityRemoveEventImplementation(T entity, CallbackInfo ci) {
		if (entity instanceof Entity entityInWorld) {
			if (NoisiumServerEntityEvent.REMOVE.invoker().onServerEntityRemove(
					entityInWorld, entityInWorld.getWorld()).interruptsFurtherEvaluation()) {
				ci.cancel();
			}
		}
	}

	@Inject(method = "save", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$cancelSave(CallbackInfo ci) {
		// TODO: Invoke an entity manager save event that's used in NoisiumServerWorldEntityTracker
		ci.cancel();
	}
}
