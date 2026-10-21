# Directional Antenna 0.2.4 — solid stepped receiver

The default is a solid version of the original 32-panel stepped dish. Panel front positions and the outer silhouette match the 0.2.3 mesh exactly. Three-unit-thick panels meet across the largest terrace changes. New opaque 16×16 materials provide pale metal fronts, darker backing and a steel frame. The feed, rear carrier and fixed wrap-around clamps remain intact. Two hinges are offset four units behind the solid face to avoid coplanar flicker found in the first capture. Default geometry is orthogonal and whole-unit, with 1:1 UV density.

The exact previous mesh, octagonal and refined model JSONs are retained as canonical assets and optional built-in appearance packs. Their embedded-texture Blockbench projects are included alongside the solid default.

`build.log` records the build and existing unit checks. `prototype-verification.json` covers shell connectivity, opacity, UV dimensions, original panel positions, all three packaged alternates and test-free packaging. `change-scope.json` compares runtime Java source with the recovered 0.2.3 baseline. Only client registration of the mesh appearance pack changes; radio/server code is unchanged.

The QA client renders the default in seven aiming positions, the new solid and archived mesh side by side, and all three alternates after actual resource-pack reloads. `native-capture.json` records screenshot hashes and native baked-model/material checks. These are raw Minecraft 1.20.1 model-preview framebuffer screenshots using its native mast and atlas. No external renderer or image synthesis is used.

On a desktop:

```sh
./gradlew :directional:runClient -I directional/validation/0.2.4/client-preview.gradle
```

The disposable Linux capture-host setup is retained under `validation/0.2.3/host-setup/`. No host adapter or preview helper is shipped in the runtime JAR. Full server GameTests are not repeated for this appearance-only update.
