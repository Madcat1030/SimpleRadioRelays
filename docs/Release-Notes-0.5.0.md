# Simple Radio Repeaters 0.5.0

Fabric Minecraft 1.20.1 · Simple Radio 1.20.1-4.5.7.9

## Two separate mods

**Simple Radio Repeaters** contains the Relay, antenna scanning, persistent relay network, Repeater Bank, Transponder, Frequency Cards and amplifiers. Installed alone, it follows Simple Radio's dimensional option and native Nether propagation.

**Simple Radio Repeaters – Dimensional Foci** is an optional expansion requiring core 0.5.0. It owns the Nether Focus, End Focus and dimensional propagation hooks. Installing it replaces native dimensional access regardless of Simple Radio's dimensional setting. No change to that setting is required; the expansion does not rewrite it. Direct radios cannot cross dimensions unaided. Focus-equipped relays provide projected transmit/receive sites instead.

Core exposes a small attachment and propagation interface. The expansion registers its own effects and policy; core has no dependency on expansion classes or resources. Focus item IDs are retained. Unknown saved attachment effects resolve safely to no attachment when the expansion is removed.

## Amplifiers

| Component | Relay antenna contribution | Decay reset |
| --- | ---: | --- |
| Copper Amplifier | +25% | No |
| Redstone Amplifier | +50% | No |
| Ender Amplifier | +75% | Yes |

The existing `relay_amplifier` item becomes the Ender Amplifier. These gains apply to the antenna contribution to Relay transmission and reception, not native base power. Ender resets relay decay without repairing inherited audio noise or resetting the total route visit limit. A Relay still needs a valid antenna, positive-strength catalyst and card module. The catalyst and upgrade sockets remain separate; the upgrade socket accepts one amplifier or focus.

## Dimensional Foci

- An Overworld Nether Focus projects to mapped Nether X/Z at roof height. A Nether Nether Focus improves local reception and reach while Nether tweaks are enabled, without projecting back to the Overworld.
- End Focus bridges Overworld and End at matching X/Z. The End pocket is at the bottom of the world; the Overworld pocket uses the source's clamped Y. There is no fixed 500-block cap.
- Two projected sites cannot link directly; a physical relay can serve as a hub in the destination world.
- The expansion's only setting is `netherTweaks`, default `true`, in `config/simpleradio-repeaters-foci.json`. Disable it with `/radiofoci config netherTweaks false`. This restores native Nether propagation while retaining focus bridges and focus-only dimensional access.
- Core settings now contain only `maxRelayHops` and `persistentUnloadedTowers`. Old core dimensional settings and commands are replaced by the expansion's configuration.

## Installation

Replace the previous 0.4.x addon JAR on the server and clients. Install the core `-prototype.jar`; add the Dimensional Foci `-prototype.jar` on both sides to enable its rules. Do not install the old combined addon alongside these builds. The expansion alone is intentionally rejected by Fabric because it requires core 0.5.0.

Recipes, models and translations for foci now ship only in the expansion. Existing relay and card module IDs remain. Removing the expansion discards its effects and follows normal missing-item behavior for its items. Test-free runtime JARs retain readable addon names and debug information. The source bundle includes tests and updated implementation/model-editing documentation.

## Verification and limits

The build and 51 unit tests pass. Core alone passes 41 addon Minecraft tests; the combined installation passes 64, including dimensional override with Simple Radio's option both on and off, focus routing, amplifier gains and module boundaries. Production startup passed with both exact JARs, followed by a successful restart after removing the expansion with a saved focus installed. Fabric's missing-core rejection also passed.

The same four previously recorded native headless tests fail in both configurations: `SystemTest.shortCircuit`, `SystemTest.garbageCollection`, `SystemTest.openSystem` and `SystemTest.closedSystem`. Their causes remain unresolved. No client join or live microphone playback was performed in this run. Evidence is included in `validation/0.5.0/`.

Directional antennas are outside this release.
