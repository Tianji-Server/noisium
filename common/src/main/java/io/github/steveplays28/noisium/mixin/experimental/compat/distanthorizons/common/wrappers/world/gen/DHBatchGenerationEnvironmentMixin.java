package io.github.steveplays28.noisium.mixin.experimental.compat.distanthorizons.common.wrappers.world.gen;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.steveplays28.noisium.experimental.extension.world.server.NoisiumServerWorldExtension;
import loaderCommon.fabric.com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.storage.StorageIoWorker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BatchGenerationEnvironment.class)
public class DHBatchGenerationEnvironmentMixin {
	@Redirect(method = "getChunkNbtData", at = @At(value = "FIELD", target = "Lnet/minecraft/server/world/ThreadedAnvilChunkStorage;worker:Lnet/minecraft/world/storage/StorageIoWorker;", opcode = Opcodes.GETFIELD))
	private @NotNull StorageIoWorker noisium$getIoWorkerFromNoisiumServerWorldChunkManager(@Nullable ThreadedAnvilChunkStorage instance, @Local(ordinal = 0) @NotNull ServerWorld serverWorld) {
		return (StorageIoWorker) ((NoisiumServerWorldExtension) serverWorld).noisium$getServerWorldChunkManager().getChunkIoWorker();
	}
}
