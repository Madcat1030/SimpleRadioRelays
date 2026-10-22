# 0.5.4 validation

| Run | Passed | Failed |
| --- | ---: | ---: |
| Core unit tests | 51 | 0 |
| Foci unit tests | 2 | 0 |
| Directional unit tests | 18 | 0 |
| Core-only Minecraft addon tests | 57 | 0 |
| Core + Foci + Directional Minecraft addon tests | 102 | 0 |

Environment: Fabric Minecraft 1.20.1, Loader 0.19.3, Fabric API 0.92.5, Simple Radio 1.20.1-4.5.7.9, Voice Chat 2.6.18, JDK 21 (release bytecode targets Java 17). Core/Foci are 0.5.4; Directional remains 0.2.6.

Native dependency: `maven.modrinth:simple-radio:f9TIjf7X`, SHA-256 `667528f774ba38426ad8bcbea5db260ec0841316b38e56302cb47037445066e8`. A version constraint alone cannot detect a different upstream jar published under the same version. These results identify the actual tested binary.

## Native parity

`NativeMathGameTests` uses real native transmitters, receivers and sources. Each link compares native `shouldRouteTo`, then `prepareSource`/`travel` and `computeSeverity`, against the numeric adapter. It checks endpoint and tower reception, plus native tower departure power for the amplifier tiers. No oracle objects were added to speculative gameplay routing.

The matrix covers AM/FM, transmitter/transceiver/walkie sender types, full and weak incoming power, all standard dimensions, and coordinates near ±1,000,000. Additional cases exercise both loss methods, distance at/below/above the reception floor, and native dimensional ON/OFF. Core-only cross-world probes demonstrate that reach can pass while dimensional travel exhausts the carrier. Foci deliberately blocks native physical cross-world links, so that particular oracle test runs only without Foci; expansion regressions cover the replacement policy.

Range probes sit inside and outside the reach boundary with at least half a physical block of margin, enlarged where coordinate float ULPs require it. Power and severity comparisons budget native float rounding. Exact range-edge acceptance is not treated as a precision-independent invariant.

With Foci installed, `ExpansionGameTests.native_Nether_noise_floor_matches_the_adapter_with_foci_hooks_active` compares the actual native Nether distance/noise hooks with the adapter, both with Nether tweaks enabled and disabled, including distant coordinates and out-of-range probes.

## Antenna origin and regressions

The hardware tests verify directly mounted and copper-wire-connected antenna cores. The wired reload test checks that an incomplete restoration retains the remote core, then recovers it without toggling the relay. Persistence, settings changes and invalidation retain the verified origin. Separate saved-tower tests prove that entrances, relay links, emitted carriers and endpoint reach use antenna bases even when relay boxes are far apart. Foci tests check mapped antenna coordinates and unchanged Nether/End height rules. These radio probes assert that their terrain remains unloaded.

Both server runs also retain the existing addon regressions for module ownership, removal reconciliation, native radio reload/cleanup, transponders, catalyst/decay behavior, antenna maintenance and the handbook. The combined run includes directional reception and cache invalidation.

The initial runs passed all new cases but failed an older bridge assertion that still measured expected interference to the relay box. Its reference now uses `antennaBase()`, matching the new behavior. `initial-*.xml` and `initial-build-and-tests.log` preserve that result; the final reports above pass completely. No gameplay math change was needed.

## Reproduce

From the project root with JDK 21:

```sh
./gradlew -I validation/0.5.4/server-check.gradle -PwithFoci build :core:prototypeJar :foci:prototypeJar :core:runGameTest :directional:runGameTest --continue
python3 tools/verify_prototypes.py
```

The QA-only selector runs addon tests and excludes the native mod's separate test suite, whose previously recorded headless failures are documented in earlier validation folders. It is outside all release source sets. Synthetic packets exercise routing and native RF calculations; these checks do not record microphone playback.

`results.json`, the final XML reports, unit XML files and `prototype-verification.json` provide the detailed evidence. Both prototype jars exclude GameTests and test entrypoints. All 705 retained gameplay methods, resources and debug information match their corresponding full builds; no obfuscation is applied.
