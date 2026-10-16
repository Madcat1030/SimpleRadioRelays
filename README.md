# Simple Radio Repeaters 0.3.0

A Fabric Minecraft 1.20.1 addon for **Simple Radio 1.20.1-4.5.7.9**. A Radio Relay attaches to an antenna core, and a six-card Repeater Bank plugs into its top. Relays carry voice through a persistent network while retaining the worst audio degradation along the chosen path.

Version 0.3.0 adds native catalyst support and relay-count power loss. Gilded blackstone is 90% efficient; an amethyst block is 60% efficient under Simple Radio's default data. The first relay applies its catalyst efficiency immediately.

## Install and existing worlds

Replace the addon JAR on both server and clients with `simple-radio-repeaters-fabric-1.20.1-0.3.0.jar`. Requirements:

- Minecraft 1.20.1, Java 17+, Fabric Loader 0.19.0+ and Fabric API.
- Simple Radio **1.20.1-4.5.7.9**, exactly, and its Lexiconfig dependency.
- Simple Voice Chat 1.20.1-2.6.0+.

The development environment uses Fabric API 0.92.5+1.20.1, Lexiconfig 1.4.21-epic and Voice Chat 2.6.18.

**0.3.0 starts a fresh saved network. Load each existing tower's chunk once so it can register and verify in the new network.** Earlier network stores are not imported. The existing Relay and Bank blocks already store their catalyst and cards directly; no inventory migration is added. Banks must be directly above their Relays.

The new store is the overworld's `data/simpleradio_repeaters_relays_v3.dat`. Once a tower is verified in 0.3.0, it can relay while unloaded under the normal persistence setting. No additional chunk loader or voice-chat port is required.

## Build a tower

1. Build a normal antenna on its core, including a native copper-wire extension if needed.
2. Place a Radio Relay against a horizontal side of the core, facing outward.
3. Place a Repeater Bank directly above the Relay. It copies the Relay's facing and plugs into the top sockets.
4. Tune Frequency Cards in the native radiosmithing station and insert them into the Bank's front slots.
5. Right-click the Relay with gilded blackstone or an amethyst block. Wait for a positive `ANT` score and `READY`.

The server accepts items from Simple Radio's catalyst registry and uses their configured efficiency. A server-supplied catalog keeps client insertion prediction correct on join and after data-pack reload. The installed catalyst uses its actual block/item rendering, including amethyst's purple native texture.

## Power and audio

At each relay:

`departure power = that tower's maximum transmitter power × its catalyst efficiency ^ total relay count`

The first relay is count 1. Counts travel with each source copy through native wires, transmitters and channel changes; a receiver-to-transmitter bridge does not reset them. Both onward relays and local wireless receivers use the same reduced departure power.

| Relay count | Gilded blackstone, 90% | Amethyst block, 60% |
| ---: | ---: | ---: |
| 1 | 90% | 60% |
| 2 | 81% | 36% |
| 3 | 72.9% | 21.6% |
| 10 | 34.87% | 0.6047% |

Each percentage is relative to the current tower's own nominal output. Mixed catalysts follow the same literal rule: gilded → amethyst → gilded produces 90% → 36% → 72.9% of each tower's maximum. Audio damage still cannot improve.

Actual distance, reception power and native attenuation decide whether each leg succeeds. A network can therefore carry a packet partway and fail at a later leg. There is no 1% cutoff: any positive signal that passes normal reception checks can still serve nearby or wired receivers. The configured relay-count budget bounds traversal, including re-entry after a channel bridge.

Audio keeps the worst interference on the selected path. Final delivery considers direct and repeated alternatives within one interference point of the cleanest viable candidate, then chooses the strongest arrival carrier. The selected packet retains its actual degradation. Hearing a tower does not guarantee that a handheld can transmit back to it.

`ANT` remains the native antenna score. `LINK` counts outgoing tower neighbors for a fresh first-relay departure using the installed catalyst; packets arriving later in a route may reach fewer neighbors. It is not an end-to-end delivery guarantee.

## Controls

- Card on a front slot: insert into that slot if empty. Card elsewhere on the front: fill the first available slot. Other faces ignore cards.
- Empty hand on an occupied front card: remove that card. Empty slots and background clicks do nothing.
- Registered catalyst on the Relay: install one if the socket is empty.
- Empty hand on the Relay: return the installed catalyst. Sneak + empty hand: toggle power.

Cards are arranged top left to bottom right, row by row. The three-unit cards retain their centered, single-line labels and one model unit of click padding above only. A Bank uses only the Relay directly below it. Removing that Relay leaves the Bank and cards intact but disconnected. Hoppers retain ordinary card insertion/extraction support.

Equivalent writes, wrong items and no-op card clicks do not restart antenna verification. Wired-core reloads retain verified antenna data while native wires restore. Missing/replaced components are reconciled when their chunks become available. Copied Relays cannot retain distant Bank bindings or overwrite the original tower's settings.

## Crafting

Each shaped recipe produces one item. Copper wire is Simple Radio's `simpleradio:copper_wire`.

| Item | Ingredients |
| --- | --- |
| Radio Relay | 4 iron ingots, 1 copper ingot, 1 copper wire, 1 redstone dust, 1 Simple Radio receiver, 1 Simple Radio transmitter |
| Repeater Bank | 4 iron ingots, 1 copper ingot, 1 copper wire, 2 glass blocks, 1 redstone dust |
| Frequency Card | 1 paper, 2 copper ingots, 1 copper wire, 1 redstone dust |

The [full guide](docs/Simple-Radio-Repeaters-Documentation.md#crafting-recipes) shows the layouts. Recipes are under `src/main/resources/data/simpleradio_repeaters/recipes/`.

## Commands and settings

`/radiorepeaters status` shows tower and packet counts. Operator commands `/radiorepeaters list` and `/radiorepeaters refresh` show tower details and request loaded-component/antenna checks. The list includes catalyst identity and efficiency.

The server config is `config/simpleradio-repeaters.json`: `maxRelayHops: 64`, `persistentUnloadedTowers: true`, `requireCatalyst: true`. The hop setting limits total relay visits, starting at 1 and continuing through wired re-entry. When catalysts are optional, an empty socket uses a neutral factor of 1; an installed catalyst still supplies its native efficiency.

Native power, reception and catalyst changes invalidate cached radio calculations. Successful data-pack reloads also refresh the client catalyst catalog. These updates do not force-load tower chunks or rescan unchanged antenna geometry.

## Source, models and validation

- [Gameplay and implementation guide](docs/Simple-Radio-Repeaters-Documentation.md).
- [Model editing guide](art/Model-Editing-Guide.md), with editable projects under `art/blockbench/`.
- Native catalyst preview: `art/Relay-Catalysts-0.3.0.png`.
- Build and test evidence: `validation/0.3.0/` and `validation/build-verification.json`.

Use JDK 21 for Gradle/Loom; output bytecode targets Java 17. Run `./gradlew build` and `./gradlew runGameTest`.

All 30 unit tests and 34 addon Minecraft tests pass. Tests cover exact catalyst power sequences, sub-1% reception, count-dependent reach, competing hop alternatives, actual native receiver output, catalyst identity/reload changes, copied Relay isolation, real copper-wire channel bridges, antenna restoration and card interactions. Near-tie bridge fixtures use an explicit neutral test catalyst to isolate carrier ranking; the counted-loss bridge uses gilded blackstone and a stronger A mast for its return budget.

The combined GameTest run also contains seven native Simple Radio tests. Four have the same failures as the earlier addon-disabled headless control: `shortCircuit`, `garbageCollection`, `openSystem` and `closedSystem`. Their cause is unresolved; they are not established live-server bugs. Reports separate addon and native results. Packet tests use synthetic encoded bytes; real multiplayer voice playback has not been exercised in this run.

Licensed GPL-3.0-only. Dependencies are distributed separately by their authors.
