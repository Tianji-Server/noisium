package io.github.steveplays28.noisium.mixin.world.gen.chunk;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.DebugInfoSender;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
	@WrapWithCondition(method = "generateFeatures", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;forEach(Ljava/util/function/Consumer;)V"))
	private static boolean noisium$cancelChunkPosStreamToFixAnInfiniteLoop(Stream<ChunkPos> stream, Consumer<?> consumer) {
		return false;
	}

	@Inject(method = "generateFeatures", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ChunkPos;stream(Lnet/minecraft/util/math/ChunkPos;I)Ljava/util/stream/Stream;", shift = At.Shift.BEFORE))
	private void noisium$addChunkSectionBiomeContainersToSet(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor, CallbackInfo ci, @Local Set<RegistryEntry<Biome>> set) {
		for (ChunkSection chunkSection : chunk.getSectionArray()) {
			chunkSection.getBiomeContainer().forEachValue(set::add);
		}
	}

	/**
	 * Replaces {@link ChunkGenerator#addStructureReferences} with a simpler one, that only checks the center chunk, instead of iterating outwards.
	 * This fixes an infinite loop with {@link io.github.steveplays28.noisium.server.world.NoisiumServerWorldChunkManager}.
	 */
	@Inject(method = "addStructureReferences", at = @At(value = "HEAD"), cancellable = true)
	public void noisium$replaceAddStructureReferencesToFixAnInfiniteLoop(StructureWorldAccess world, StructureAccessor structureAccessor, Chunk chunk, CallbackInfo ci) {
		var chunkPos = chunk.getPos();
		int chunkPosStartX = chunkPos.getStartX();
		int chunkPosStartZ = chunkPos.getStartZ();
		var chunkSectionPos = ChunkSectionPos.from(chunk);
		var chunkPosLong = chunkPos.toLong();

		for (StructureStart structureStart : chunk.getStructureStarts().values()) {
			try {
				if (structureStart.hasChildren() && structureStart.getBoundingBox().intersectsXZ(
						chunkPosStartX, chunkPosStartZ, chunkPosStartX + 15, chunkPosStartZ + 15)
				) {
					structureAccessor.addStructureReference(chunkSectionPos, structureStart.getStructure(), chunkPosLong, chunk);
					DebugInfoSender.sendStructureStart(world, structureStart);
				}
			} catch (Exception e) {
				CrashReport crashReport = CrashReport.create(e, "Generating structure reference");
				CrashReportSection crashReportSection = crashReport.addElement("Structure");
				crashReportSection.add(
						"Id",
						() -> world.getRegistryManager().getOptional(RegistryKeys.STRUCTURE).map(
								structureTypeRegistry -> {
									var structureId = structureTypeRegistry.getId(structureStart.getStructure());
									if (structureId == null) {
										return "UNKNOWN";
									}

									return structureId.toString();
								}
						).orElse("UNKNOWN")
				);
				crashReportSection.add(
						"Name",
						() -> {
							var structureTypeId = Registries.STRUCTURE_TYPE.getId(structureStart.getStructure().getType());
							if (structureTypeId == null) {
								return "UNKNOWN";
							}

							return structureTypeId.toString();
						}
				);
				crashReportSection.add("Class", () -> structureStart.getStructure().getClass().getCanonicalName());
				throw new CrashException(crashReport);
			}
		}

		ci.cancel();
	}
}
