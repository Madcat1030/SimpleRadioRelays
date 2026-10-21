# Simple Radio Repeaters 0.5.2

Fabric Minecraft 1.20.1 · Simple Radio 1.20.1-4.5.7.9 · Java 17+

## Active receivers losing their frequency after travel

The supplied broken-state log identifies a different lifecycle failure from the activation window addressed in 0.5.1. After teleporting away and back, the ground radio remains active and valid, but its `canonical` and `subscribed` flags are false. The server reports `000.02 AM registered=false`. Cached and independently rebuilt relay routes agree and can reach the radio. Replacing a radio creates a new registered channel and temporarily restores delivery.

The pinned Simple Radio version's `FrequencyChannel.removeReceiver(Predicate)` and `removeTransmitter(Predicate)` always remove an empty channel from `RadioManager.getInstance().frequencies()`, the server registry. Client channels use these same methods when chunks unload. In an integrated server, removing the last client endpoint can therefore delete the server's channel with the same frequency number, even while its server receivers remain active. Spawn chunks make this particularly visible: those server devices never unload, so they never reactivate. A relay refresh cannot restore their native subscription.

Core now remembers the registry that created each native frequency channel. Its composable `FrequencyChannelLifecycleMixin` wraps the existing empty-channel removal and performs it only in that owning registry. It also checks object identity so delayed cleanup of an old channel cannot delete its replacement. Ordinary endpoint removal, real empty-channel collection and native delivery continue normally. The 0.5.1 activation repair remains for channels evicted between deserialization and activation.

This corrects a native Simple Radio cleanup defect through the addon; no changes to the base mod JAR are required. It adds no packet polling, audio rescans, forced chunk loads or periodic endpoint re-registration.

## CHECK while speaking near a tower

Native microphones and speakers send neighbor notifications while polling their audio activity for redstone output. The relay previously interpreted those notifications as antenna hardware changes and repeatedly displayed `CHECK`. A verified relay remained eligible throughout these checks, so this display behavior is separate from the missing-channel failure.

The relay now ignores activity notifications from a still-present microphone or speaker beside it. Actual core changes, device removal/replacement, native antenna notifications and periodic antenna verification remain enabled. `CHECK` can still appear for legitimate verification and continues to preserve an eligible saved node.

## Install and verify

Replace the previous core addon with `simple-radio-repeaters-fabric-1.20.1-0.5.2-prototype.jar` and restart the world/server once. Update clients too for multiplayer. Existing 0.5.x worlds, towers and receiving blocks can stay in place.

At RT1, confirm the existing transceiver → RT1 → RT2 → RT3 Transponder → RT2 → RT1 route reaches the radio and wired receiver. Teleport away and return several times without replacing or retuning those devices. Both should continue receiving. Speaking near the tower should no longer cause continuous activity-driven `CHECK` states.

If reception fails, hold the transmitting handheld and run `/radiorepeaters diagnose` before changing blocks or leaving the world, then provide that session's `logs/latest.log`. The command remains read-only and compares route reachability separately from native receiver registration.

The optional Foci build is rebuilt with a matching 0.5.2 core dependency; its gameplay source is unchanged. It is not required for the current core-only setup. Directional Antenna remains 0.1.1.

## Verification and limits

- Before the registry fix, both new native cleanup regressions delete a live server channel and fail. After the fix, a placed radio and native wired receiver continue receiving translated packets through three repeated unload cycles, separately exercising client receiver cleanup and client transmitter cleanup. A separate native registry represents the client; these cases do not launch a graphical client.
- Old-generation cleanup cannot delete the replacement channel. Removing its last real server endpoint still cleans up that channel. Native direct FM delivery remains intact.
- Before the activity filter, the native microphone notification regression reproduces the unwanted `CHECK`. Afterward it preserves `READY`, eligibility and the verified snapshot; removing the actual core still triggers verification.
- All 51 unit tests pass: 49 core and two Foci. All 50 core addon Minecraft cases and all 73 combined core/Foci addon cases pass, including the existing physical Transponder, saved 4,000-block AM return route, delayed activation and read-only diagnostic checks.
- The exact core prototype passes production Fabric startup without Foci under `development=false` and `-Xverify:all`, activates native radio/receiver/transmitter devices, verifies a Relay/Bank and shuts down cleanly.
- Packaging checks compare retained methods, debug information, fields, resources and mixin configuration against the full builds. The runtime JAR excludes addon GameTests and their entrypoints; no addon obfuscator is used.

The four previously recorded native headless failures remain in both suites: `SystemTest.shortCircuit`, `SystemTest.garbageCollection`, `SystemTest.openSystem` and `SystemTest.closedSystem`. They cause the aggregate GameTest tasks to return nonzero; the reports separate them from the passing addon cases. This patch does not claim to fix those tests. Live microphone playback and the user's graphical teleport sequence still require in-game confirmation.

Reproduction logs, XML test reports, packaging verification and the production smoke result are under `validation/0.5.2/` in the source archive. Attached user logs are not redistributed.
