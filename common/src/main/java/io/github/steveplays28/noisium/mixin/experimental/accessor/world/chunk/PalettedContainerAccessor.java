package io.github.steveplays28.noisium.mixin.experimental.accessor.world.chunk;

import net.minecraft.util.thread.LockHelper;
import net.minecraft.world.chunk.PalettedContainer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PalettedContainer.class)
public interface PalettedContainerAccessor {
	@Accessor
	@NotNull LockHelper getLockHelper();
}
