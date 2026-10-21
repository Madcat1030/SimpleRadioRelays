# Simple Radio Directional Antenna 0.2.2 prototype

Fabric Minecraft 1.20.1 · Simple Radio **1.20.1-4.5.7.9** · Java 17+

Adds a mast-mounted **Parabolic Receiver** and a cheaper **Wide-Angle Receiver Array**. Both improve the attached station's reception in the direction they face. Their bonuses depend on the underlying antenna and do not increase outgoing transmitter power or repair inherited audio degradation.

Replace the previous Directional Antenna JAR on both server and clients. Existing dishes keep their identity and aiming position.

0.2.2 adds a chunkier parabolic appearance: an eight-sided copper rim, broad bowl terraces, a substantial feed arm and paired wrap-around mast clamps. All authored parts and rotation origins use whole model units, every part is at least one unit thick, and element angles are 0° or ±45°. Each face maps one texture pixel to one model unit (16 pixels per block). Long braces are tiled instead of stretching a texture.

The finer 0.2.1 dish remains intact as an alternate in the same JAR. To use it, open **Options → Resource Packs**, enable **Simple Radio: Refined Parabolic Receiver**, then press Done. Disable that pack to return to the chunky default. This is a client appearance choice for the same block; reception, aiming, recipes, saved data and reserved cells retain their existing behavior.

The mod works with base Simple Radio. Repeaters and Dimensional Foci are optional; this workspace supplies their 0.5.2 source for integration checks. Install the Directional Antenna prototype JAR on server and clients alongside the base mod and its dependencies.

## Reception pattern

### Wide-angle array

The array has a 135° primary arc: **±67.5° from its aim**. Throughout that arc it supplies **half the parabolic's additional reception bonus**. A smooth shoulder fades that bonus to zero at ±90°. This scales the added allowance, not the station's ordinary reception or its total power. Multiple arrays and dishes select the strongest applicable bonus; they do not add together.

Its copper-framed open grilles, separate split receiving element, ceramic insulator, fixed mast clamps and swivelling head are actual Java block geometry with four 16×16 pixel textures. The visible reflector is approximately two blocks wide and two blocks high. Placement retains the common clear 3×3 selection/reservation plane for mounting and aiming.

The new recipe uses two copper ingots, three iron bars and one native copper wire:

```text
Copper ingot   Iron bars      Copper ingot
Iron bars      Empty          Iron bars
Empty          Copper wire    Empty
```

### Parabolic receiver

Angles are measured horizontally from the dish's facing direction. The solid reception wedge is 90° wide: 45° to either side. A smooth shoulder extends another 30° past each edge.

| Angle from facing | Beyond the full-gain edge | Extra gain remaining |
| ---: | ---: | ---: |
| 0–45° | 0° | 100% |
| 60° | 15° | 25% |
| 75° and beyond | 30° and beyond | 0% |

Only the extra gain fades. The station retains its ordinary omnidirectional reception everywhere. The pattern uses horizontal bearing; ordinary native distance and height loss still apply. Overlapping dishes use the strongest applicable bonus, rather than adding their gains.

At full gain, the receiver's distance allowance reaches **75% of the equivalent tower's transmission radius**, unless ordinary reception is already stronger. With default native settings, that gives an ordinary fresh transceiver enough incoming power for clear reception throughout that region. The transceiver can sometimes be heard beyond it using its own remaining transmission range. This is a reception allowance, rather than a hard wall at a fixed radius.

For a native station, the equivalent tower uses native Transmitter base power, the receiver's current antenna score and the native transmission loss. For a Relay, it uses that tower's first-relay departure power, including its catalyst and installed amplifier. Actual replies still depend on the outgoing tower packet's power and inherited audio quality. The dish preserves those normal limits.

## Placement and removal

### Horizontal aiming

An empty-main-hand right-click on any part of the dish turns its reflector 15° clockwise as viewed from above. Sneak-right-click turns it counterclockwise. There are seven positions relative to the mounting face: **−45°, −30°, −15°, 0°, +15°, +30°, +45°**. The stops do not wrap around. An action-bar message reports the selected angle.

The mast clamp and mounting arm stay fixed. The reflector, feed and full reception pattern swivel together; its wedge width and shoulder falloff remain the same. Aim changes immediately update receiver snapshots and invalidate cached relay connectivity. The aim survives chunk unload and server restart, including saved unloaded relays. Existing 0.1.0 dishes start straight out from their original mounting face.

The original fixed 3×3 reservation and selection plane is retained. Leave clearance around the reflector for its visual sweep when building a housing. Rotating or mirroring a structure retains or mirrors its relative aim respectively.

1. Build a native antenna and core, with a Receiver or Relay attached normally. Wired antenna extensions are supported.
2. Right-click the **side of a vertical mast section** with the dish. The selected face is the direction the dish points.
3. The mast must have an actual antenna block immediately above it. Top sections and horizontal-only arms cannot host a dish. Both native non-top vertical visual variants qualify.
4. Keep the 3×3 vertical plane in front of that mast clear, including one row above and one row below the clicked section. Fluids and occupied cells prevent placement.

The dish reserves nine blocks: one center and eight selection/collision parts. Breaking any part with a stone-tier or better pickaxe removes the assembly and drops one dish. Removing the supporting mast or the antenna above it detaches the dish. Pistons cannot move it. Mount it higher if the lower reservation row would overlap a Relay, Bank or other station hardware.

No catalyst or upgrade is installed in the dish. The Receiver or Relay retains its existing catalyst and antenna requirements. A dish alone is not a radio station.

Prototype recipe:

```text
Iron ingot     Nether quartz   Iron ingot
Iron bars      Copper block    Iron bars
Empty          Copper wire     Empty
```

The wire is Simple Radio's copper wire. The recipe and visual model can be changed independently of the reception code.

## Repeaters and Dimensional Foci

Optional hooks apply the same directional reception to handheld-to-relay, relay-to-relay and relay-to-native-Receiver legs. Changing a dish publishes its new immutable profile and immediately clears Repeaters graph/path caches through its existing public invalidation hook. Its revision also participates in the periodic configuration fingerprint. No full configuration check is added to voice-packet processing. The core mod itself is unchanged.

The dish does not create dimensional access or a dish at a projected focus pocket. Its bonus applies to physical endpoints in the same actual dimension. Simple Radio's dimensional option, or Dimensional Foci's replacement rules and Nether attenuation, remain in control.

Verified dish positions and relay-to-mast bindings are saved in `simpleradio_directional_dishes.dat`. This lets a persistent unloaded Relay retain its dish. A loaded chunk whose dish is absent loses that saved effect. Missing neighboring chunks cause a retry, rather than a forced chunk load or permanent disconnection.

All scans and saved-data changes run on the main server thread. Audio hooks read immutable snapshots and perform numeric bearing/gain calculations. Native geometry scans are cached for up to 200 ticks, with dirty notifications prompting an earlier scan. Structural validation and saved-chunk reconciliation run every 20 ticks. Membership uses the exact native traversal, so a nearby, unconnected antenna cannot donate its dish.

## Model kit

Open the `.bbmodel` files directly in Blockbench. Their textures are embedded, so they do not depend on texture paths on this computer.

- `art/model-kit/Parabolic-Receiver.bbmodel`: default chunky dish, with whole-unit geometry.
- `art/model-kit/Parabolic-Receiver-Refined.bbmodel`: retained finer dish alternate.
- `art/model-kit/Wide-Angle-Receiver.bbmodel`: the new receiver, with embedded textures and separate fixed-mount/swivel groups.
- `art/model-kit/Native-Antenna-Column.bbmodel`: exact native supported-column variant.
- `art/model-kit/Native-Antenna-Vertical.bbmodel`: exact native non-column vertical variant.
- `art/model-kit/native-reference/`: the original two Java model JSONs and both original 16×16 textures, extracted losslessly from the pinned Simple Radio source JAR, with its GPL license.

The native top variants are intentionally excluded. Native reference projects are separate from the dish, so their mast cubes cannot accidentally become part of the exported receiver.

The dish's Java model is `src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver.json`. North is the base orientation: front points toward negative Z; the mast's block is one block behind the center, toward positive Z. The center block's normal coordinates are 0–16; the 3×3 reserved plane spans **X = −16…32 and Y = −16…32**. The mounting arm reaches into the supporting mast block. Keep the main dish inside that footprint when replacing the geometry. All other facings use blockstate rotations.

Keep the export origin and texture references, replace the Java model, and rebuild. Keep the fixed cubes named `mast_clamp` and `mounting_arm`, or name replacement fixed cubes with a `mount_` prefix; all other cubes are part of the swivel. The reflector pivot is **[8, 8, 8]** in model units. The build splits the fixed mount and swivel from that single editable JSON and generates the 28 facing/aim combinations. Angled reflector quads are transformed once when resources bake and then cached; there is no per-frame block-entity renderer. Resource packs that replace the baked layers directly should override the generated `_mount` and `_head` model JSONs. Editing the visual model does not change collision/reservation cells.

## Build and tests

This source workspace includes core/Foci 0.5.2 solely so optional integration can compile and be tested against the actual API. The Directional Antenna runtime JAR does not bundle or require them.

```sh
./gradlew :directional:build :directional:prototypeJar
./gradlew :directional:runGameTest
./gradlew :directional:runGameTest -PwithRepeaters
./gradlew :directional:runGameTest -PwithFoci
```

Use JDK 21 for Gradle; release bytecode targets Java 17. Runtime output is under `directional/build/libs/`; use the `-prototype.jar` to exclude addon GameTest fixtures. Names and debug data remain readable.

Recreate the native mast references from the pinned base-mod source archive with:

```sh
python3 directional/tools/make_model_kit.py /path/to/simpleradio-fabric-1.20.1-4.5.7.9-sources.jar
```

The authored generators are `tools/make_wide_array.py`, `tools/make_parabolic_receiver.py` (refined alternate and its built-in pack), and `tools/make_blocky_parabolic.py` (chunky default). Generate the refined resources before the chunky model if recreating all assets. The native-reference extractor leaves both receiver types and their registrations/resources alone.

Full server/GameTest evidence is under `directional/validation/0.2.0/`; current appearance checks are under `0.2.2/`. Live microphone playback is outside automated checks.

For 0.2.0, all **18 unit tests**, all **18 standalone Directional Antenna Minecraft tests**, and all **91 combined addon Minecraft tests** pass. The new physical-array checks cover placement on a native mast, aiming from an outer panel, saved pattern identity, complete removal with one correct drop, and actual reception with half the dish's additional gain. The same four native headless tests listed below still fail; no addon test fails. The complete GameTest task therefore exits nonzero in both installations.

The graphical client capture resolves all seven aimed models and all four textures. Prototype verification confirms the two receiver types' mining tags, embedded Blockbench textures, readable runtime names and exclusion of test/capture helpers. No core or Foci code changes are included.

## Native model captures

0.2.2 captures are `art/Parabolic-Receiver-0.2.2-Comparison.png`, `-Native-Preview.png`, `-Aiming.png` and `-Refined-Alternate.png`. The comparison renders both models in the same native client frame. The final sheet is captured after enabling the real built-in pack and reloading resources; QA checks that the actual block model changed to the retained refined model. Reproduce with `./gradlew :directional:runClient -I directional/validation/0.2.2/client-preview.gradle`.

The build and 18 unit checks pass. Asset checks verify exact 1:1 UV density for both dishes, whole-unit default geometry, the 0°/45° element angles, and both packaged model sets. All server/radio class files are unchanged from 0.2.1; the sole class change registers the optional client resource pack. The previous refined model is preserved byte-for-byte. Current evidence is in `validation/0.2.2/`; no live microphone playback or new full GameTest run is claimed for this appearance update.

Earlier refined-dish captures remain at `art/Parabolic-Receiver-0.2.1-Native-Preview.png` and `art/Parabolic-Receiver-0.2.1-Aiming.png`, with their original evidence under `validation/0.2.1/`. Use the current 0.2.2 capture script above to reproduce both retained appearances from this source tree.

`art/Wide-Angle-Receiver-0.2.0-Native-Preview.png` shows the actual model on the native mast, plus front and rear details. `art/Wide-Angle-Receiver-0.2.0-Aiming.png` shows all seven aims. These are unretouched Minecraft client framebuffer screenshots using its baked block models, texture atlas and item lighting. No image synthesis or external 3D renderer is used. This is a native model sheet rather than a screenshot of a placed-world scene.

To reproduce the capture on a desktop with a normal display:

```sh
./gradlew :directional:runClient -I directional/validation/0.2.0/client-preview.gradle
```

The temporary capture mod is compiled outside the runtime source set. Its code is excluded from the distribution. `native-capture.json` records the raw screenshot hashes and successful model/texture checks. The array's source JSON is `models/block/wide_angle_receiver.json`; keep the `mount_` names on stationary parts and the swivel origin at [8,8,8]. `tools/make_wide_array.py` reproduces the authored geometry, textures, resource definitions and editable project.

## Earlier 0.1.1 validation results

All **15 unit tests pass**, including the baked reflector transforms for all 28 mounting/aim combinations, immutable quad reuse and the seven-step pattern. All **16 Directional Antenna Minecraft tests pass** with base Simple Radio alone. With Repeaters and Dimensional Foci installed, **all 80 addon Minecraft tests pass**. These include actual native reception after aiming, main/outer-panel interactions, end stops, blockstate and saved-data round trips, mirrors, and immediate invalidation of the real Repeaters network cache.

The four previously recorded native headless failures remain: `SystemTest.shortCircuit`, `SystemTest.garbageCollection`, `SystemTest.closedSystem` and `SystemTest.openSystem`. Consequently the complete GameTest command exits with four native failures in both installations; no addon test fails. Their causes remain unresolved. These results do not establish a live-server voice problem.

The runtime prototype excludes addon GameTest fixtures and their entrypoint, retains readable method names and Java 17 bytecode, and preserves every retained class/resource byte from the remapped build. Generated models resolve every facing/aim state to one fixed mount and one reflector. There was no graphical client session or live microphone playback in this run; client geometry was checked through baked-model unit tests. Earlier 0.1.0 production startup and real unload/reload evidence remains under its versioned validation directory.
