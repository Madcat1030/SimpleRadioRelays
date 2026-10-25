# Repeaters 0.7.1 validation

96 unit tests passed (77 core, 2 Foci, 17 Directional). The native Minecraft server suite passed 83 core tests and 129 combined core/Foci/Directional tests. XML reports and logs are retained here.

New regressions exercise an actual read-only multi-release ZIP filesystem, not permission bits that root could bypass; valid `mode: off` and custom settings take effect despite normalization failure. A writable/malformed-file test checks successful normalization and failed reload preservation. Existing command tests still reject corrupt JSON.

The server fixtures fill the real 16,384-device retention limit and compare observed/native/relay ingress and egress fallback without creating further work. Another fixture fills the 128-job relay queue, marks an unqueued viable path pending, builds another channel, moves the target out of range, and verifies the stale marker is cleared while queued jobs survive. Both new fixtures ran in the core and combined suites.

`core-payload-diff.json` limits runtime class changes to RepeatersConfig, TowerManager and TerrainManager families. Routing, RF math, monitor classes/models and other core payloads are unchanged apart from the handbook text and version metadata. Foci differs from 0.7.0 only in fabric.mod.json. Directional 0.2.9 has its previous SHA-256, dd686cb9668b49b29575de8faa231a585539517fb1fe3f49941afbf09093cbef.

Production/prototype archives are identical unmodified runtime copies; the verification report checks Java 17 bytecode, module boundaries and test exclusion. Build tooling uses JDK 21, Gradle 9.5.0 and Loom 1.17.21. The capture host needs a host-only unsupported-Unix-socket fallback; no host agent is distributed.

No live microphone session or new visual capture was performed for this logic/documentation patch. Existing 0.7.0 native monitor captures remain under validation/0.7.0. The terrain curve, 200-tick publication cadence, queue cap and 0.5.x tip compatibility branch are unchanged.
