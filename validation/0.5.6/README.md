# 0.5.6 validation

| Run | Passed | Failed |
| --- | ---: | ---: |
| Core unit tests | 55 | 0 |
| Foci unit tests | 2 | 0 |
| Directional unit tests | 17 | 0 |
| Core-only Minecraft addon tests | 63 | 0 |
| Core + Foci + Directional Minecraft addon tests | 108 | 0 |
| Directional-only Minecraft addon tests, without Repeaters/Foci | 20 | 0 |

Fabric Minecraft 1.20.1, Loader 0.19.3, Fabric API 0.92.5, Simple Radio 1.20.1-4.5.7.9, Voice Chat 2.6.18 and JDK 21 were used. Release bytecode targets Java 17. The tested native Simple Radio binary has SHA-256 `667528f774ba38426ad8bcbea5db260ec0841316b38e56302cb47037445066e8`.

## Behavior

The Transponder regression checks every input/output combination for Simplex, Duplex and Multicast, including matching frequency numbers in different AM/FM modes. It asserts exactly one accepted copy per allowed receiver and preserves encoded data and stream ownership. Additional checks exercise real panel clicks through all three modes, block save/client-update tags, legacy Simplex/Duplex data, saved relay routing, missing card sides and duplicate selected frequencies.

The updated wide-angle tests check 66/44/22/0 percent at 45/60/75/90 degrees, intermediate values, smooth joins, symmetry, monotonic falloff and native reception from a physically placed array. The parabolic profile and ordinary reception remain covered. Directional's separate server run starts without either Repeaters or Foci, confirming that the optional handbook contribution adds no required dependency.

The existing addon regressions remain, including native RF parity, wired antenna restoration, receiver lifecycle repairs, saved routing, catalysts, amplification/decay, controller ownership and performance capture. No delivery-plan cache or routing cadence optimization is introduced.

The initial concurrent run passed the new behavior tests but failed readiness assertions in two existing antenna fixtures. Those fixtures now run in separate named batches so their settling preconditions are isolated from concurrent world mutations. Their original assertions and timing checks remain. No production antenna or scanning logic changed. The initial XML/log and final passing reports are retained separately.

## Handbook client checks

A real Minecraft client uses the native font and book screen. It discovers the seven core chapters, the Foci chapter and the Directional chapter from their respective resources. A QA-only independent mod supplies a tenth chapter without changing core. The reader is exercised with each of the four production addon combinations:

| Foci | Directional | Chapters | English pages |
| --- | --- | ---: | ---: |
| Absent | Absent | 7 | 39 |
| Absent | Present | 8 | 48 |
| Present | Absent | 8 | 49 |
| Present | Present | 9 | 58 |

Every page fits within 14 native book lines. Link destinations are valid, recipe references resolve, and recipe display follows native page navigation. Eight unmodified framebuffer captures are in `screenshots/`, including the cover, contents, recipes, Multicast explanation and the new wide-angle values. The final capture screen places its pointer outside the page to keep tooltips from covering ingredients; the actual reader retains native ingredient tooltips.

The menu-only preview parses the shipped default crafting JSON with Minecraft's native recipe serializers and uses the same illustration builder as the actual reader. In game, the reader obtains recipes from the client's synchronized server recipe manager. No live microphone playback or remote-server recipe override session was performed. Only the font resources needed for rendering were staged; missing optional sound/window-icon asset messages in the headless client are unrelated to the addon checks.

## Packaging

`verify_packages.py` verifies all three prototype jars. Core/Foci's 859 retained methods, class metadata, debug information and resources match their full builds. Directional's retained runtime entries are byte-identical to its full build. GameTests, test entrypoints, QA resources and unused test helpers are excluded. No obfuscation is applied. Chapter ownership checks confirm that optional text and recipe definitions live in their own addon jars. All 74 source model/texture resources match 0.5.5 exactly, so the previous pixel-density and geometry audits still apply without repeating them.

`results.json`, unit XML, the three final server XML reports, native client log, screenshots, packaging reports and source-change listing retain the evidence. The source-change listing excludes validation-only files.

## Reproduce

```sh
./gradlew -I validation/0.5.6/server-check.gradle -PwithFoci build prototypeJar :core:runGameTest :directional:runGameTest --continue
./gradlew -I validation/0.5.6/server-check.gradle :directional:runGameTest
./gradlew -I validation/0.5.6/handbook-preview.gradle -PwithFoci :directional:runClient
python3 validation/0.5.6/verify_packages.py
```

The last client command needs a working display/OpenGL context. Save the combined server XML before the standalone run overwrites the Directional run report. The QA-only selector runs addon tests and excludes the native mod's separate suite, whose prior headless failures are documented in earlier validation folders. None of the QA launcher mods is shipped in a distribution jar.
