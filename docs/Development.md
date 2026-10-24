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

Directional 0.2.9 can run without Repeaters, or alongside Repeaters 0.5.7+. It rejects older installed cores because they do not expose this API. Foci uses its matching core version.

Native wire entities use Fabric's server entity lifecycle events. Destruction immediately rescans affected loaded antennas or invalidates their saved records; normal tracking/chunk unloading preserves them. New wires are inspected at the end of the tick, after native socket attachment. Directional retains verified mast membership during incomplete or temporarily smaller restoration scans, with a bounded retry once all prior mast chunks are available. Confirmed wire cuts bypass that grace. These notifications do not force-load chunks or add per-packet world scans.

`AntennaWireMixin` pauses only native `tickDeath` for a missing router on a previously verified relay extension whose historical dependencies are unavailable. It resets the native timeout so sockets can settle after reload. Explicit `burnOut`/destruction bypasses that hook; native expiry resumes when the dependencies are available. Ordinary audio wires without a verified relay antenna footprint keep their native behavior.

## Profiling before optimization

Use `/radiorepeaters test start 60`, or `test start` followed by `test stop`. JSON reports appear in `logs/radiorepeaters/`. Compare a typical network with the expanded network using the same talker and receivers. This release does not add delivery-plan caching or reduce packet checks. Keep the existing frame/receiver checks when investigating future optimizations.

`/radiorepeaters` is the only command root; the former singular alias was removed in 0.5.8.

## Models and handbook

See [the model kit](../directional/art/model-kit/README.md) for editable Blockbench projects and [handbook extensions](Handbook-Extensions.md) for addon-owned chapters. Receiver asset generation must not overwrite translated tooltips or handbook text. Historical native captures and test evidence remain under the versioned `validation/` directories.
