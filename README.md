# Simple Radio Repeaters 0.5.0

Fabric Minecraft 1.20.1 addons for **Simple Radio 1.20.1-4.5.7.9**. This release separates the relay network from the optional dimensional expansion.

| JAR | What it adds | Requires |
| --- | --- | --- |
| Simple Radio Repeaters | Relay, six-card Repeater Bank, three-card Transponder, Frequency Cards, antenna scanning, persistent routing and three amplifiers | Simple Radio |
| Dimensional Foci | Nether/End focus items, focus-only dimensional radio and optional Nether propagation adjustments | Simple Radio Repeaters 0.5.0 |

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
- Transponder center display: toggle simplex/duplex.
- Catalyst or upgrade on Relay: insert if its matching socket is empty.
- Empty hand on/near a front socket: remove that component. Other ordinary empty-hand clicks do nothing.
- Sneak + empty hand on Relay: toggle power.

A Bank repeats selected channels. A Transponder converts its left channel to its right channels in simplex, or both directions in duplex. It needs one tuned card on each side. Put the main frequency on both sides if it should also be repeated unchanged.

`/radiorepeaters status`, `/radiorepeaters list` and `/radiorepeaters refresh` retain their existing roles. List/refresh require operator level 2.

## Builds and evidence

One workspace builds two independent runtime JARs. Use JDK 21 for Gradle; release classes target Java 17.

```sh
./gradlew build prototypeJar
./gradlew --continue :core:runGameTest :foci:runGameTest
```

Outputs are in `core/build/libs/` and `foci/build/libs/`. Install the `-prototype.jar` files for distributions without addon test fixtures. They retain readable addon names and debug data. Source and development builds retain the tests.

All 51 unit tests pass. Core alone passes 41 addon Minecraft tests; the combined installation passes 64. Current evidence is under `validation/0.5.0/`. The full guide explains the code boundary, crafting, persistence and propagation. The four previously recorded native headless failures remain documented separately from addon regressions; live microphone playback is outside the automated checks.

See [the implementation guide](docs/Simple-Radio-Repeaters-Documentation.md) and [model editing guide](art/Model-Editing-Guide.md).

GPL-3.0-only. Dependencies are distributed separately by their authors.
