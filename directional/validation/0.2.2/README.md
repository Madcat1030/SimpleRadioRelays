# Directional Antenna 0.2.2 — chunky alternate

The chunky dish uses whole model units, 0°/45° native element angles, broad bowl terraces and thicker hardware. The refined 0.2.1 model is retained unchanged in an optional built-in resource pack and its own embedded-texture Blockbench project. Both use one texture pixel per model unit.

`build.log` and `unit/` record the build and 18 passing existing unit checks. `prototype-verification.json` checks both models' UV density, the default's integer geometry/minimum thickness/angle set, native model bounds and test-free packaging. `change-scope.json` checks that all runtime classes except client resource-pack registration retain their 0.2.1 bytes, the previous refined model is preserved byte-for-byte, and recipes, tags, array resources and textures are unchanged. Full server GameTests were not repeated for the appearance change.

The temporary QA client renders the default at all seven aims, both appearances on the native mast in one comparison frame, then selects the actual built-in resource pack and reloads resources. It checks that the real block now bakes as the retained refined alternate before capturing that state. `native-capture.json` records the raw screenshot hashes and successful native checks. No external renderer or image synthesis is used; these are native model sheets, not placed-world scenes. No live microphone playback is asserted.

To reproduce on a desktop:

```sh
./gradlew :directional:runClient -I directional/validation/0.2.2/client-preview.gradle
```

`capture_native.py` additionally starts the task host's virtual display. That host setup and the QA mod are outside the runtime distribution. In normal play, select the refined alternate under Options → Resource Packs; disable it for the chunky default. Both styles are included in the same JAR and use the same registered block/item and server behavior.
