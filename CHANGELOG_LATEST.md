### Changed

- Moved an `@Inject` on `NoiseChunkGenerator#method_38332` to an `@Overwrite`
    - This allows other mods, such as [ishland](https://github.com/ishland)'s mods, to change the `Executor` of
      `NoiseChunkGenerator#populateNoise`'s `CompletableFuture`.
