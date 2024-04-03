### Added

- Forge support
- NeoForge support
- `GenerationShapeConfig` caching optimisation
    - `horizontalCellBlockCount` and `verticalCellBlockCount` are now cached, which skips a `BiomeCoords#toBlock` call every time these
      methods are invoked
- ReTerraForged, Nether Depths, and Lost Cities compatibility
    - The main optimisation (`NoiseChunkGenerator#populateNoise`) has been rewritten to improve compatibility, by using an `@Inject` and
      a `@Redirect` instead of an `@Overwrite`
