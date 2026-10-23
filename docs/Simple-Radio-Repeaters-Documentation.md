# Simple Radio Repeaters 0.5.2

Historical implementation notes for 0.5.2. For current setup, use the [README](../README.md); for current build, test and integration details, use [Development](Development.md). Later behavior changes are recorded in the versioned release notes.

A pair of Fabric Minecraft 1.20.1 addons for **Simple Radio 1.20.1-4.5.7.9**. The core provides persistent stationary relay networks. The optional Dimensional Foci addon replaces native dimensional radio with focus-equipped relay bridges.

## Installation and module ownership

| Module | Contents |
| --- | --- |
| `core` / `simpleradio_repeaters` | Relay, Bank, Transponder, Frequency Cards, three amplifier tiers, antenna scanner, saved tower records, route mapping/updating and delivery hooks |
| `foci` / `simpleradio_repeaters_foci` | Nether and End focus items/recipes/models, projection policy, optional Nether propagation and native dimensional guards |

Install core on server and clients. Install the matching Foci JAR on both sides to enable its rules. Foci declares a required dependency on the matching core version; Fabric rejects an addon-only installation before game startup. Core has no dependency on the expansion. Both use Simple Radio 1.20.1-4.5.7.9, Fabric API, Fabric Loader 0.19.0+, Java 17+ and Simple Voice Chat 1.20.1-2.6.0+. Include Simple Radio's Lexiconfig dependency.

Replace the old 0.4.x addon JAR. Existing Relay/Bank/Transponder/card IDs are unchanged. The two focus IDs also remain `simpleradio_repeaters:nether_focus` and `simpleradio_repeaters:end_focus`, but the expansion now owns their registration and resources. Install both JARs to retain those items. Loading without the expansion follows normal missing-item handling; focus effects and saved projected sites do not remain active.

## Building and using a tower

Build a native antenna on a supported solid core, optionally connected through native copper wire. Put a Relay on a horizontal side of the core, facing outward. A Repeater Bank or Transponder must be directly above the Relay and adopts its facing during placement. Side and bottom module bindings are rejected, including saved/copied bindings.

Tune Frequency Cards at the native radiosmithing station and insert them into the front slots. Install gilded blackstone or an amethyst block into the Relay. The server accepts any registered native catalyst with finite positive strength. A supported core, a positive verified antenna and a catalyst are mandatory. A bare Relay and module remain offline.

The Bank repeats each selected frequency/modulation independently. Duplicates select the same channel once. Blank cards occupy slots but do not activate a frequency. AM and FM remain separate channels. A Relay does not require a microphone, speaker or separate native RX/TX to repeat traffic. Native radios and wired stations join through their existing endpoints.

The Transponder replaces the Bank. Its single left slot is the input in Simplex; both right slots are outputs. Duplex also converts either right input to the left output, without directly cross-connecting the right slots. Multicast shares each selected channel with every selected channel, including its unchanged channel. A tuned left card and at least one tuned right card are required in all modes. Matching cards on both sides preserve a channel in Simplex/Duplex. Converted traffic can continue through further relays and Transponders.

## In-world controls and display

| Interaction | Result |
| --- | --- |
| Card on front slot | Insert there if empty; never replace or fall back from an occupied slot |
| Card elsewhere on module front | First available slot |
| Card on other face | No change |
| Empty hand on occupied front card | Return that card |
| Empty hand elsewhere on Bank | No change |
| Empty hand on Transponder center display | Cycle Simplex, Duplex and Multicast |
| Registered catalyst on Relay | Install in the empty catalyst socket |
| Amplifier or focus on Relay | Install in the empty upgrade socket |
| Empty hand on/near a front socket | Return that component only |
| Other ordinary empty-hand Relay click | No change |
| Sneak + empty hand on Relay | Toggle enabled state |

Cards are three model units high with one unit of targeting padding above, none below. Bank fill order is top left to bottom right, row by row. Catalyst and upgrade ejection regions each include a one-unit margin; other faces/background do not eject components. Survival insertion consumes one item; creative insertion retains it. Returned items enter inventory or drop if full. Breaking a module drops its cards. Breaking a Relay drops its installed components and removes its saved network record.

The upgrade socket is shared: one amplifier OR one focus. The catalyst occupies its separate socket. Hoppers and other sided automation cannot move cards; unsided `Container` integrations remain usable. Quiet `removeItemNoUpdate` follows the vanilla contract. The loaded Relay polls its module each tick, so its routing recovers by the next tick; integrations still call `setChanged()` for inventory persistence and client synchronization.

`ANT` is effective antenna score, including an amplifier's bonus, rounded only for display. `LINK` counts outgoing neighboring towers for a fresh departure, not players or every reachable network node. Links are directed and do not prove a return path.

`READY` means configured and verified. `NO ANT`, `NO CAT`, `NO CARD`, `NO BANK` and `OFF` identify missing requirements. `SCAN` means an initial antenna check is pending. `CHECK` retains an eligible saved antenna while rechecking. `WAIT BANK` means module data is pending. A checking display does not itself disable a previously verified network node.

## Amplifiers, catalysts and audio

| Upgrade | Antenna multiplier | Raw score 32 contributes | Decay reset |
| --- | ---: | ---: | --- |
| Copper Amplifier | 1.25 | 40 | No |
| Redstone Amplifier | 1.50 | 48 | No |
| Ender Amplifier | 1.75 | 56 | To 1 |

The existing `relay_amplifier` item is renamed Ender Amplifier and retains its recipe. All tiers boost the Relay's antenna-derived TX and RX contributions. Native transmitter/receiver base power and the saved raw antenna score are unchanged. A zero antenna stays zero.

For a departure:

`nominal = native transmitter base + raw antenna score × upgrade multiplier × transmitter aptitude`

`departure = nominal × current tower catalyst strength × 0.9^(decay count − 1)`

Fresh traffic starts at zero visits/decay. Each ordinary Relay increments both. Ender increments total visits but resets decay to 1. Copper and Redstone do not reset decay. Subsequent ordinary towers resume at counts 2, 3 and so on. The configured total-visit budget remains finite across amplifiers, channel conversions and re-entry. Native wires retain both counts.

At count 1, gilded blackstone's 90% strength gives 90% of nominal; amethyst's 60% gives 60%. At counts 2 and 3 they give 81%/72.9% and 54%/48.6%. Each tower applies its own catalyst once. Strengths are not multiplied across the whole route. Custom strengths above 100% receive the same shared 10% retention, so cycles cannot grow output without limit. There is no arbitrary 1% cutoff; a weak carrier can still reach a very close receiver.

Reception uses native receiver base plus effective antenna contribution. Catalyst strength limits departure, not receiver gain. Amplification restores RF reach and does not repair inherited audio noise. Every candidate preserves the worst interference on its route. Final selection admits candidates within one interference point of the cleanest viable candidate, then prefers the stronger remaining carrier, followed by cleaner audio/fewer visits.

A distant player hearing a tower does not establish a return link. Their handheld must still reach a physical or projected receiving site. Wired RX-to-TX bridges retain their incoming carrier and counts; native wiring alone does not reset an already-frequenced source to full antenna departure power.

## Dimensional Foci rules

Without the expansion, all dimensional access, coordinate scaling and Nether propagation follow Simple Radio's settings. Core adds no custom Nether range or interference.

With Dimensional Foci installed, native direct wireless crossings are disallowed regardless of Simple Radio's `frequency.crossDimensional` value. Focus-equipped Relays create the permitted sites. Both native endpoint delivery and virtual relay routing follow that policy. The expansion does not edit the native config file or change its stored boolean.

| Focus and physical home | Projected or local behavior |
| --- | --- |
| Nether Focus, Overworld | Bidirectional pocket at mapped Nether X/Z, at its roof: normally Y=128 |
| Nether Focus, Nether | Improved physical local propagation while Nether tweaks are enabled; no Overworld projection |
| End Focus, Overworld | Bidirectional pocket at matching End X/Z, at its bottom: normally Y=0 |
| End Focus, End | Bidirectional pocket at matching Overworld X/Z, retaining source Y clamped to that world's build range |
| Focus in an unsupported dimension | Physical local site only |

Nether X/Z use the native dimension coordinate-scale ratio. End has no fixed 500-block cap and does not target the central island. Native dimensional attenuation still governs projected legs. The Nether roof pocket is intentionally weak at lower elevations.

A pocket must connect to a physical receiver or Relay. Two projected pockets cannot link directly; a physical End/Nether hub can receive and rebroadcast between them. Every tower still needs its catalyst, supported antenna, selected channels and actual RF reach. Projection never force-loads terrain.

A focus departure localizes the carrier origin to its exit dimension for the native speaker's origin gate. Audio bytes, owners, original owner, activity, worst interference and traversal counts remain intact. This is why focus output can play while ordinary foreign-origin sources are rejected.

## Nether setting

The expansion has one server setting, defaulting to true:

```json
{ "netherTweaks": true }
```

File: `config/simpleradio-repeaters-foci.json`.

| Link with tweaks enabled | Distance and noise |
| --- | --- |
| Local Overworld or End | Native calculation |
| Ordinary local Nether | Physical distance ×4; native baseline interference +10 points |
| Local Nether with a Nether-focused Relay endpoint | Physical distance ×2; native baseline interference |
| Dimensional leg involving Nether, including projections | Native coordinate scaling including Y ×8; twice native dimensional attenuation modifier |
| Overworld–End projected leg | Native coordinate scaling and native dimensional modifier |

All axes use the appropriate factor. A projected Nether pocket remains a dimensional leg and does not receive the cheaper local profile. With native additive loss 1 and dimensional modifier 4, a tweaked Nether crossing costs 9 per effective distance on FM or 5 on AM; an End crossing costs 5 or 3. Native baseline interference is 2% FM/15% AM, so ordinary tweaked Nether floors are 12%/25%.

Turning `netherTweaks` off restores native Nether range/interference, including on focus legs. It does not disable focus bridges or permit direct dimensional radio. A native global setting of ON does not suppress the expansion or its Nether setting.

Operators can save/apply a live change using `/radiofoci config netherTweaks false` or `true`. The command without a boolean reports the current setting. Commands require permission level 2 and invalidate cached routes. File edits take effect after restart. Client-local settings do not control server propagation.

## Core configuration and commands

`config/simpleradio-repeaters.json` contains:

```json
{ "maxRelayHops": 64, "persistentUnloadedTowers": true }
```

The hop budget is clamped to 1–128. Disabling persistence makes unloaded towers unavailable. The former core dimensional switches are removed and omitted when this config is saved. Catalysts remain mandatory.

`/radiorepeaters status` reports counts. Operator `/radiorepeaters list` reports tower positions, raw/effective antenna details, channels, catalyst and attachment/module mode. `/radiorepeaters refresh` reconciles loaded components, refreshes resource settings and requests antenna checks.

Operator `/radiorepeaters diagnose` writes `[relay-diagnostic]` lines to `logs/latest.log`. Hold the transmitting walkie-talkie or transceiver in the main hand. The report includes installed versions, RF configuration, saved and loaded tower/module data, native endpoint activity/validity, canonical frequency registration and cached/fresh RF route probes from the player's position. The probe emits no audio, requests no missing chunks and does not replace the live cached graph. Its candidate strengths are RF-only; they do not imply actual delivery past registration, activity, custom accept criteria or deduplication.

From 0.5.5, operators can record actual routing performance with `/radiorepeater test start`, `/radiorepeater test stop` or `/radiorepeater test start <seconds>` (1–3600). The singular command is an alias of the existing plural root. Recording spans the server's dimensions and ends by explicit stop, elapsed time or server shutdown. Speak normally during the recording: the command observes existing traffic and does not generate any. Each run writes a JSON report to `logs/radiorepeaters/`, with a summary in `latest.log`.

Reports separate entrance passes, path search/merge, direct and relayed candidate selection, callbacks/wires and native delivery. They include comparison counters, cache activity, live/maintenance/diagnostic graph build timings, invalidation reasons, tower state and bounded movement/RF-state observations. Self timings exclude nested repeater calls to avoid double-counting wired re-entry. Percentiles are labeled histogram upper bounds. Capturing adds measurement overhead and is disabled by default; it does not change cache lifetime, packet handling or route choices. See [0.5.5 notes](Release-Notes-0.5.5.md) for the schema's scope and comparison procedure.

## Native receiver reload repair

Simple Radio 1.20.1-4.5.7.9 stores a frequency object when an auditory block deserializes. Deserialization may precede entity ticking; native garbage collection can remove the empty channel before the block activates. Native activation then registers its receiver/transmitter on the stale object. A placed radio can become silent on a valid relay output even though routing and tower links remain healthy. Replacing the radio reacquires the live object, which explains that temporary workaround.

Core 0.5.1 injects at the start of `activate()` on native `RadioBlockEntity`, `ReceiverBlockEntity` and `TransmitterBlockEntity`. It resolves the canonical frequency for the stored frequency number and modulation immediately before native routers are created. A correction logs `[radio-reload]` once for that activation. The hook neither repairs audio quality nor changes RF math. It creates a native channel only for a device that is actually activating; translated routing still skips absent listener channels instead of creating empty frequencies per packet.

The activation repair covers both an evicted channel and a different current generation of the same channel. It does not run on a server device that remains loaded while the client travels away. A relay refresh is not a native receiver reactivation and cannot repair an already orphaned device in an older build.

Core 0.5.2 also fixes the native `FrequencyChannel` cleanup path: removing the last client receiver or transmitter previously deleted the server's channel with the same number. This strands still-active server endpoints, particularly in spawn chunks. `FrequencyChannelLifecycleMixin` records the creating `Frequencies` registry in the constructor and uses a composable MixinExtras `WrapOperation` for empty-channel removal. Cleanup targets that registry and checks that the registered object is still the same channel. Client cleanup cannot evict a server channel; stale cleanup cannot remove a replacement generation. No per-packet repair is needed.

## Persistence and antenna verification

The saved network remains `data/simpleradio_repeaters_relays_v3.dat`. Immutable tower snapshots retain identity, dimension, relay position, antenna-base position, channels, catalyst ID, attachment effect name, module identity/position, Transponder policy, verified antenna and dependency footprint. Existing records without `AntennaBase` keep their previous radio origin until a successful scan resolves the antenna; earlier stores are not imported.

From 0.5.4, radio distance and emitted-carrier origins use the centre of the resolved core directly below the mast. A copper-wire extension resolves to the remote antenna core. The relay box remains the identity/ownership location, so module mounting, removal reconciliation and directional-dish bindings still address the correct block. Successful origin changes invalidate cached routes even if antenna strength is unchanged. Incomplete reload scans retain the last verified origin. Foci project from the antenna coordinates with their existing scale and height rules.

Loaded Relay ownership resolves copied UUIDs before adopting/publishing controller state. Module bindings must point directly above the Relay. A moved/copied Relay cannot keep a distant wireless Bank. Ordinary removal retires the appropriate record/binding. Chunk-load reconciliation detects missing/replaced blocks and identities after external edits/restoration, including ghost relays.

Antenna scans follow the native supported antenna/core/wire structure. They check chunk availability before reading terrain and defer incomplete scans. Audio routing never scans terrain or requests missing chunks. Settling/retry logic retains a verified wired-core antenna while native wires restore, confirms completed zero scores before publishing disconnection and uses the native 200-tick periodic boundary as a fallback. Wrong items, blank-slot clicks and equivalent settings writes do not repeatedly restart scanning. Neighbor notifications from a still-present microphone or speaker beside the relay are ignored because native audio activity polls redstone neighbors. Actual core changes and removal/replacement still request verification. An eligible relay continues forwarding during legitimate `CHECK` states.

Unloaded verified towers can route from saved numeric snapshots. A physical native receiver/wire/transmitter station still needs its own chunk loaded. Absent focus registration resolves saved focus effects to NONE and creates no projected sites.

## Implementation and module interface

`RelayBlock` and `RelayBlockEntity` own physical interaction, catalyst/upgrade stacks, identity, antenna verification, module binding and display state. `ChannelBankBlockEntity` owns six card stacks, inventory integration and cached display labels. `TransponderBlockEntity` uses three card stacks plus conversion direction. Client renderers remain entirely in core and render the installed item/model without referring to focus classes.

`RelayAttachment` is a registry of immutable effects rather than a closed enum. Core registers NONE and its three amplifier effects; the expansion registers its own focus effects. Each effect specifies antenna multiplier and decay-reset behavior. `RelayAttachmentItem` handles generic socket recognition and optional addon tooltip text. Stable effect names are saved in `TowerRecord`; unknown effects resolve to NONE.

`RadioPolicy` is the small propagation extension interface. Core's default returns one physical site, normal propagation and the native dimensional permission. `RadioSpace.installPolicy` installs the expansion policy during initialization and rejects a second policy provider. `FocusPolicy` adds projection geometry, always disables unassisted native crossings and supplies optional Nether profiles. Its cheap setting fingerprint participates in network cache keys, so an old route cannot retain a previous profile after a live toggle.

`RadioSpace` calculates coordinate-scaled distances and directed legs using existing ServerLevels. It never reads blocks/chunks. A leg stores its dimensional state and numeric propagation profile. `NativeRadioAdapter` applies native RF configuration, registered catalyst strength, antenna gain and propagation to native-compatible reach/loss math.

The expansion alone owns `NativeTransmissionMixin`, `NativeTravelMixin` and `DimensionalSpeakerMixin`. Transmission guards reject foreign native destinations. Travel guards zero direct foreign wireless carriers even if another caller skips the reach check; native wires retain their ordinary behavior. Local wireless distance/noise hooks share the same profile as virtual routing. The speaker hook uses MixinExtras `ModifyExpressionValue` on the boxed dimensional setting, enforcing a local carrier origin without replacing the stored config. Distance hooks also use composable expression modification rather than redirects.

`TowerManager` owns loaded instances, saved records, chunk/component indexes and immutable per-channel network caches on the server thread. Settings/resource changes invalidate graphs. Native configuration/catalyst fingerprints are checked at startup, explicit refresh, successful data-pack reload and periodic ticks, not rebuilt for every voice packet. `RelayGraph` caches useful total-visit/decay/noise alternatives with dominance pruning and bounded cache weights; amplifier detours remain possible but redundant cycles are pruned. Cached values contain no player, block entity or voice-packet objects.

`RelayRouter` evaluates current entrances and receiver exits, performs channel conversion through directed ports and materializes a native source for selected repeated delivery. `RelaySourceState`/`RadioSourceMixin` retain shared frame identity plus per-branch total visits, decay and inherited noise across copies and wires. Frame dedupe prevents replay. The one-point carrier preference compares paths for one transmitter; separate transmitters carrying the same source still have the previously documented first-accepted-copy limitation.

`RadioBlockActivationMixin` contains the native frequency lifecycle repair. `RelayDiagnostics` derives an operator-requested textual snapshot, including an independent numeric graph from `TowerManager.inspectNetwork`; this code does not run on each voice packet. `TowerManager.records()` remains in the runtime build because diagnostics now uses that immutable collection snapshot.

## Models and lighting

Current resources are under `core/src/main/resources` and `foci/src/main/resources`. Existing Bank/card geometry and text placement are unchanged. Core houses the Relay and amplifier models; Foci houses the two focus item models. Focus models/recipes retain their established IDs. Expansion language entries use a separate resource namespace to avoid file collisions with core language resources.

Physical inserts use world sky/block light from the cell in front of the Relay, retaining the 0.4.4 darkness fix. Displays use full-bright text. Bank labels reuse immutable cached presence/strings; no server query or tuning-NBT parse occurs every frame. The model editing guide provides the existing coordinates and font controls; update paths to the relevant module. Runtime JSON remains authoritative for the current meshes.

## Crafting

All recipes are shaped and produce one item. W is native `simpleradio:copper_wire`. Relay/catalyst installation remains separate from crafting.

| Item | Grid, rows separated by / | Key |
| --- | --- | --- |
| Relay | IWI / RDT / ICI | I iron ingot; R native receiver; D redstone dust; T native transmitter; C copper ingot |
| Bank | ICI / GDG / IWI | I iron ingot; C copper ingot; G glass block; D redstone dust |
| Frequency Card | _P_ / CDC / _W_ | P paper; C copper ingot; D redstone dust |
| Transponder | IGI / RCT / IWI | I iron ingot; G glass; R receiving module; C amethyst block OR gilded blackstone; T transmitting module |
| Copper Amplifier | _C_ / CDC / _W_ | C copper ingot; D redstone dust |
| Redstone Amplifier | _B_ / CDC / _W_ | B redstone block; C copper ingot; D redstone dust |
| Ender Amplifier | _A_ / RER / _W_ | A Ancient Debris; R end rod; E eye of ender |
| Nether Focus | _O_ / NQN / _W_ | O obsidian; N netherrack; Q quartz |
| End Focus | _P_ / ECE / _W_ | P ender pearl; E end stone; C chorus fruit |

Recipe JSON lives in each module's `src/main/resources/data/simpleradio_repeaters/recipes/`. The Transponder recipe uses native subparts, and its center catalyst ingredient does not fill the Relay's catalyst socket.

## Building, distribution and validation

Use JDK 21 for Gradle/Loom. Release classes target Java 17. `./gradlew build prototypeJar` builds both modules, runs unit tests and creates the separate runtime distributions under their respective `build/libs/` directories.

`./gradlew --continue :core:runGameTest :foci:runGameTest` exercises core alone and core plus expansion. Current evidence is recorded under `validation/0.5.2/`; older validation directories remain historical. Reports distinguish addon tests from the four previously recorded native headless failures (`shortCircuit`, `garbageCollection`, `openSystem`, `closedSystem`). Their causes remain unresolved and they are not established live-server bugs.

`tools/PrototypeJar.java` removes addon fixtures/test entrypoints and audited test-only core overloads. It does not rename, optimize or rewrite retained gameplay methods. `tools/verify_prototypes.py` independently compares retained method bytes/debug data, fields, constant pools, resources and mixin configuration with the full builds. Production checks use the actual prototype JARs with JVM verification enabled. Real microphone playback is outside these automated checks.

0.5.4 adds `NativeMathGameTests`: real native transmitters, receivers and sources provide the reach/travel/severity reference for `NativeRadioAdapter` and `RadioLinkMath`. Coverage includes AM/FM, handheld and tower sender types, native tower power, additive/multiplicative loss, partial incoming power, reception-floor branches, all standard dimensions and coordinates near ±1,000,000. Range-edge probes use an explicit margin outside float rounding uncertainty; power/severity comparisons budget native float ULPs. Cross-world cases separately cover reach passing but travel exhausting the carrier, plus native dimensional OFF. Expansion tests compare the Nether profile with native Foci hooks active and verify antenna-based projections. These fixtures never load the terrain at the radio positions.

Gameplay retains numeric candidate evaluation. Native reference objects exist only in development tests and are stripped from prototype distributions. The exact Simple Radio version constraint cannot identify an upstream binary republished under the same version; validation records the tested dependency hash. A future jar with that version still needs hook compatibility and parity checks.

## 0.5.0 regression results

The core-only Minecraft run passes all **41 addon tests**. The combined installation passes all **64 addon tests**, including focus return traffic, native ON/OFF precedence, direct-crossing rejection, the speaker origin gate and live Nether-setting cache changes. All **51 unit tests** pass (49 core, 2 expansion). Both Minecraft runs also execute seven native tests and retain the same four recorded native failures; separated reports are supplied. Test packets use synthetic encoded bytes rather than live microphone audio.
