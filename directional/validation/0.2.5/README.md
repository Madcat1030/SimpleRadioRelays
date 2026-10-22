# Directional Antenna 0.2.5 — clean surfaces and shared mounts

Receiver surfaces are trimmed offline so intersections are represented by one rendered surface instead of coincident faces. The final transformed-polygon audit checks all five appearances at every aim, including opposed coplanar faces. Additional checks place each primary receiver against both actual native mast variants and combine opposite receivers at independent center/extreme aims.

Both the solid stepped dish and wide-angle array use whole-unit geometry, 16×16 textures and one texture pixel per model unit on every rendered face. The array's long members tile instead of scaling a 16-pixel patch over a longer bar. Existing alternate appearances remain selectable and receive the common mount and face cleanup.

Shared rings have their own baked model layer. A server-maintained `brackets` blockstate selects one owner per physical mast, independently of the receiver type or aim. Placement and removal update ownership immediately. The existing periodic block-entity pass repairs saved assignments after load and skips missing neighbor chunks. Each receiver retains its support arm and swivel.

`build.log` records the build and existing unit checks. `prototype-verification.json` records packaging, geometry, UV and mast/pair checks. `surface-cleanup.json` compares authored and finished face counts. `shared-mount-gametests.xml` records two focused tests on the actual Minecraft server: mixed dish/array ownership and removal handoff, plus east/west saved-state repair and missing-chunk behavior. The QA-only test selector excludes other tests from this focused run.

`native-capture.json` records unretouched native framebuffer PNGs. The client verifies all seven aims for both receivers, checks that `brackets=false` removes exactly the ring pair, and reloads all three actual built-in appearance packs. It captures both receivers and an opposite-side installation with a separate clamp close-up.

```sh
./gradlew :directional:build :directional:prototypeJar
python3 directional/tools/verify_distribution.py
./gradlew :directional:runGameTest :directional:runClient -I directional/validation/0.2.5/server-check.gradle -I directional/validation/0.2.5/client-preview.gradle
```

The optional disposable capture-host setup is retained under `validation/0.2.3/host-setup/`. No host adapter, QA selector, preview code or test fixture is shipped in the prototype. This run does not repeat the full radio integration suite or claim live microphone playback.
