# 0.5.7 maintenance verification

Core/Foci 0.5.7; Directional 0.2.8. JDK 21 / Java 17 target, Gradle 9.5.0, Loom 1.17.21, Fabric Minecraft 1.20.1. Tests use the pinned Simple Radio binary (`f9TIjf7X`), SHA-256 `667528f774ba38426ad8bcbea5db260ec0841316b38e56302cb47037445066e8`.

| Configuration | Tests | Failures |
| --- | ---: | ---: |
| Core JUnit | 55 | 0 |
| Foci JUnit | 2 | 0 |
| Directional JUnit | 17 | 0 |
| Core Minecraft tests | 63 | 0 |
| Standalone Directional Minecraft tests | 20 | 0 |
| All three addons, Minecraft tests | 108 | 0 |

`unit-summary.json`, `server-summary.json` and the accompanying XML files contain the results. The standalone test loader lists Directional and its fixture mod without core or Foci. Its optional-relay fixture exits without invoking the integration. The combined fixture exercises the core ticker API, actual native/relay reception, unchanged-scan cache reuse, native receiver unload/reload without graph invalidation, and immediate invalidation and changed reach after aiming/removal.

`build.log` records the initial successful build. `release-build.log` records the final packaged build after stable dish ordering was added. The standalone host log ends early despite a completed 20-case XML report; the report is the authoritative result. Core and combined logs include full test completion. No live microphone playback or new graphical client capture was performed.

The server suite selects addon tests through `tools/gametest.gradle`. It does not include Simple Radio's independent tests. Existing antenna-settling fixtures retain their isolated batches and assertions.

Packaging verification requires each prototype to be byte-identical to its normal release JAR, all classes to target Java 17, and addon tests to be absent from both binary and sources JARs. Directional has no required Repeaters/Foci dependency and no mixins into Repeaters. Core contains no Foci or Directional implementations.

The routing algorithm and TowerManager logic were checked against 0.5.6: Java tokens match after removing comments, whitespace and imports. Only formatting and unused imports changed in those two files. Radio power formulas, angular curves, recipes, block models and textures remain unchanged. No delivery-plan cache or packet-rate change was introduced.

The execution host requires a host-only Java agent to disable unsupported Unix-socket probes. It is not a project dependency or part of any delivered mod. The existing host setup notes are under `directional/validation/0.2.3/host-setup/`; normal desktop builds do not need the agent.
