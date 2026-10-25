# 0.6.0 validation

Repeaters core contains the environmental implementation. No Foci or Directional source files changed from the 0.5.8 source release. Foci is repackaged as 0.6.0 solely for its exact core-version metadata dependency. Directional 0.2.9 remains standalone and compatible.

The final modes are `off`, `relay_mapping` (fresh-install default), and `full`. Off restores the pre-terrain reach/travel calculations, disables the elevation bonus and stops sampling. Full adds device checks with a 256-block free radius and a movement threshold strictly greater than 16 blocks. Device checks retain only an expiring scalar result and sampled endpoint coordinates; they allocate no retained profile array and write no device paths to SavedData.

## Results

- 84 JUnit tests passed: 65 core, 2 Foci, 17 Directional.
- 69 core-only Minecraft server tests passed.
- 21 standalone Directional Minecraft server tests passed without Repeaters or Foci.
- 115 combined Minecraft server tests passed with all three addons.

The final XML reports and unit summaries are alongside this file. Server tests select this workspace's fixtures rather than the independent upstream Simple Radio suite. GameTest execution uses a Java 21 development host; release classes target Java 17. These checks do not establish live microphone playback quality or a workload-specific production performance ceiling.

New engine tests query a real vanilla noise generator at distant coordinates without loading chunks. Physical mast tests cover growth/trimming; relay tests exercise saved profile roundtrips, power/dish graph rebuild reuse and delayed height commits. Device tests cover native reach/travel agreement, no engine calls on the routing invocation, the inclusive 256-block exemption, the strict 16-block movement threshold and absence of device saved profiles. Off-mode tests cancel pending work and restore ordinary native delivery. Unit tests cover smooth clearance weighting, resolution-independent attenuation, sloping rays, bounded elevation, three-mode configuration and sample-count limits.

The environment's incremental remapper can produce an empty output archive despite reporting success. Packaging therefore reruns the release remap tasks and verifies metadata, classes, source/test exclusion, Java class versions and ordinary/prototype byte identity before distribution. No tests, host agents or custom obfuscation are included in the production JARs.

See `docs/Terrain-and-Elevation.md` for the implementation scope, defaults, command controls, work budgets, persistence and startup behavior. Existing saved relay records acquire their tip on their next verified loaded scan; no chunk is force-loaded to migrate them.
