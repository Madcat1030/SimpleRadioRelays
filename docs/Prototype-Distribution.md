# Simple Radio Repeaters 0.4.3 prototype distribution

`simple-radio-repeaters-fabric-1.20.1-0.4.3-prototype.jar` is a separate runtime-only copy of 0.4.3 for Fabric Minecraft 1.20.1 and Simple Radio 1.20.1-4.5.7.9. Replace the existing addon JAR; do not install both copies together. The mod ID, version, network protocol, saved data, gameplay code, assets and recipes remain the same.

The prototype removes all 13 addon GameTest fixture classes, the `fabric-gametest` entrypoints and 13 unused helper methods. The methods are test convenience constructors/overloads, an unused snapshot-list accessor and the two private lambda bodies used only by removed test constructors. Packaging rejects a helper removal if retained code still calls it. Minecraft overrides, serialization, mixin hooks, record/enum machinery and ordinary diagnostics remain available.

No obfuscator or general-purpose optimizer is used. Addon class/method names, source-file names, line numbers and other debug data remain intact. Minecraft references still use the required Fabric intermediary names so the JAR works in a normal installation.

The JAR is 196,035 bytes. Independent comparison confirms that all 625 retained methods in 72 classes are byte-identical to the full 0.4.3 build, including debugging data. Runtime resources and mixin configuration are unchanged; only addon fixtures, test entrypoints and audited helper methods are removed.

A production-mode dedicated server loaded the exact prototype JAR with JVM verification enabled. It verified a real antenna/catalyst Bank, a duplex Transponder, an amplifier and an End focus, then shut down cleanly. There were no missing-method, missing-class, verifier or mixin application failures. This check did not include a client join or microphone playback. The full 0.4.3 build passes 51 unit tests and 60 addon Minecraft GameTests; four previously recorded native headless test failures remain unresolved. The prototype preserves the full build's retained gameplay methods exactly.

## Rebuild

Use the supplied source bundle and JDK 21:

```sh
./gradlew prototypeJar
```

The output is under `build/libs/` with the `-prototype.jar` suffix. `build/prototype/packaging-report.json` records excluded classes and methods and the output checksum. The tool uses ASM shipped with the pinned Gradle distribution; no extra runtime dependency is added to the mod.

`tools/PrototypeJar.java` performs the conservative filter; `tools/prototype.gradle` registers its separate task. Regular development builds retain their tests. The source bundle intentionally includes those tests for further development. Prototype verification and the production-server check are under `validation/0.4.3/`.

SHA-256: `a8b32802a4a01d552adc4490f69b2dec2aad62d072acc54d8a3f658ae10d136b`.
