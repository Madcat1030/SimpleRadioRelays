# Simple Radio Repeaters 0.5.0 prototype distribution

This release ships two Fabric Minecraft 1.20.1 JARs for Simple Radio **1.20.1-4.5.7.9**:

| Distribution | Purpose | Bytes |
| --- | --- | ---: |
| `simple-radio-repeaters-fabric-1.20.1-0.5.0-prototype.jar` | Core relay hardware and network | 192,744 |
| `simple-radio-repeaters-dimensional-foci-fabric-1.20.1-0.5.0-prototype.jar` | Optional dimensional expansion; requires core 0.5.0 | 30,984 |

Replace the old addon JAR on both server and clients. Install core alone for native dimensional behavior, or both JARs for focus-only dimensional communication. The expansion overrides Simple Radio's dimensional option whether it is on or off, without editing that configuration. It has one independent `netherTweaks` option, enabled by default.

The core JAR contains no focus items, assets, recipes or dimensional overrides. The expansion contains those resources and depends on the core's attachment and propagation interfaces. Existing focus item IDs remain unchanged. Removing the expansion clears unknown saved attachment effects; it does not leave a projected relay behind. Missing focus items follow Minecraft's normal missing-item handling.

## Packaging

The prototype filter removes nine core and five expansion GameTest fixture classes, their `fabric-gametest` entrypoints, and 13 audited core test-only helper methods. Packaging rejects removals still referenced by retained code. Ordinary diagnostics, serialization and Minecraft hooks remain.

All 619 retained methods in 72 core classes and 33 methods in seven expansion classes are byte-identical to the full builds, including debug data. No obfuscator or general optimizer is used. Addon names, source filenames and line numbers remain intact. Minecraft references retain Fabric's required intermediary mappings. Runtime resources and mixin configuration match the full builds.

## Validation

The clean packaging build and all 51 unit tests pass. Minecraft tests pass for all 41 core-only addon cases and all 64 combined addon cases. The same four previously recorded native headless test failures remain in both runs: `SystemTest.shortCircuit`, `SystemTest.garbageCollection`, `SystemTest.openSystem` and `SystemTest.closedSystem`. Their causes remain unresolved; this does not establish four live-server bugs.

Production Fabric startup with the exact prototype JARs, `development=false` and JVM `-Xverify:all` passed. Checks exercised components, amplifier gains, focus state, the expansion's live configuration command and a saved-world restart after removing the expansion. Fabric also correctly rejected the expansion without core. This run did not include a client join or live microphone playback. Current evidence is under `validation/0.5.0/`; older evidence is retained under versioned directories.

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
f8bc17d391a1b99469538e50e21ca8540edc1f729edba86fa88c68f491afd07c  simple-radio-repeaters-fabric-1.20.1-0.5.0-prototype.jar
8b9bef2d20b52d8ef64575896f96b19dc22687429f49215e5f1376ca85a21bc1  simple-radio-repeaters-dimensional-foci-fabric-1.20.1-0.5.0-prototype.jar
```
