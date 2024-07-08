package io.github.steveplays28.noisium.mixin.experimental.accessor.util.thread;

import net.minecraft.util.thread.LockHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.locks.Lock;

@Mixin(LockHelper.class)
public interface LockHelperAccessor {
	@Accessor
	@NotNull Lock getLock();

	@Accessor
	void setThread(@Nullable Thread thread);
}
