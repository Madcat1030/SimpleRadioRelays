# 0.5.5 validation

| Run | Passed | Failed |
| --- | ---: | ---: |
| Core unit tests | 54 | 0 |
| Foci unit tests | 2 | 0 |
| Directional unit tests | 18 | 0 |
| Core-only Minecraft addon tests | 60 | 0 |
| Core + Foci + Directional Minecraft addon tests | 105 | 0 |

Environment: Fabric Minecraft 1.20.1, Loader 0.19.3, Fabric API 0.92.5, Simple Radio 1.20.1-4.5.7.9, Voice Chat 2.6.18, JDK 21 (release bytecode targets Java 17). Core/Foci are 0.5.5; Directional remains 0.2.6. Tested Simple Radio binary SHA-256: `667528f774ba38426ad8bcbea5db260ec0841316b38e56302cb47037445066e8`.

## Capture mechanics

`PerformanceTimingTest` checks empty histograms, measured totals, bucket percentile bounds and the largest elapsed value. `PerformanceGameTests` runs the real singular/plural command trees and native radio objects. It checks permissions, invalid duration, duplicate start, stop without capture, automatic elapsed-time completion, shutdown flushing and a fresh session after completion.

The delivery fixture has a translating tower and an output-channel peer, with both sites in unloaded terrain. A native transmitter callback changes branch power and re-enters routing through another transmitter. Capture-disabled and capture-enabled deliveries must have identical carrier power, inherited noise, relay counts, encoded byte identity, activity and stream ownership, with exactly one accepted copy per frame. No synthetic voice playback is performed.

Report assertions check both entrance passes, endpoint comparisons, callback RF changes, stable input state, receiver RF changes, unchanged tower snapshots, real path-cache hits, explicit invalidation reasons, separately labeled diagnostic graph/cache work and production graph misses. Nested exclusive phase totals must equal routing self time, while inclusive route time must include child work. Neither tower's terrain may be loaded by observation. Idle timed capture must write a report without inventing packets.

`core-only-example-capture.json` and `core-foci-directional-example-capture.json` are these short synthetic verification captures. They validate report contents; their cold JVM timings are **not** measurements of a player's network or evidence for a routing optimization. Gameplay capture instructions are in [the release notes](../../docs/Release-Notes-0.5.5.md).

## Existing behavior and packaging

Both server runs retain the addon regressions, including native RF parity, antenna-base origins, wired antenna reload, persisted tower reconciliation, transponders, receiver reactivation, catalysts, amplification/decay, controller ownership and handbook behavior. The combined run also covers Foci and directional reception. No candidate selection, RF equation, cache policy, invalidation policy, frame deduplication or routing cadence changed in this release. The two entrance passes are retained.

The initial capture fixture used one tower and incorrectly expected a path-cache hit: a one-node graph can prune its search entirely. The final fixture uses an output-only peer that needs a translated route, exercising actual path lookup and reuse. No production algorithm change was required.

`prototype-verification.json` checks both distribution jars against their full builds: all 812 retained methods, resources, class names and debug information match. Automated GameTests, their entrypoints and unused test helper methods are removed. Both archives pass ZIP integrity checks and target Java 17; Foci depends on core 0.5.5 exactly.

## Reproduce

From the project root with JDK 21:

```sh
./gradlew -I validation/0.5.5/server-check.gradle -PwithFoci build :core:prototypeJar :foci:prototypeJar :core:runGameTest :directional:runGameTest --continue
python3 tools/verify_prototypes.py
```

The QA-only selector runs addon tests and excludes the native mod's separate suite, whose earlier headless failures are documented in previous validation folders. It is outside all release source sets. `results.json`, server XML, unit XML, the build log and packaging reports retain the final evidence. `changed-source-files.json` lists source changes relative to 0.5.4.
