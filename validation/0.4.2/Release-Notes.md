# Simple Radio Repeaters 0.4.2

Fabric Minecraft 1.20.1; requires Simple Radio 1.20.1-4.5.7.9 exactly. Replace the addon JAR on the server and all clients.

## Optional dimension features

The addon optionally enables interdimensional transmission through focused relays and optionally adjusts Nether radio signals. Both preferences default to true, but activate only while Simple Radio's frequency.crossDimensional is false. When native dimensional radio is enabled, its original distance and interference calculations take priority automatically: there are no addon Focus projections, local Focus benefits or Nether propagation changes. Ordinary relays, Transponders and amplifiers continue working.

The independent server preferences in config/simpleradio-repeaters.json are:

```json
{
  "maxRelayHops": 64,
  "persistentUnloadedTowers": true,
  "interdimensionalRelays": true,
  "netherTweaks": true
}
```

Disable Nether adjustments immediately and save the choice:

```text
/radiorepeaters config netherTweaks false
```

Disable addon Focus bridges immediately and save the choice:

```text
/radiorepeaters config interdimensionalRelays false
```

Use true to enable either preference again. Operator permission level 2 is required. /radiorepeaters config shows configured and effective states, including a native-mode override. Either setting command without its boolean queries just that preference. File edits take effect on server restart; commands take effect immediately and clear cached routes. Preferences remain saved when native dimensional radio is switched on or off.

Disabling Nether tweaks restores native Nether distance, baseline noise and dimensional attenuation while retaining any independently enabled Focus bridges. Disabling bridges removes projected sites while retaining any independently enabled local Nether adjustments. Nether Focus tooltips explain the activation requirement. Amplification Focus behavior is unchanged.

## Fixed

Transponders are included in both mineable/pickaxe and needs_stone_tool. A stone-or-better pickaxe now gives normal harvesting speed and drops the block itself; inserted cards continue dropping separately.

Translated receiver delivery uses get() and skips an absent native frequency instead of creating one. The virtual graph still crosses translated channels without native listeners. A two-Transponder regression verifies that packets reach the final listener while the intermediate channel stays unregistered.

## Validation

Standalone build passes. All 51 unit tests and 58 addon Minecraft GameTests pass. New checks exercise native-mode priority across all feature combinations, live mode transitions and cached geometry, persistent operator commands, Transponder tool tier/speed/loot, and listener-free translated intermediate hops.

The combined run also contains seven native Simple Radio tests. Four previously recorded headless failures remain: shortCircuit, garbageCollection, openSystem and closedSystem. Their cause remains unresolved; they are not established live-server bugs. Reports are separated in validation/0.4.2/.

The distributed prototype excludes addon GameTest entrypoints, fixture classes and audited test-only helpers. Retained methods and debugging data are compared against the full build. No addon obfuscation is added. Synthetic packets are used; live multiplayer microphone playback was not exercised.
