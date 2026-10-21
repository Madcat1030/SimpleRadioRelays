# Directional Antenna 0.2.3 — finished original mesh

The default restores the original 32-panel stepped silhouette and bowl profile, with Simple Radio's actual antenna-column mesh, a continuous metal perimeter, copper binding straps, finished feed and paired mast clamps. Geometry uses whole model units and 1:1 texture density. Both solid designs are retained as optional built-in resource packs and embedded-texture Blockbench projects.

`build.log` records the build. The asset verifier checks the exact original panel outline/profile, native mesh texture reference, UV density, whole-unit geometry, both alternate packs and prototype packaging. The baseline runtime-class hashes are taken before the client registration edit; the change-scope report identifies any class differences. Server/radio behavior is outside this visual change, and full server GameTests are not repeated. Existing unit checks remain part of the build.

The QA client renders the default at seven aims, the original model beside the finished version, and each solid alternate after an actual resource-pack reload. `native-capture.json` records raw screenshot hashes and successful baked-model/material checks. These are native Minecraft model-preview sheets using its real atlas and mast; no external rendering or image synthesis is involved. No live microphone playback is claimed.

On a desktop, reproduce with:

```sh
./gradlew :directional:runClient -I directional/validation/0.2.3/client-preview.gradle
```

The task-host capture launcher also starts a software display. Host setup is separate from the addon: no virtual-display adaptation, Java probe agent or QA mod is shipped in the runtime JAR. In normal play, leave both appearance packs disabled for mesh, or select either named alternate in Options → Resource Packs.
