# Simple Radio Repeaters 0.5.6

0.5.6 updates the in-game guide to **Simple Radio Relays**, adds server-recipe illustrations and lets addons supply their own chapters. Transponders gain Multicast as their third mode. Directional Antenna 0.2.7 uses the new 66/44/22/0 wide-angle profile. See [the release notes](docs/Release-Notes-0.5.6.md) and [the chapter contribution format](docs/Handbook-Extensions.md).

This workspace also includes the separate [Directional Antenna 0.2.6 prototype](directional/README.md), with the solid stepped receiver, wide-angle array, shared mounts and narrowed reception profiles. Earlier dish designs remain as appearance options. Its runtime does not require either addon.

0.5.5 adds an operator performance recorder: `/radiorepeater test start`, `/radiorepeater test stop`, or `/radiorepeater test start 60`. Speak normally while recording, then compare the JSON files in `logs/radiorepeaters/` before and after expanding the network. It records routing stages, RF comparisons, cache hits/misses, graph rebuild reasons, signal/movement changes and real accepted deliveries. Routing behavior is unchanged. See [commands and report details](docs/Release-Notes-0.5.5.md). Core and matching Foci are 0.5.5; Directional Antenna remains 0.2.6.

0.5.4 moves each relay's radio origin to its resolved antenna core, including copper-wire extensions. The verified core position is saved for unloaded routing and focus projections. Native-object parity tests check reach, travel power and audio severity against the numeric adapter. See [release notes](docs/Release-Notes-0.5.4.md).

0.5.3 adds **Relays for Dummies**, an in-game handbook crafted shapelessly from a vanilla book and native copper wire. Right-click to read it, or find it in the Repeaters creative tab. Clickable contents cover tower setup, cards and controls, signal quality, catalysts, amplifiers, transponders and troubleshooting. Dimensional Foci and Extenders chapters appear only when their respective addons are installed. Every opening uses the current language and installed content, so existing books grow without recrafting. The reader uses vanilla book pages and adds no book-mod dependency.

0.5.2 fixes client channel cleanup deleting a live server frequency after travel, and prevents microphone/speaker activity from repeatedly showing `CHECK`. See [the fixes and test instructions](docs/Release-Notes-0.5.2.md). The earlier activation-time repair remains. Hold the transmitting radio and run `/radiorepeaters diagnose` to record saved/live tower state, RF paths and endpoint registrations in `logs/latest.log`.

Fabric Minecraft 1.20.1 addons for **Simple Radio 1.20.1-4.5.7.9**. This release separates the relay network from the optional dimensional expansion.

| JAR | What it adds | Requires |
| --- | --- | --- |
| Simple Radio Repeaters | Relay, six-card Repeater Bank, three-card Transponder, Frequency Cards, antenna scanning, persistent routing and three amplifiers | Simple Radio |
| Dimensional Foci | Nether/End focus items, focus-only dimensional radio and optional Nether propagation adjustments | Matching Simple Radio Repeaters core |

Install the core on server and clients. Add the Foci JAR on both sides if you want its dimensional rules. Replace the old 0.4.x addon JAR. Dependencies remain Minecraft 1.20.1, Java 17+, Fabric Loader 0.19.0+, Fabric API, Simple Radio **1.20.1-4.5.7.9** and its Lexiconfig dependency, plus Simple Voice Chat 1.20.1-2.6.0+.

## First tower

1. Build a supported native antenna and core. Native copper-wire extensions work.
2. Put a Relay against the core's horizontal side, facing outward.
3. Place a Repeater Bank or Transponder **directly above** the Relay.
4. Tune cards at the native radiosmithing station and insert them on the module's front.
5. Install gilded blackstone or an amethyst block in the Relay. Wait for positive `ANT` and `READY`.
6. Optionally install one amplifier or, with the expansion, one focus in the other socket.

The Relay requires a registered positive-strength catalyst and a positive verified antenna. A bare Relay and Bank cannot operate. Card modules have no inventory screen. Hoppers cannot insert/extract cards; direct unsided mod inventory integration remains available.

## Amplifiers

| Component | Antenna gain for Relay TX/RX | Resets relay decay |
| --- | ---: | --- |
| Copper Amplifier | +25% | No |
| Redstone Amplifier | +50% | No |
| Ender Amplifier | +75% | Yes |

The existing `relay_amplifier` item is now named Ender Amplifier. Its endgame recipe remains. Multipliers affect antenna contribution, not native base power. A 32-point antenna contributes 40, 48 or 56 points. The single upgrade socket accepts one amplifier or focus; the catalyst has its own socket. Amplification does not repair inherited audio noise.

Departure power remains `nominal tower power × current catalyst strength × 0.9^(decay count − 1)`. Only Ender resets decay to 1; total visits still consume the configured budget. There is no 1% cutoff.

## Dimensional behavior

**Core alone follows Simple Radio's dimensional setting and native Nether calculations.**

**Installing Dimensional Foci replaces native dimensional access, whether Simple Radio's setting is ON or OFF.** Direct radio transmissions cannot cross worlds. A focus-equipped Relay provides a projected transmit/receive site instead. The expansion does not rewrite Simple Radio's configuration.

| Focus and home dimension | Effect |
| --- | --- |
| Nether Focus in Overworld | Two-way pocket at mapped Nether X/Z, at the Nether roof (default Y=128) |
| Nether Focus in Nether | Local range/reliability improvement while Nether tweaks are enabled; no Overworld projection |
| End Focus in Overworld | Two-way pocket at matching End X/Z, at the bottom (default Y=0) |
| End Focus in End | Two-way pocket at matching Overworld X/Z and the source's clamped Y |

There is no fixed End radius. Two projected pockets cannot link directly; a physical relay in their world can act as a hub. A handheld can reply through an existing pocket if its real signal can reach it.

The expansion's one setting is `config/simpleradio-repeaters-foci.json`:

```json
{ "netherTweaks": true }
```

With it enabled, ordinary local Nether distance is physical distance ×4, with the moderate noise floor. A Nether-focused Nether Relay uses ×2 and native baseline noise. Nether dimensional legs retain native coordinate mapping and twice the native dimensional attenuation modifier. Disabling it restores native Nether propagation while retaining focus bridges and the expansion's focus-only dimensional policy.

An operator can change and save the setting immediately:

```text
/radiofoci config netherTweaks false
/radiofoci config netherTweaks true
```

Core settings now contain only `maxRelayHops: 64` and `persistentUnloadedTowers: true` in `config/simpleradio-repeaters.json`. The old core dimensional switches are removed.

## Controls

- Card on a front slot: insert into that slot. Card elsewhere on the front: first available slot. Other faces ignore cards.
- Empty hand on an occupied front card: remove that card. Background clicks leave cards alone.
- Transponder center display: cycle Simplex, Duplex and Multicast.
- Catalyst or upgrade on Relay: insert if its matching socket is empty.
- Empty hand on/near a front socket: remove that component. Other ordinary empty-hand clicks do nothing.
- Sneak + empty hand on Relay: toggle power.

A Bank repeats selected channels. A Transponder converts left to right in Simplex or both directions in Duplex. Multicast shares every selected channel with all selected channels, including itself. It needs one tuned card on each side. In Simplex/Duplex, put the main frequency on both sides if it should also be repeated unchanged.

`/radiorepeaters status`, `/radiorepeaters list` and `/radiorepeaters refresh` retain their existing roles. `/radiorepeaters diagnose` writes a read-only report to `logs/latest.log`; hold the transmitting walkie/transceiver in the main hand for its RF probe. List/refresh/diagnose require operator level 2.

## Builds and evidence

One workspace builds two independent runtime JARs. Use JDK 21 for Gradle; release classes target Java 17.

```sh
./gradlew build prototypeJar
./gradlew --continue :core:runGameTest :foci:runGameTest
```

Outputs are in `core/build/libs/` and `foci/build/libs/`. Install the `-prototype.jar` files for distributions without addon test fixtures. They retain readable addon names and debug data. Source and development builds retain the tests.

0.5.3 passes all 53 core/Foci unit tests and the focused dedicated-server handbook recipe/use test. The real Minecraft client checks all four chapter combinations, verifies page fit and navigation, and captures the native reader. Evidence is under `validation/0.5.3/`. The earlier 0.5.2 routing regression suite passed 50 core addon Minecraft tests and 73 combined addon tests; it is not repeated for this handbook-only change. The four previously recorded native headless failures remain documented separately from addon regressions. Live microphone playback is outside the automated checks.

See [the implementation guide](docs/Simple-Radio-Repeaters-Documentation.md) and [model editing guide](art/Model-Editing-Guide.md).

GPL-3.0-only. Dependencies are distributed separately by their authors.
