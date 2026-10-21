# Optional capture-host setup

These scripts record the disposable Linux development host used for the native screenshots. They are outside the mod's source sets and are never included in its JAR. A normal desktop needs only the documented Gradle `runClient` command.

For this workspace layout, copy `bootstrap.py`, `CaptureHostAgent.java` and `run_gradle.py` into `<workspace>/toolchain/`, then run `python3 bootstrap.py` there. It downloads Gradle 9.5.0, a JDK 21 archive, checksum-verified Debian virtual-display packages and the pinned Minecraft font assets. Other game dependencies are fetched by Loom. The default project path is `<workspace>/recovered/source/simple-radio-addons`; `RADIO_PROJECT_DIR` can override it.

Compile the host probe adapter and package it before using `run_gradle.py`:

```sh
./jdk/bin/javac --add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -d agent-classes CaptureHostAgent.java
./jdk/bin/jar cfm capture-host-agent.jar agent-manifest.mf -C agent-classes .
```

The supplied manifest identifies the agent entry point. The task host disallows Unix-domain sockets; the adapter makes the JDK/Loom availability probes report unsupported so they use their supported fallback. It uses the JDK's internal ASM in the development JVM and adds no classes to Fabric's classpath. The mod itself does not require an agent.

Run the versioned `capture_native.py` only after setting up that host runtime. It starts Xvfb and the native Minecraft client, verifies the actual baked models and pack reloads, then saves raw framebuffer screenshots. Generated game caches, binaries and downloads are not part of this source archive.
