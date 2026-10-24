# 0.5.8 wire and reception-cache verification

Core/Foci 0.5.8; Directional 0.2.9. Java 17 target, JDK 21 test runtime, Gradle 9.5.0, Loom 1.17.21, Fabric Minecraft 1.20.1. The build pins Simple Radio `f9TIjf7X` / `1.20.1-4.5.7.9`.

| Configuration | Tests | Failures |
| --- | ---: | ---: |
| Core JUnit | 55 | 0 |
| Foci JUnit | 2 | 0 |
| Directional JUnit | 17 | 0 |
| Core server | 63 | 0 |
| Standalone Directional server | 21 | 0 |
| All three addons, server | 109 | 0 |

The accompanying XML, unit/server summaries and prototype verification contain the results. `release-build.log` records the final successful build. The combined server run passed all 109 cases; the build attempted afterward in the same offline invocation lacked cached JUnit resolution metadata. The subsequent online release build completed successfully. This was a host dependency-cache issue, not a failed server test.

## Regression coverage

The wired-core fixture uses native insulators and a real Wire entity across a chunk boundary. It checks restoration of socket lists, preservation of the remote antenna origin, normal wire unloading, immediate deliberate-cut invalidation, saved-data round trips, immediate reconnection and cutting the remote extension after its relay box has unloaded. It also removes an endpoint router while a historical dependency is unavailable and ticks the real wire beyond its native expiry interval; the wire and connection must survive without loading chunks. With dependencies available, native expiry must still work.

The Directional fixture checks native receiver and optional relay mast bindings through a temporarily empty wire table, the real Wire tick reconnecting both endpoints, normal unloading and immediate loss of reception on a deliberate cut. It runs with core/Foci installed and with Directional alone. Existing unchanged-scan, native receiver lifecycle, routing, transponder, dimensional and native-math parity tests remain in the suites.

The tests exercise server behavior, not live microphone playback. No new graphical capture was needed for these changes. Test classes remain excluded from distribution JARs; prototype copies must be byte-identical to ordinary release JARs. The temporary execution host uses the documented host-only socket-probe adapter, which is not a mod dependency or a distributed file.

## Supplied captures

| Capture suffix | Duration | Routed frames | Accepted deliveries | Distinct eligible receiver IDs | Average routing self time |
| --- | ---: | ---: | ---: | ---: | ---: |
| 427fb26c | 10.04 s | 157 | 157 | 1 | 224.3 µs |
| 99463e83 | 10.02 s | 147 | 147 | 2 | 213.0 µs |
| 30293404 | 5.01 s | 90 | 0 | 0 | 52.8 µs |

All three captures reuse their network and path caches. Each includes one unchanged positive antenna scan, no graph builds or invalidations, no relay loads/unloads, and no routing exceptions. They show no repeated ten-second graph rebuild in steady state. These small networks do not justify adding a delivery-plan cache yet; the short active-speech samples are not a large-network capacity benchmark.

The first capture has four reachable route options per frame and successfully accepts every attempted delivery. It does not include the travel transition where connectivity reportedly changed.

The second capture has six reachable options and three output channels per frame. Two distinct receiver UUIDs are tracked; duplicate-receiver skips and receiver-object replacements are zero. Only one eligible receiver has a viable RF candidate per frame. This does not support a shared-UUID collision between the two observed receivers, although the profiler does not list individual item NBT or receiver/channel mappings.

The third capture has only one reachable option and one output channel per frame: the starting bank on the incoming channel. No return-channel receiver reaches candidate evaluation and no delivery is attempted. The failure precedes radio playback or return-channel deduplication. The RF configuration stamp rises by 31 between the second and third captures while all reported RF parameters and tower settings stay the same. In this addon configuration that is consistent with one Directional relay-reception revision. The captures do not identify the changed dish/binding or contain the transition itself.

There is also a material hardware difference between the first and second captures. The far transponder changes from an Ender Amplifier with effective antenna score 112 to a Redstone Amplifier with effective score 96. The near bank stays at effective score 96. Assuming full parabolic gain, AM and the logged defaults, the latter setup has:

- First-relay nominal departure: `(4400 + 96 × 10) × 0.9 ≈ 4824`.
- Near-tower directional reception allowance: `4824 × 0.75 ≈ 3618`.
- Far-tower return departure after one prior relay: `4824 × 0.9 ≈ 4341.6`.
- Direct return reach: `4341.6 + 3618 ≈ 7959.6`, short of the approximately 7999-block leg.

Only channel 0.03 has the 4000-block intermediary in the logs. That shorter route can return when channel 0.02 cannot. Restoring the far Ender Amplifier both raises its antenna contribution and resets the decay; its return departure becomes approximately 4968, giving approximately 8586 blocks of reach to the near tower under the same full-beam assumption. No change to channel translation or RF math is needed to explain the selective return.

The separate reproduced Directional restore bug can remove the reception allowance altogether, preventing the outbound leg from reaching the transponder. The final capture is consistent with that failure, but cannot establish it as the unique cause.
