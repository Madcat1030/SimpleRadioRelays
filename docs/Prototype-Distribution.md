# Simple Radio Repeaters 0.5.2 prototype distribution

This release ships two Fabric Minecraft 1.20.1 JARs for Simple Radio **1.20.1-4.5.7.9**:

| Distribution | Purpose | Bytes |
| --- | --- | ---: |
| `simple-radio-repeaters-fabric-1.20.1-0.5.2-prototype.jar` | Core relay hardware, network, lifecycle fixes and diagnostics | 206,269 |
| `simple-radio-repeaters-dimensional-foci-fabric-1.20.1-0.5.2-prototype.jar` | Optional dimensional expansion; requires core 0.5.2 | 30,985 |

Replace the old addon JAR on both server and clients. Install core alone for native dimensional behavior, or both JARs for focus-only dimensional communication. The expansion overrides Simple Radio's dimensional option whether it is on or off, without editing that configuration. It has one independent `netherTweaks` option, enabled by default.

The core JAR contains no focus items, assets, recipes or dimensional overrides. The expansion contains those resources and depends on the core's attachment and propagation interfaces. Existing focus item IDs remain unchanged. Removing the expansion clears unknown saved attachment effects; it does not leave a projected relay behind. Missing focus items follow Minecraft's normal missing-item handling.

## Packaging

The prototype filter removes thirteen core and five expansion GameTest fixture classes, their `fabric-gametest` entrypoints, and twelve audited core test-only helper methods including synthetic bodies. Packaging rejects removals still referenced by retained code. `TowerManager.records()` remains because runtime diagnostics uses it. Ordinary diagnostics, serialization and Minecraft hooks remain.

All 645 retained methods in 76 core classes and 33 methods in seven expansion classes are byte-identical to the full builds, including debug data. No obfuscator or general optimizer is used. Addon names, source filenames and line numbers remain intact. Minecraft references retain Fabric's required intermediary mappings. Runtime resources and mixin configuration match the full builds.

## Validation

The packaging build and all 51 unit tests pass. Minecraft tests pass for all 50 core-only addon cases and all 73 combined addon cases. The same four previously recorded native headless test failures remain in both runs: `SystemTest.shortCircuit`, `SystemTest.garbageCollection`, `SystemTest.openSystem` and `SystemTest.closedSystem`. Their causes remain unresolved; this does not establish four live-server bugs.

Production Fabric startup with the exact core prototype JAR, `development=false` and JVM `-Xverify:all` passed. Checks activated the native radio, wired receiver and transmitter with the lifecycle mixins and verified a ready Relay/Bank before clean shutdown. This run did not include a client join or live microphone playback. Current evidence is under `validation/0.5.2/`; earlier optional-module removal/startup checks remain historical under `validation/0.5.0/`.

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
e6bcc0c6db8dbde6b4670d83be1ac9bc7b83690217c5fad96572ccdd9361b753  simple-radio-repeaters-fabric-1.20.1-0.5.2-prototype.jar
8892ef1ea47288faa1b2291827a33124aa17dce92c5c748179308b22e539e1a6  simple-radio-repeaters-dimensional-foci-fabric-1.20.1-0.5.2-prototype.jar
```
