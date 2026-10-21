# Simple Radio Repeaters 0.5.1 prototype distribution

This release ships two Fabric Minecraft 1.20.1 JARs for Simple Radio **1.20.1-4.5.7.9**:

| Distribution | Purpose | Bytes |
| --- | --- | ---: |
| `simple-radio-repeaters-fabric-1.20.1-0.5.1-prototype.jar` | Core relay hardware, network, reload fix and diagnostics | 204,648 |
| `simple-radio-repeaters-dimensional-foci-fabric-1.20.1-0.5.1-prototype.jar` | Optional dimensional expansion; requires core 0.5.1 | 30,986 |

Replace the old addon JAR on both server and clients. Install core alone for native dimensional behavior, or both JARs for focus-only dimensional communication. The expansion overrides Simple Radio's dimensional option whether it is on or off, without editing that configuration. It has one independent `netherTweaks` option, enabled by default.

The core JAR contains no focus items, assets, recipes or dimensional overrides. The expansion contains those resources and depends on the core's attachment and propagation interfaces. Existing focus item IDs remain unchanged. Removing the expansion clears unknown saved attachment effects; it does not leave a projected relay behind. Missing focus items follow Minecraft's normal missing-item handling.

## Packaging

The prototype filter removes twelve core and five expansion GameTest fixture classes, their `fabric-gametest` entrypoints, and twelve audited core test-only helper methods including synthetic bodies. Packaging rejects removals still referenced by retained code. `TowerManager.records()` now remains because runtime diagnostics uses it. Ordinary diagnostics, serialization and Minecraft hooks remain.

All 642 retained methods in 75 core classes and 33 methods in seven expansion classes are byte-identical to the full builds, including debug data. No obfuscator or general optimizer is used. Addon names, source filenames and line numbers remain intact. Minecraft references retain Fabric's required intermediary mappings. Runtime resources and mixin configuration match the full builds.

## Validation

The packaging build and all 51 unit tests pass. Minecraft tests pass for all 46 core-only addon cases and all 69 combined addon cases. The same four previously recorded native headless test failures remain in both runs: `SystemTest.shortCircuit`, `SystemTest.garbageCollection`, `SystemTest.openSystem` and `SystemTest.closedSystem`. Their causes remain unresolved; this does not establish four live-server bugs.

Production Fabric startup with the exact core prototype JAR, `development=false` and JVM `-Xverify:all` passed. Checks activated the native radio, wired receiver and transmitter with the new mixin and verified a ready Relay/Bank before clean shutdown. This run did not include a client join or live microphone playback. Current evidence is under `validation/0.5.1/`; earlier optional-module removal/startup checks remain historical under `validation/0.5.0/`.

## Rebuild

Use JDK 21 for Gradle; release classes target Java 17.

```sh
./gradlew build prototypeJar
./gradlew --continue :core:runGameTest :foci:runGameTest
python3 tools/verify_prototypes.py
```

Outputs are under `core/build/libs/` and `foci/build/libs/`. Each module's `build/prototype/packaging-report.json` records removals and checksums. `tools/PrototypeJar.java` performs the conservative filter using ASM supplied with Gradle. Source and ordinary development builds retain tests.

SHA-256:

```text
24fa510a6e4d042f9551b71391ad3c2604b6df84c10787d14e3c2b650a79d6d7  simple-radio-repeaters-fabric-1.20.1-0.5.1-prototype.jar
1aff77937113cecd527669d7350bf9ce4286c24c9f9bcbc103e19c77826ae9dc  simple-radio-repeaters-dimensional-foci-fabric-1.20.1-0.5.1-prototype.jar
```
