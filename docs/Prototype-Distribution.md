# Simple Radio Repeaters 0.4.4 prototype distribution

`simple-radio-repeaters-fabric-1.20.1-0.4.4-prototype.jar` is the test-free distribution for Fabric Minecraft 1.20.1 and Simple Radio 1.20.1-4.5.7.9. Replace the previous addon JAR on both server and clients. Existing 0.4 worlds and configuration work directly.

The prototype removes 13 addon GameTest fixture classes, the `fabric-gametest` entrypoints and 13 audited test-only helper methods. Packaging rejects a removal if retained code still calls it. Minecraft overrides, serialization, hooks, record/enum machinery and normal diagnostics remain.

No obfuscator or general optimizer is used. Addon names, source filenames, line numbers and debugging information remain intact. Minecraft references keep Fabric's required intermediary mappings.

The JAR is 196,162 bytes. All 625 retained methods in 72 classes are byte-identical to the full 0.4.4 build, including debug data. Resources and mixin configuration match the full build. Compared with the 0.4.3 prototype, only the Relay renderer and version metadata change.

The build and 51 unit tests pass. A native client joined a production dedicated server running this exact JAR with `-Xverify:all`, checked socket lighting and ordinary blackstone-to-amethyst replacement, captured the in-world renderer, and shut down cleanly. Live microphone playback was not tested. The full GameTest baseline remains under `validation/0.4.3/` (60 addon passes and four unresolved native failures); current rendering and packaging evidence is under `validation/0.4.4/`.

## Rebuild

Use JDK 21 for the build tools; release classes target Java 17.

```sh
./gradlew build prototypeJar
```

Output is under `build/libs/`. `build/prototype/packaging-report.json` records removals and checksums. `tools/PrototypeJar.java` performs the conservative filter using ASM supplied with Gradle; no extra runtime dependency is added. Regular development builds and the source bundle retain tests.

SHA-256: `94fcd26aa0f2e9f42575149d7a296acd2328d808a1f68949bd45c2d2e124df66`.
