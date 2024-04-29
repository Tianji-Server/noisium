### Fixed

- Ad Astra compatibility
  - Ad Astra and other dimension mods no longer crash when trying to generate chunks in their dimensions
  - The code is also slightly faster and more concise, because the chunk sections array is now fetched directly from the chunk that's
    being generated, instead of recreating it before noise population, which may result in a very slight performance increase
