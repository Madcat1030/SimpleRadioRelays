# Development

Use JDK 21 for Gradle; all three mods target Java 17 and Fabric Minecraft 1.20.1. The wrapper pins Gradle 9.5.0 and Loom 1.17.21. Simple Radio is pinned to `1.20.1-4.5.7.9`.

```sh
./gradlew build prototypeJar
python3 tools/verify_prototypes.py
./gradlew :core:runGameTest
./gradlew :directional:runGameTest
./gradlew :directional:runGameTest -PwithFoci
```

`build` runs JUnit tests. Run the three server commands separately to cover core alone, Directional alone, and all addons together. `-PwithRepeaters` also supports testing Directional with core and without Foci. Server tests select this workspace's fixtures; they do not run Simple Radio's independent test suite. Reports are written to the running module's `run/gametest/reports/` directory. These tests do not establish live microphone playback quality.

Production code belongs in `src/main`, unit tests in `src/test`, and Minecraft fixtures in `src/gametest`. Each GameTest source set is a development-only Fabric mod. Foci's tests can use core's parity-test helpers; neither is packaged in a release. The ordinary JAR and `-prototype.jar` are byte-identical. Packaging performs no custom shrinking or obfuscation; Fabric's normal Minecraft symbol remapping remains necessary.

## Optional reception integration

Directional depends on Simple Radio at runtime. It compiles against Repeaters only for optional integration. Core discovers `RelayIntegration` implementations through the `simpleradio_repeaters:integration` Fabric entrypoint once during initialization. Without core, Fabric never instantiates that entrypoint; Directional's native receiver hooks continue to work independently.

The extension exposes reception adjustments, antenna-dirty and relay-tick notifications, and a configuration stamp. It does not control routing, dimensional mapping, or transmit power. Foci continues to install the separate dimensional `RadioPolicy`.

All integration calls run on the server thread. Reception methods read cached state and must not scan the world. A connected dish change publishes a new immutable reception view and invalidates the graph immediately. Native receiver membership changes publish their own bindings without invalidating relay links. No-op scans preserve the relay revision. Standalone and combined GameTests cover these boundaries.

Directional 0.2.9 can run without Repeaters, or alongside Repeaters 0.5.7+. It rejects older installed cores because they do not expose this API. Foci 0.7.3+ requires core 0.7.3+ with the same Foci API number, independently of either release number. See [Foci compatibility](Foci-Compatibility.md).

Native wire entities use Fabric's server entity lifecycle events. Destruction immediately rescans affected loaded antennas or invalidates their saved records; normal tracking/chunk unloading preserves them. New wires are inspected at the end of the tick, after native socket attachment. Directional retains verified mast membership during incomplete or temporarily smaller restoration scans, with a bounded retry once all prior mast chunks are available. Confirmed wire cuts bypass that grace. These notifications do not force-load chunks or add per-packet world scans.

`AntennaWireMixin` pauses only native `tickDeath` for a missing router on a previously verified relay extension whose historical dependencies are unavailable. It resets the native timeout so sockets can settle after reload. Explicit `burnOut`/destruction bypasses that hook; native expiry resumes when the dependencies are available. Ordinary audio wires without a verified relay antenna footprint keep their native behavior.

## Profiling before optimization

Use `/radiorepeaters test start 60`, or `test start` followed by `test stop`. JSON reports appear in `logs/radiorepeaters/`. Compare a typical network with the expanded network using the same talker and receivers. This release does not add delivery-plan caching or reduce packet checks. Keep the existing frame/receiver checks when investigating future optimizations.

`/radiorepeaters` is the only command root; the former singular alias was removed in 0.5.8.

## Terrain propagation

`terrain/TerrainManager` owns the bounded server-thread height-query queue. `TerrainProfiles` stores only physical relay terrain samples in world SavedData. RF graphs and channel ports share those samples; mast-height commits and completed profiles publish on the Overworld's 200-tick boundary. The native antenna base remains the RF origin, while the independently saved mast tip determines environmental clearance. Device checks retain only an expiring scalar result and sampled endpoint coordinates, with no device profile arrays or disk writes. Their reach/travel expression hooks compose with the Foci and Directional hooks.

`TerrainMathTest` covers clearance, density-independent weighting, sloping rays, elevation bounds, movement and free-radius boundaries. `TerrainGameTests` exercises the real noise generator without loading chunks, actual mast-tip scans, native travel parity, device resampling and persisted relay-profile reuse. The profile report snapshots include `terrain_settings` and cumulative `terrain_work`, so generator work can be measured independently of routing time. See [terrain settings](Terrain-and-Elevation.md) for the defaults and operator commands.

## Models and handbook

See [the model kit](../directional/art/model-kit/README.md) for editable Blockbench projects and [handbook extensions](Handbook-Extensions.md) for addon-owned chapters. Receiver asset generation must not overwrite translated tooltips or handbook text. Historical native captures and test evidence remain under the versioned `validation/` directories.


## Terrain follow-up (0.6.1)

Relay ground profiles are referenced by registered physical mast columns; they are no longer an LRU cache. Clearance memoization is still bounded because a missed clearance calculation is cheap and never requests generator work by itself. A per-relay-pair last result preserves established environmental penalties during remapping. Pending mapping is a display state, not an eligibility flag.

Full-mode preflight uses the actual native source type/power or the relay departure power and the existing reception integrations before admitting terrain work. Device scalar entries expire by inactivity instead of packet traversal order; the protective capacity fallback uses ordinary propagation and is counted explicitly. Sampling has separate relay/device queues, a time budget with a count ceiling, and 3:1 relay priority. Native hooks remain MixinExtras expression modifications.

The attenuation curve averages extra-distance fractions. Keep this distinction explicit: a 100% extra-distance penalty gives approximately half range; it is not a 100% loss of power or range. Profile persistence, native/relay RF parity and capacity tests must exercise both clear and buried paths.


## Relay Monitor (0.7.0)

Passive card attachments override `routingCards()` to return no forwarding cards. Both the loaded Relay refresh and authoritative controller reconciliation use that method, so a monitor cannot become a saved relay node. Hardware readiness is separate from forwarding eligibility.

`RelaySurvey` predicts direct first-departure links through the existing `RadioSpace`, `NativeRadioAdapter` and `TerrainManager` APIs. It does not create native frequencies or emit sources. `TowerManager.revision()` changes only on actual network invalidation; each monitor reuses its survey until the card, attached relay or revision changes. The client receives the current text page and card inventory, with no exact TX-only location. Compass binding resolves the selected node server-side and writes a static vanilla lodestone target with validation disabled.


## Confirmed antenna edits (0.7.4)

`AntennaBlockChangeMixin` observes successful block-identity replacements through `LevelChunk.setBlockState`. It does not run for chunk unloading or ordinary property changes such as transmitter activity flags. `TowerManager` narrows those events through a chunk index and the saved footprint. Known structural destruction immediately persists an invalid record; geometry scans are coalesced and wait until the next server tick so zero-delay native antenna ticks can settle. Confirmed complete scans may publish zero immediately. Non-destructive changes still wait when historical dependencies are unavailable. The existing wire lifecycle path and periodic restoration verification keep their original semantics.


## Antenna-dependent station power (0.7.5)

`TowerPower` owns the shared base multiplier. Two composable return-value hooks adjust only native block Transmitter/Receiver types; handheld, Radio and third-party frequencing types retain their power. `NativeRadioAdapter` uses the same rule for virtual tower TX and RX, before catalyst/hop limits and optional reception integrations. Recovery uses the saved raw antenna score; effective amplifier/elevation bonuses remain additive. Native source initialization picks up the adjusted power, while already-powered packets are not scaled again. No shared native configuration value is rewritten.

The boolean `antennaDependentBasePower` is independent of terrain mode and participates in the existing configuration fingerprint. The existing config reload path invalidates graphs without discarding terrain samples. Operator diagnostics record the flag and ANT-zero powers. `TowerPowerGameTests` covers native power/reach/travel, disabled parity, unchanged radio types, amplifier/catalyst/decay interaction and saved-only graph changes.
