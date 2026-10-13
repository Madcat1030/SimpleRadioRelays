# Simple Radio Repeaters 0.1.1: handheld robustness audit

Date: 2026-10-13. Target: Minecraft 1.20.1, Simple Radio 1.20.1-4.5.7.9, Simple Radio Repeaters 0.1.1.

No addon fault reproducing the reported handheld stall was found. The installed 0.1.1 production build was left unchanged. This archive contains diagnostic sources and the actual server test reports; it is not a mod update to install.

## Confirmed addon behavior

All nine radio/routing/mixin source files are byte-for-byte unchanged from the supplied 0.1.0 source. The 0.1.1 wired-antenna fix changes antenna verification and saved tower handling; it does not alter handheld item tuning, registration, push-to-talk, or playback.

The duplicate filter belongs to each source packet and is shared by its copies. It does not keep a global recent-audio blacklist or use encoded audio bytes as identity. Repeated identical payloads in new packets continue to arrive.

Three additional Minecraft GameTests passed:

| Test | What was exercised | Result |
| --- | --- | --- |
| Handheld lifecycle | Real native walkie-talkie and transceiver items, native listener and RF routers, 1,600 fresh packets in both directions across a 9,000-block saved tower chain. Four phases: initial activation, native garbage collection, ownership transfer, and item serialization with owner recreation simulating reconnect. Native push-to-talk and walkie half duplex were checked. | Every packet reached its intended handheld output exactly once. Tower chunks remained unloaded. |
| Tuning isolation | One native walkie was retuned and reactivated through the native bench menu 20 times while a separate walkie received 40 fresh packets from a transceiver. This included a native collection pass while the tuned item was in the bench. | Retuning did not interrupt the independent receiver. The retuned receiver resumed immediately. |
| Two-channel return path | Native transmitter -> saved repeater chain -> native receiver -> modeled native copper-wire loss -> native transmitter on the second channel -> repeater chain -> three receivers near the origin. Return recipients were a tower receiver, a bare radio receiver, and a real owner-backed walkie receiver. 100 input packets. | All three return recipients received all 100 packets independently, exactly once. |

The six existing addon GameTests also passed, including the wired-core reload race and preservation of cached antenna power while dependencies are unavailable.

## Confirmed upstream tuning quirk

A separate probe contains no addon imports or routing calls. It was run both with the addon enabled and with `simpleradio_repeaters` disabled. Both runs reproduced this sequence:

1. A walkie is activated on `003.30`.
2. It is moved to the native tuning bench, changed to `003.31`, and returned before the next native garbage-collection pass.
3. The item tag displays `003.31`, but the live receiver remains valid on `003.30`.
4. Subsequent native item activation and garbage collection, with the item back in inventory, leave the mismatch intact.
5. Removing the item from inventory across a collection pass lets the native routers be rebuilt on `003.31` when it returns.

The native `Frequencing.setFrequency(ItemStack, ...)` method only writes the item tag. Native `TransceiverItem.entityTick` returns early when a router with the same radio UUID and owner already exists; it does not compare that router's live channel against the item tag. This behavior exists without the repeater addon.

The probe is named `quick_bench_retune_leaves_native_registration_on_old_channel`. Its passing result means it successfully demonstrated this existing quirk, not that native tuning is bug-free.

A bench visit can therefore recreate a radio's runtime state. This does **not** establish the cause of the user's incident, particularly the observation that retuning one item appeared to revive multiple devices. No logs or state snapshot from that failure were available, and this probe does not reproduce a spontaneous whole-network stall.

## Test reports and limits

`gametest-controls.xml`: nine addon/handheld routing tests passed, the native tuning probe reproduced the quirk, and three native Simple Radio tests passed. Four native tests failed.

`baseline-controls.xml`: the same tuning probe reproduced the quirk with the addon disabled; three native tests passed and the same four native test names failed: `SystemTest.closedSystem`, `SystemTest.garbageCollection`, `SystemTest.openSystem`, and `SystemTest.shortCircuit`. These native failures were also present in the earlier pre-audit controls. Both Gradle game-test tasks consequently exited with failure despite the addon tests passing.

The handheld audit used synthetic opaque packet bytes and observed the output of native receivers. It did not connect graphical clients, capture microphones, decode those bytes as Opus, or verify audible client playback. The reconnect test recreates the native item/owner registration state; it is not a full client login or server restart. The return-path routing test models copper-wire power loss; the separate existing wired-core test uses a real native wire entity for the antenna lifecycle regression.

Distributed 0.1.1 jar, unchanged SHA-256:
`4c8cf28a2c31eb36e9fe4b8893f6b68d012c49bac976dbd4741b9ef2bd705f76`

## Reproduce

Extract this audit archive separately from the 0.1.1 source project. Use the source project's supported build toolchain and JDK 21 for its Gradle/Loom build; compiled mod classes remain Java 17 compatible. With the source project as the working directory:

```sh
python3 /absolute/path/to/handheld-audit/run_audit.py
```

The runner adds the audit sources through a Gradle initialization script, builds a native-only test probe, runs the enabled-addon and disabled-addon controls, and retains XML reports in `results/`. It then removes its temporary test jars and restores ordinary compilation/resources. No production source or addon metadata file is edited. Run this against a development test environment, not a live server. The runner's command sequence is the one used for this audit; its Python syntax and portable source overlay were checked separately.
