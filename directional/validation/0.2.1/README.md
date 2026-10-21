# Directional Antenna 0.2.1 — model update

This release replaces the parabolic placeholder with an authored faceted dish, rolled copper rim, inner bevel, compact horn and twin feed rails. The mast clamps and swivel match the wide-angle array. The registered block/item IDs are retained, so existing dishes adopt the model when resources reload.

`build.log` records the successful build. `unit/` contains the 18 passing existing unit checks. `change-scope.json` compares all runtime class bytes with 0.2.0 and records unchanged array, recipe and mining-tag resources. There is no routing, reception or placement change. Full Minecraft functional checks were not repeated for this visual change; their 0.2.0 results remain in the previous validation directory.

`prototype-verification.json` checks all 1,500 dish faces for one texture pixel per model unit, five 16×16 materials, exact shared hardware, correspondence with the packaged model layers and exclusion of capture/test helpers. Long feed rails are subdivided rather than fitting more than 16 units onto a 16-pixel image. Rotations preserve lengths and do not use element rescaling.

`native-capture.json` records the two raw framebuffer screenshots and native model/texture validation. `preview/` is temporary client-only QA source outside the shipped mod. The screenshots are model-preview sheets inside Minecraft 1.20.1, including its native mast, texture atlas and renderer; they are not placed-world scenes. There is no live microphone playback claim.

To reproduce on a desktop:

```sh
./gradlew :directional:runClient -I directional/validation/0.2.1/client-preview.gradle
```

The task-host `capture_native.py` also starts a virtual display; its toolchain paths are specific to this workspace. No display adaptation or capture mod is included in the runtime JAR.
