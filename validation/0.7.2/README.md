# Repeaters 0.7.2 validation

98 unit tests passed (79 core, 2 Foci, 17 Directional). Native Minecraft server fixtures passed all 83 core tests and all 129 combined core/Foci/Directional tests. Final XML reports and build logs are retained here.

Terrain tests exercise every requested anchor, interpolated points above and below ground, the +300% deep clamp, quarter-range effective distance, path averaging, sample-density invariance, sloping/reversed profiles and the unchanged free radius. Config tests verify that previous defaults adopt the new curve, custom legacy curves preserve their old shape, explicit points take precedence, invalid curves reject a reload, and read-only files still apply valid settings. The monitor's existing native survey fixture now also edits a curve point and verifies its percentage changes without new generator queries.

The first suite run hit an unrelated test-harness timing limit: the accelerated server completed the timer fixture's 3,000-tick allowance in under one real second. Its allowance is now 30,000 ticks; it still checks the real one-second deadline, duration_elapsed stop reason and generated report. Production timer code is unchanged. Both final server runs passed this fixture.

Runtime class changes are confined to the RepeatersConfig, TerrainMath and TerrainManager families. The handbook text and version metadata also change. Relay routing, monitor models/renderers, native hooks, scan cadence and sampling budgets are unchanged. Foci differs from 0.7.1 only in fabric.mod.json. Directional 0.2.9 retains SHA-256 dd686cb9668b49b29575de8faa231a585539517fb1fe3f49941afbf09093cbef.

The production/prototype verification checks identical runtime copies, Java 17 bytecode, module boundaries and test exclusion. Builds use JDK 21, Gradle 9.5.0 and Loom 1.17.21. The host-only unsupported-Unix-socket agent is not distributed. No live microphone playback or new visual capture was performed for this balance/configuration patch. Source archives exclude transient build, run, log and cache folders.
