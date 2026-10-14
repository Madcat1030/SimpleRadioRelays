# Simple Radio Repeaters 0.2.4

A Fabric Minecraft 1.20.1 addon for **Simple Radio 1.20.1-4.5.7.9**. A catalyst-powered Radio Relay connects to the antenna core, and a separate six-card Repeater Bank selects its channels. Both blocks are operated in the world, with live antenna/status/link text and visible card frequency labels.

**0.2.4 retains existing 0.2.0 through 0.2.3 towers and cards.** The 0.2.x hardware starts fresh from 0.1.x. Old combined 0.1.x banks, their inventories and saved network records are not migrated. Rebuild towers with the new Relay and Bank. Existing Frequency Card items keep their tuning. The Relay has no container or inventory screen; gilded blackstone is a directly inserted component.

## Install

Put `simple-radio-repeaters-fabric-1.20.1-0.2.4.jar` in the server and client `mods` folders, replacing the previous addon JAR. Requirements:

- Minecraft 1.20.1, Java 17+, Fabric Loader 0.19.0+ and Fabric API.
- Simple Radio **1.20.1-4.5.7.9**, exactly, and its Lexiconfig dependency.
- Simple Voice Chat 1.20.1-2.6.0+.

The development environment uses Fabric API 0.92.5+1.20.1, Lexiconfig 1.4.21-epic and Voice Chat 2.6.18. No additional chunk loader, voice-chat port or resource pack is required.

## Crafting recipes

Each shaped crafting recipe produces one item. Copper wire means Simple Radio's `simpleradio:copper_wire` item.

| Item | Ingredients |
| --- | --- |
| Radio Relay | 4 iron ingots, 1 copper ingot, 1 copper wire, 1 redstone dust, 1 Simple Radio receiver, 1 Simple Radio transmitter |
| Repeater Bank | 4 iron ingots, 1 copper ingot, 1 copper wire, 2 glass blocks, 1 redstone dust |
| Frequency Card | 1 paper, 2 copper ingots, 1 copper wire, 1 redstone dust |

In 0.2.3, wire replaces the Relay's top-center copper ingot, the Bank's bottom-center copper ingot and the Card's bottom-center paper. The [gameplay guide](docs/Simple-Radio-Repeaters-Documentation.md#crafting-recipes) shows the exact layouts. Recipe JSON files are under `src/main/resources/data/simpleradio_repeaters/recipes/`.

## Build a tower

1. Build the normal antenna on its core, including a native copper-wire extension if needed.
2. Place a Radio Relay against a horizontal side of the core, facing outward.
3. Place a Repeater Bank directly above it. The Bank copies the Relay's facing and plugs into its top ports.
4. Tune Frequency Cards in the native radiosmithing station and right-click the Bank's front slots to insert them.
5. Right-click the Relay with gilded blackstone. Wait for a positive `ANT` score and `READY`.

The Bank has two columns of three slots. Right-click a front slot with a card to insert it into that slot; an occupied slot leaves the held card alone. A card click elsewhere on the front fills the first available slot, starting at the top left and proceeding row by row. Card clicks on the other faces do nothing. With an empty hand, only clicking an occupied card on the front removes it. Other empty-hand clicks do nothing. On the Relay, an empty-hand click removes the catalyst; sneak + empty-hand toggles power. Unsupported items and no-op clicks do not restart antenna verification.

Version 0.2.4 considers direct and repeated routes within one interference point of the cleanest available delivery, then chooses the strongest remaining arrival carrier. This prevents a slightly cleaner but nearly exhausted B-to-receiver carrier from beating a regenerated local C exit when the receiver is on the near side of the antenna core and the Relay is on the far side. The tolerance is measured against the cleanest candidate, so successive near-ties cannot widen it. Each selected packet keeps its actual worst interference; RF regeneration does not clean it up.

The Relay uses one adjacent Bank, preferably above it. A Bank belongs to one Relay. Side and bottom adjacency work electrically, but the model's connector pins are designed for stacking. Duplicate cards select a channel once. AM and FM remain distinct.

## Radio behavior

A handheld must reach an entry Relay using its own transmitter. Every Relay departure uses that tower's full RF power, while audio retains the worst interference along the selected path. The final wireless leg can degrade it further. Hearing a strong tower broadcast does not guarantee that a distant handheld can reply.

Verified towers continue relaying from saved records while unloaded. Routing, attachment and reconciliation do not force missing chunks to load. Wired-core reloads retain their last verified antenna while native insulators and wires restore. Missing or replaced Relays and Banks are reconciled when their chunks become available, preventing persistent ghost connections.

Loaded Relays also recheck antennas on the native 200-tick world-time boundary, about ten seconds at normal tick rate. Missed dirty notifications no longer leave the score stale. Bursts of build notifications share a settling window rather than restarting it every tick. Reload `CHECK` retains the verified network record.

`ANT` is native antenna strength. `LINK` counts distinct outgoing tower neighbors across the selected channels, not players or all towers in a chain. The screen also shows pending verification, missing components and disabled status.

## Documentation and editable models

- [Gameplay and implementation guide](docs/Simple-Radio-Repeaters-Documentation.md): setup, controls, RF behavior, persistence, configuration and code reference.
- [Model editing guide](art/Model-Editing-Guide.md): Blockbench groups, exact coordinates, partial exports and Java text placement.
- `art/blockbench/Relay.bbmodel` and `Repeater-Bank.bbmodel`: editable Java Block/Item projects with embedded 16×16 textures.
- `art/Repeater-Bank-0.2.0-Native-Preview.png`: actual Minecraft model/font rendering, including empty and occupied states.

The master models show inserted components in separate groups. Those components are excluded from the base runtime models and rendered only when present. `art/make_assets.py` reproduces the original supplied assets; do not rerun it over hand edits.

## Commands and configuration

`/radiorepeaters status` shows counts and packet counters. `/radiorepeaters list` and `/radiorepeaters refresh` require operator level 2. Refresh checks saved component positions in loaded chunks and rechecks loaded antennas.

The server config is `config/simpleradio-repeaters.json`, with defaults `maxRelayHops: 64`, `persistentUnloadedTowers: true` and `requireCatalyst: true`. Restart after edits. The new saved network is `data/simpleradio_repeaters_relays.dat` in the overworld; the 0.1.x network store is ignored.

## Build and verification

Use JDK 21 for Gradle/Loom; output bytecode targets Java 17.

```sh
./gradlew build
./gradlew runGameTest
./gradlew runBaseGameTest
```

All 21 unit tests and 25 addon Minecraft tests passed. The exact results and XML reports are under `validation/0.2.4/` and `validation/build-verification.json`. The opposite-side receiver/Relay bridge was reproduced on 0.2.3 before the fix; the corrected run verifies packet return while retaining the actual noisier path. Selection tests also cover the inclusive one-point boundary, rejection beyond it, iteration-order independence and prevention of chained near-ties. Tests cover routing, native antenna/wire restoration, split ownership, real card/catalyst interactions, no-op clicks, component identities, delayed entity data and stale Relay caches. The unchanged 0.2.0 artwork was reviewed in the native client in all four facing states. The 0.2.1 regressions exercise a real receiver/copper-wire/transmitter bridge, a weak-carrier return failure, packet return while the Relay is checking after reload, missed mast notifications and repeated build notifications. The 0.2.2 interaction checks insert into all six slots out of order in every facing, preserve occupied targets and creative-held cards, and reject side/back/top/bottom and empty-hand background clicks.

Four native Simple Radio tests failed in the 0.2.1 addon-disabled baseline in this headless environment: `shortCircuit`, `garbageCollection`, `openSystem` and `closedSystem`. The same four baseline failures occur in the 0.2.4 combined GameTest run, so that task exits nonzero; addon regressions are reported separately. Their underlying cause has not been established, and these are not confirmed live-server bugs. Multiplayer voice playback and shader/resource-pack combinations need in-game checks.

The 0.1.3 PDF under `docs/history/` is historical documentation. Use the current Markdown guide for 0.2.4. Licensed GPL-3.0-only; dependencies are distributed separately by their authors.
