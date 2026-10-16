# Simple Radio Repeaters 0.4.0

A Fabric Minecraft 1.20.1 addon for **Simple Radio 1.20.1-4.5.7.9**. A Radio Relay attaches to an antenna core and carries voice through a persistent tower network. Put a six-card Repeater Bank on top to repeat selected channels, or use a three-card Radio Transponder to translate between channels.

Version 0.4.0 adds a second socket on the Relay for a Nether Focus, End Focus or Relay Amplifier. Foci provide mapped reception pockets when native cross-dimensional radio is disabled. When it is enabled, relays use Simple Radio's normal coordinate scaling and attenuation without requiring a focus. Amplifiers reset carrier decay without repairing degraded audio or resetting the relay-visit limit.

## Install and existing worlds

Replace the addon JAR on both server and clients with `simple-radio-repeaters-fabric-1.20.1-0.4.0.jar`. Requirements:

- Minecraft 1.20.1, Java 17+, Fabric Loader 0.19.0+ and Fabric API.
- Simple Radio **1.20.1-4.5.7.9**, exactly, and its Lexiconfig dependency.
- Simple Voice Chat 1.20.1-2.6.0+.

Development checks use Fabric API 0.92.5+1.20.1, Lexiconfig 1.4.21-epic and Voice Chat 2.6.18.

Version 0.4 retains `data/simpleradio_repeaters_relays_v3.dat`. Existing 0.3 towers keep their Bank, catalyst, identity and verified antenna; their new attachment socket starts empty. No migration layer or network reset is added. When upgrading from 0.2, load each tower's chunk once to register it. Earlier network stores are not imported.

Verified towers can relay while unloaded under the normal persistence setting. Routing does not load their chunks. A live native receiver/wire/transmitter station still needs its own chunk loaded.

## Build a tower

1. Build a normal antenna on a supported core, including a native copper-wire extension if needed.
2. Place a Radio Relay against a horizontal side of the core, facing outward.
3. Put a Repeater Bank or Radio Transponder directly above it. Placement copies the Relay's facing.
4. Tune Frequency Cards in the native radiosmithing station and insert them into the front slots.
5. Right-click the Relay with gilded blackstone or an amethyst block. Wait for a positive `ANT` score and `READY`.
6. Optionally install one focus or amplifier in the Relay's left socket.

A Relay always needs a registered catalyst with finite, positive strength, a supported core and a positive native antenna score. A bare Relay and card module stay offline. Valid towers retain native receiver base reception plus antenna gain. The catalyst and attachment are direct installed components; the Relay has no inventory screen.

## Banks and transponders

A Bank repeats each selected frequency/modulation pair independently. Its six slots and existing controls are unchanged.

A Transponder has one card on the left and two on the right. It needs a tuned left card and at least one tuned right card. Click its center display with an empty hand to change direction:

| Mode | Incoming left channel | Incoming right channel |
| --- | --- | --- |
| Simplex, one arrow | Broadcast on the inserted right channels | No conversion |
| Duplex, opposite arrows | Broadcast on the inserted right channels | Broadcast on the left channel |

The two right slots are not automatically connected to one another inside the same unit. Put the left channel in a right slot too if it should remain live while also feeding another frequency. AM and FM are distinct channels and can be translated. Transponders accept both ordinary wireless arrivals and relay-network arrivals, and can feed further Banks or Transponders. Encoded voice, owners, worst interference and traversal counts survive conversion.

## Foci and dimensions

The addon follows Simple Radio's `frequency.crossDimensional` setting automatically:

| Native setting | Relay behavior |
| --- | --- |
| Enabled | Physical relays, receivers and handhelds use native dimension-scaled coordinates, including Y, and native dimensional attenuation. Foci are ignored. |
| Disabled, no focus | Same-dimension radio only. |
| Disabled, Nether Focus | Add a local transmit/receive pocket at mapped Nether X/Z, anchored at the roof: Y=128 in the default Nether. |
| Disabled, End Focus | Add a local transmit/receive pocket at mapped End X/Z, anchored at the bottom: Y=0 in the default End. |

Focus mapping uses the worlds' native coordinate scales: Overworld X/Z divided by eight for the Nether, and unchanged for the End. Home-tower Y does not move a focus pocket. The End focus has **no 500-block cap** and does not target the center island. Native dimensional attenuation, antenna power and the receiving device determine its reach and return range.

Focus pockets connect to physical radios and relays in their target dimension. Two projected pockets cannot link directly; a physical relay in that dimension can receive and rebroadcast between them. Ordinary home-world links remain available. A focus placed in its own named dimension does not create another same-world antenna.

## Power and audio

At each departure:

`power = current tower's nominal transmitter power × its catalyst strength × 0.9 ^ (decay count − 1)`

Fresh traffic starts with total visits 0 and decay count 0. Every relay increments total visits. An ordinary relay increments decay count; an amplified relay resets decay count to 1, so it leaves at its own catalyst-scaled maximum. Subsequent ordinary relays continue at counts 2, 3 and so on until another reset. Native wires and channel changes preserve both counts. The default 64-visit limit still applies across amplifiers, conversions and re-entry.

| Decay count | Gilded blackstone, 90% | Amethyst block, 60% | Custom catalyst, 200% |
| ---: | ---: | ---: | ---: |
| 1 | 90% | 60% | 200% |
| 2 | 81% | 54% | 180% |
| 3 | 72.9% | 48.6% | 162% |
| 10 | 34.87% | 23.25% | 77.48% |

Percentages are relative to the current tower's nominal output. Each tower applies its own catalyst once, rather than multiplying every catalyst on the path. Actual reduced-power legs must pass native reception checks; there is no 1% cutoff.

Audio retains the worst interference on the selected route. An amplifier restores carrier reach, not lost audio quality. Final delivery admits candidates within one interference point of the cleanest viable route and chooses the strongest remaining carrier, retaining that candidate's actual degradation. Hearing a strong tower does not guarantee a handheld can transmit back.

`ANT` is the native antenna score. `LINK` counts distinct outgoing tower neighbors for a fresh first-relay departure. It excludes players and is not an end-to-end delivery guarantee.

## Controls

- Card on a front slot: insert there if empty. Card elsewhere on the front: fill the first available slot. Other faces ignore cards.
- Empty hand on an occupied front card: remove that card. Empty slots and background clicks do nothing.
- Empty hand on a Transponder's center display: toggle simplex/duplex.
- Registered catalyst on the Relay: install one if its socket is empty.
- Focus or amplifier on the Relay: install one if its attachment socket is empty.
- Empty hand on the attachment socket: return its attachment. Other empty-hand Relay clicks return the catalyst.
- Sneak + empty hand on the Relay: toggle power without removing components.

Cards have three-unit height and one unit of click padding above only. A Bank fills top left to bottom right, row by row; a Transponder fills left, upper right, lower right. One module belongs only to the Relay directly below it. Breaking a module drops its cards; breaking a Relay drops both installed components. Hoppers retain card automation.

Wrong items, empty-slot clicks and equivalent writes do not restart antenna verification. Wired-core reloads keep verified antenna data while native wires restore. Missing/replaced components are reconciled on chunk load. Copied Relays cannot retain distant controller bindings or alter the original's settings.

## Crafting

All shaped recipes produce one item. Copper wire is `simpleradio:copper_wire`.

| Item | Ingredients |
| --- | --- |
| Radio Relay | 4 iron ingots, copper ingot, copper wire, redstone dust, native receiver and transmitter blocks |
| Repeater Bank | 4 iron ingots, copper ingot, copper wire, 2 glass blocks, redstone dust |
| Frequency Card | Paper, 2 copper ingots, copper wire, redstone dust |
| Nether Focus | Quartz center, obsidian above, netherrack on either side, copper wire below |
| End Focus | Chorus fruit center, ender pearl above, end stone on either side, copper wire below |
| Relay Amplifier | Eye of ender center, Ancient Debris above, end rods on either side, copper wire below |
| Radio Transponder | 4 iron ingots, glass, native receiving module, native transmitting module, copper wire, amethyst block **or** gilded blackstone |

Ancient Debris is the vanilla material used for the requested netherite ore. The Transponder uses native subparts, not completed radio blocks. Its recipe's amethyst/blackstone does not replace the separate catalyst installed in its Relay. The [full guide](docs/Simple-Radio-Repeaters-Documentation.md#crafting-recipes) shows every grid. Recipe JSON is under `src/main/resources/data/simpleradio_repeaters/recipes/`.

## Commands and settings

`/radiorepeaters status` shows tower and packet counts. Operator commands `/radiorepeaters list` and `/radiorepeaters refresh` show tower details and request loaded-component/antenna checks. The list includes catalyst strength, attachment and Bank/simplex/duplex mode.

The addon config is `config/simpleradio-repeaters.json`: `maxRelayHops: 64`, `persistentUnloadedTowers: true`. Catalysts are mandatory. The focus policy follows the native dimensional switch; there is no separate End radius setting.

Configuration fingerprints run at startup, explicit refresh, successful data-pack reload and every 100 server ticks. Packet lookups read the dimensional switch for their cache scope but do not fingerprint the full configuration or catalyst list. Component edits invalidate routes immediately. Successful reloads also refresh the connection-scoped client catalyst catalog.

## Routing and validation

Routes use directed channel ports. At a given port, an arrival can discard another only when it uses no more total visits, no more decay and no worse audio. This retains useful amplifier detours while stopping redundant cycles. Numeric cache keys include entry, previous total visits, previous decay and routing mode. Limits remain 128 route entries/32,768 elements and 512 link rows/65,536 edges. Eviction only causes recomputation; packet/player/block-entity objects are not cached.

Use JDK 21 for Gradle/Loom; release bytecode targets Java 17. Run `./gradlew build` and `./gradlew runGameTest`. Current evidence is in `validation/0.4.0/` and `validation/build-verification.json`.

All **48 unit tests and 50 addon Minecraft tests pass**. Coverage includes native dimensional math, focus return paths without chunk loads, End reception beyond 500 blocks, physical hubs between pockets, amplifier resets and budget limits, mixed catalysts, directed AM/FM conversion, conversion loops, socket controls, persistence and real copper-wire bridges. Randomized checks compare ordinary routing on 2,000 graphs and amplified routing on another 1,000 graphs against exhaustive references, with changed ingress on a second packet. Native client captures verify the production renderers, card labels and arrows.

The combined run also contains seven native Simple Radio tests. Four match the failures in an earlier addon-disabled headless control: `shortCircuit`, `garbageCollection`, `openSystem` and `closedSystem`. Their cause is unresolved; they are not established live-server bugs. The standalone build succeeds. Tests use synthetic encoded bytes; actual multiplayer microphone playback was not exercised. The 0.3.2 dedicated-server/client catalog lifecycle evidence is retained as historical validation, rather than claimed as a new 0.4 session.

See the [implementation guide](docs/Simple-Radio-Repeaters-Documentation.md) and [model editing guide](art/Model-Editing-Guide.md). Existing Bank assets are unchanged. New meshes use the same 16px palette and native Minecraft component textures.

Licensed GPL-3.0-only. Dependencies are distributed separately by their authors.
