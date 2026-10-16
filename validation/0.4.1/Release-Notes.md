# Simple Radio Repeaters 0.4.1

Fabric Minecraft 1.20.1; requires Simple Radio 1.20.1-4.5.7.9 exactly. Install the test-free prototype JAR on the server and every client, replacing the previous addon JAR.

## Nether radio

Ordinary wireless Nether links now use four times physical distance instead of eight times. Their baseline interference floor is native AM/FM interference plus 10 percentage points: 25% AM and 12% FM with defaults. This applies to native radios even without a relay network.

A Nether Relay with a Nether Focus uses two times physical distance and the normal native interference floor for local incoming and outgoing links. The focus does not create an Overworld projection. If either physical Relay endpoint has a Nether Focus, the local link gets this benefit. Previously inherited audio degradation is retained.

Nether crossings keep native coordinate mapping, including eightfold Nether Y, and double the configured native dimensional modifier. With default additive loss, FM loses 9 and AM loses 5 per effective distance unit. Overworld roof projections remain intentionally weak. A projected leg never receives the cheaper local-Nether profile.

## Focus behavior

A Nether Focus in the Overworld still creates a bidirectional pocket at mapped Nether X/Z and roof Y=128. Nether users can reply through that pocket, but a Nether tower cannot create an independent Overworld projection with its own Nether Focus.

An End Focus connects the Overworld and End in either direction. Overworld towers project at matching End X/Z and bottom Y=0. End towers project at matching Overworld X/Z and their own Y, clamped inside the Overworld build range. There is no 500-block cap. The End Focus does not bridge from the Nether.

Native cross-dimensional mode still removes the requirement for foci and suppresses their extra projected sites. The local Nether Focus benefit remains active in either mode. Two projected sites cannot directly form a backbone; a physical relay must connect them.

## Amplification Focus

The Relay Amplifier is now displayed as Amplification Focus. Its item ID and recipe are unchanged. It increases the Relay's antenna score by 50% for transmit and receive gain and resets carrier decay to 1 at departure. The built-in native radio base is not multiplied. A 32-point antenna acts as 48 points; routing retains fractional scores and the screen rounds for display. Saved records retain the original verified score, preventing repeated insertion/reload from compounding the boost.

The catalyst, supported core, positive antenna score and total relay-visit limit remain mandatory. Audio is never repaired. All three attachment tooltips use the requested wording.

## Validation

Standalone build and prototype packaging pass. All 51 unit tests and 54 addon Minecraft GameTests pass. New checks cover native/virtual Nether parity, local focus reach and replies, stricter Nether crossings, reverse End projection and replies, and amplifier transmit/receive gain. Existing wire, card, persistence, conversion, bounded-search and worst-audio checks still pass.

The combined run also executes seven native Simple Radio tests. The same four recorded failures remain: shortCircuit, garbageCollection, openSystem and closedSystem. Their cause is unresolved; these are not established live-server bugs. Addon and native XML reports are separated in the source archive.

The prototype excludes the addon's GameTest entrypoints, 13 fixture classes and 13 audited test-only helper methods. Retained gameplay methods, fields, constant pools, debug information and resources are checked against the full build. No addon renaming or obfuscation is added. Minecraft's required Fabric mappings remain.

Checks use synthetic packets. Live multiplayer microphone playback was not exercised in this run. The earlier model screenshots remain historical 0.4.0 captures; geometry is unchanged.
