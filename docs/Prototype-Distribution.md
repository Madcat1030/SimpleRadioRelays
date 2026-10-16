# Simple Radio Repeaters 0.4.0 prototype distribution

`simple-radio-repeaters-fabric-1.20.1-0.4.0-prototype.jar` is a separate runtime-only copy of 0.4.0 for Fabric Minecraft 1.20.1 and Simple Radio 1.20.1-4.5.7.9. Replace the existing addon JAR; do not install both copies together. The mod ID, version, network protocol, saved data, gameplay code, assets and recipes remain the same.

The prototype removes all 13 addon GameTest fixture classes, the `fabric-gametest` entrypoints and 13 unused helper methods. The methods are test convenience constructors/overloads, an unused snapshot-list accessor and the two private lambda bodies used only by removed test constructors. Packaging rejects a helper removal if retained code still calls it. Minecraft overrides, serialization, mixin hooks, record/enum machinery and ordinary diagnostics remain available.

No obfuscator or general-purpose optimizer is used. Addon class/method names, source-file names, line numbers and other debug data remain intact. Minecraft references still use the required Fabric intermediary names so the JAR works in a normal installation.

The JAR is 187,165 bytes versus 271,883 bytes for the original, about 31% smaller. An independent class-file comparison confirms that all 586 retained methods in 68 classes are byte-identical, including debug attributes. Fields, constant pools, class attributes, mixin configuration and all runtime resources are unchanged. The only metadata change removes test entrypoints.

A production-mode dedicated server loaded the exact prototype JAR with JVM verification enabled. It verified a real antenna/catalyst Bank, a duplex Transponder, an amplifier and an End focus, then shut down cleanly. There were no missing-method, missing-class, verifier or mixin application failures. This check did not include a client join or microphone playback. The parent 0.4.0 unit/GameTest results remain historical validation of the unchanged gameplay code.

## Rebuild

Use the supplied source bundle and JDK 21:

```sh
./gradlew prototypeJar
```

The output is under `build/libs/` with the `-prototype.jar` suffix. `build/prototype/packaging-report.json` records excluded classes and methods and the output checksum. The tool uses ASM shipped with the pinned Gradle distribution; no extra runtime dependency is added to the mod.

`tools/PrototypeJar.java` performs the conservative filter; `tools/prototype.gradle` registers its separate task. Regular development builds retain their tests. The source bundle intentionally includes those tests for further development. Prototype verification and the production-server check are under `validation/0.4.0-prototype/`.

SHA-256: `ab4eb5fd7045bdfe85e85c36c2455561020af8e54bb4876c757affbcdd96fe2a`.
