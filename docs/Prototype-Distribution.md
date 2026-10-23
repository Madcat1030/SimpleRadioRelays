# Distribution builds

Run `./gradlew build prototypeJar` with JDK 21. All release JARs target Java 17.

Each module writes its normal runtime JAR and an identical `-prototype.jar` copy under `build/libs/`. Install one copy per mod. The prototype name is retained for existing testing workflows; it no longer denotes different bytecode.

GameTests live under `src/gametest`, unit tests under `src/test`; neither is included in release JARs. There is no custom obfuscation, shrinking, method deletion or descriptor rewriting. Standard Fabric remapping is retained.

Run `python3 tools/verify_prototypes.py` to verify identical copies, test exclusion, Java 17 bytecode and module dependency boundaries. See [Development](Development.md) for the separate Minecraft test commands.
