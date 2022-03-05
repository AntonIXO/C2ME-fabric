package com.ishland.c2me.mixin.optimization.worldgen.global_biome_cache;

import com.ishland.c2me.base.common.GlobalExecutors;
import com.ishland.c2me.common.optimization.worldgen.global_biome_cache.IGlobalBiomeCache;
import com.ishland.c2me.base.common.util.PalettedContainerUtil;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class MixinThreadedChunkAnvilStorage {

    @Shadow
    protected abstract CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> loadChunk(ChunkPos pos);

    @Shadow
    @Final
    private ChunkGenerator chunkGenerator;

    @Redirect(method = "getChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ThreadedAnvilChunkStorage;loadChunk(Lnet/minecraft/util/math/ChunkPos;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> redirectLoadChunk(ThreadedAnvilChunkStorage threadedAnvilChunkStorage, ChunkPos pos) {
        if (chunkGenerator.getBiomeSource() instanceof IGlobalBiomeCache source)
            return this.loadChunk(pos).thenApplyAsync(either -> {
                either.left().ifPresent(chunk -> {
                    for (ChunkSection chunkSection : chunk.getSectionArray()) {
                        final ChunkSectionPos chunkSectionPos = ChunkSectionPos.from(chunk.getPos(), chunkSection.getYOffset());
                        final RegistryEntry<Biome>[][][] biomes = source.preloadBiomes(chunkSectionPos, chunk.getStatus().isAtLeast(ChunkStatus.FEATURES) ? null : PalettedContainerUtil.toArray(chunkSection.getBiomeContainer(), 4, 4, 4), chunkGenerator.getMultiNoiseSampler());
                        PalettedContainerUtil.writeArray(chunkSection.getBiomeContainer(), biomes);
                    }
                });
                return either;
            }, GlobalExecutors.invokingExecutor);
        else
            return this.loadChunk(pos);
    }

}
