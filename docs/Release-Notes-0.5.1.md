# Simple Radio Repeaters 0.5.1

Fabric Minecraft 1.20.1 · Simple Radio 1.20.1-4.5.7.9 · Java 17+

## Receiver reload fix

This patch fixes a reproducible native frequency-registration failure after a radio or wired station reloads. Relay links can remain ready while the receiving endpoint is silent. Breaking and replacing the endpoint restores it; refreshing relay antennas does not.

Simple Radio creates or looks up a frequency object while reading a block entity's saved data. The block may not start ticking immediately. Its empty frequency can be garbage-collected in that interval. When it later activates, native code registers its receiver or transmitter on the old object, which is no longer the frequency in the server registry. Transponder output looks up the live registry and therefore cannot find that receiver. A carried radio on another channel can continue working because its own registered routers keep that channel alive.

Core now resolves the live frequency, preserving its number and AM/FM mode, immediately before native activation creates the routers. The hook covers placed radios, wired receivers and wired transmitters. It handles both a missing channel and a newer registered object for the same channel. Corrections produce a `[radio-reload]` log line.

The controlled regression failed before the fix with `channelRegistered=false, receiverRegistered=false`, then passed with the fix. The user's teleport-away-and-back and break-and-replace observations match this failure. A live microphone and graphical client were not available for automated validation, so the existing-world travel test remains the final confirmation.

Relay range, catalyst efficiency, amplifier gain, decay, card behavior, saved tower format and dimensional policy are unchanged. Translated packets still use `get()` and skip channels without native listeners; the repair creates a frequency only for a device that is actually activating.

## Install and test

Replace the previous core addon JAR with `simple-radio-repeaters-fabric-1.20.1-0.5.1-prototype.jar`, then restart the world/server once. For a multiplayer installation, update clients as well. Existing 0.5.0 worlds and installed blocks can be used directly. The runtime JAR excludes addon GameTests and retains readable names and debug information.

For the current core-only test, keep the existing tower layout and devices:

1. At RT1, confirm the 0.01 → RT1 → RT2 → RT3 Transponder → RT2 → RT1 → 0.02 route works.
2. Teleport away and return using the same procedure that previously broke the radio and wired receiver. Leave those receiving blocks in place.
3. Repeat the trip and verify both endpoints continue receiving without replacement, card changes or a relay refresh.

If a failure remains, hold the transmitting walkie-talkie/transceiver in the main hand at RT1 and run:

```text
/radiorepeaters diagnose
```

Run it while working as well if convenient, so the same `logs/latest.log` contains both states. On failure, run it before replacing blocks, refreshing, or leaving the world. Send that session's `logs/latest.log` and identify approximately when the successful and failed voice tests occurred. No world files are required initially.

## Diagnostics

The new operator command writes `[relay-diagnostic]` lines to `logs/latest.log`:

- Installed radio addon/base versions and relevant RF/configuration values.
- Saved tower settings, antenna scores, catalyst strength, amplifier identity/reset behavior and ordered Transponder ports.
- Already loaded Relay/Bank/Transponder data for comparison with the saved records.
- Native receiver/transmitter activity, validity and membership of the current live frequency.
- Cached and independently rebuilt RF route probes, including reachable relay ports and possible receiver exits.

The command is read-only: it emits no voice, loads no missing chunks, changes no inventory or radio registration and does not install its fresh graph. RF candidates are distinct from actual accepted deliveries. Diagnostics run only when requested, with no new per-packet tracing or configuration checks.

## Verification

- 49 core unit tests pass; 46 core addon Minecraft tests pass.
- The combined core/Foci installation passes all 69 addon Minecraft tests; the expansion's two unit tests pass.
- Regression coverage includes a frequency evicted before radio activation, a replacement frequency generation for all three native station types, and read-only diagnostics preserving routes, endpoints and unloaded chunks.
- Local physical Transponder-to-radio delivery and the 4,000-block saved AM return route with Ender amplifiers continue to pass.
- The exact core prototype starts in a production Fabric server without Foci under `development=false` and `-Xverify:all`. The test activates a native radio, wired receiver and wired transmitter and verifies a ready Relay/Bank before a clean shutdown.
- Packaging verification checks retained methods/debug data and resources against the full builds. Core retains 642 methods in 75 classes; the expansion retains 33 methods in seven classes. No addon obfuscator is used.

Four previously recorded native headless tests still fail: `SystemTest.shortCircuit`, `SystemTest.garbageCollection`, `SystemTest.openSystem` and `SystemTest.closedSystem`. Full GameTest commands return nonzero because of those cases; the supplied reports preserve them separately from the passing addon cases. Their causes are not attributed to this reload fix. No real microphone playback was tested.

Evidence is under `validation/0.5.1/`. The optional Foci source is rebuilt with a matching 0.5.1 core dependency; its gameplay source is unchanged. The separate Directional Antenna module remains at 0.1.1.
