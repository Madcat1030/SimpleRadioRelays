# Simple Radio Repeaters 0.3.0

A Fabric Minecraft 1.20.1 addon for **Simple Radio 1.20.1-4.5.7.9**. Stationary relays amplify radio transmissions across an antenna network. A separate six-card Repeater Bank selects the channels, and both blocks expose their controls and status in the world.

Version 0.3.0 uses native catalysts and count-dependent RF regeneration. Gilded blackstone defaults to 90% and amethyst blocks to 60%. The first relay applies its efficiency immediately. Each subsequent relay uses its own efficiency raised to the total number of relay visits. Audio retains its worst degradation, and wires/channel changes retain the relay count.

This release starts a new catalyst-aware network store without importing earlier saved networks. Load each existing tower's chunk once to register and verify it in 0.3.0. Relay and Bank blocks continue reading their directly stored catalyst/card items through the existing block data format. Banks must be directly above Relays.

## Installation and first tower

Install the addon on the server and every client. Replace the previous addon JAR rather than keeping both versions. Required dependencies are Minecraft 1.20.1, Java 17 or later, Fabric Loader 0.19.0 or later, Fabric API, Simple Radio **1.20.1-4.5.7.9**, its Lexiconfig dependency, and Simple Voice Chat 1.20.1-2.6.0 or later. Development checks use Fabric API 0.92.5+1.20.1, Lexiconfig 1.4.21-epic and Voice Chat 2.6.18.

1. Build a normal Simple Radio antenna on a solid core, or connect the core to an antenna through a native copper-wire extension.
2. Place a **Radio Relay** against a horizontal side of the core. The Relay faces outward, with the core behind it.
3. Put a **Repeater Bank** directly above the Relay. It copies the Relay's facing when placed and plugs into the two top sockets.
4. Tune a Frequency Card in the native radiosmithing station. Select the same frequency and AM/FM modulation as the radios, then press Apply.
5. Right-click a front slot on the Bank with the card to insert it into that slot. A click elsewhere on the front fills the first available slot.
6. Right-click anywhere on the Relay with **gilded blackstone or an amethyst block** to install its catalyst. The catalyst is retained during use.
7. Wait for the screen to show a positive antenna score and `READY`.

The Relay has no container, menu or inventory slots. Its catalyst is a directly installed component. The Bank holds six physical cards and has no inventory screen. Blank cards can be inserted, but they do not activate a channel until tuned. Matching numeric frequencies on AM and FM are separate channels. Duplicate matching cards select the channel once; they do not increase range or duplicate audio.

The Relay requires no additional microphone, speaker, receiver or transmitter block. Normal handhelds and wired stations participate through their existing radio endpoints.

## Crafting recipes

All three recipes are shaped crafting-table recipes and produce one item. Copper wire is Simple Radio's `simpleradio:copper_wire`; glass means a full glass block. The Relay's catalyst is installed separately.

### Radio Relay

| Left | Center | Right |
| --- | --- | --- |
| Iron ingot | Copper wire | Iron ingot |
| Simple Radio receiver | Redstone dust | Simple Radio transmitter |
| Iron ingot | Copper ingot | Iron ingot |

Total: 4 iron ingots, 1 copper wire, 1 copper ingot, 1 redstone dust, 1 receiver and 1 transmitter. File: `src/main/resources/data/simpleradio_repeaters/recipes/relay.json`.

### Repeater Bank

| Left | Center | Right |
| --- | --- | --- |
| Iron ingot | Copper ingot | Iron ingot |
| Glass block | Redstone dust | Glass block |
| Iron ingot | Copper wire | Iron ingot |

Total: 4 iron ingots, 1 copper ingot, 1 copper wire, 2 glass blocks and 1 redstone dust. File: `src/main/resources/data/simpleradio_repeaters/recipes/channel_bank.json`.

### Frequency Card

| Left | Center | Right |
| --- | --- | --- |
| Empty | Paper | Empty |
| Copper ingot | Redstone dust | Copper ingot |
| Empty | Copper wire | Empty |

Total: 1 paper, 2 copper ingots, 1 redstone dust and 1 copper wire. The result is a blank card, which is tuned at the radiosmithing station. File: `src/main/resources/data/simpleradio_repeaters/recipes/frequency_card.json`.

Version 0.2.3 replaces the Relay's top-center copper ingot, the Bank's bottom-center copper ingot and the Card's bottom-center paper with copper wire. Recipe IDs and result counts remain the same.

## In-world controls

| Block and action | Result |
| --- | --- |
| Bank: card click on a front slot | Insert into that slot; an occupied slot leaves the held card alone |
| Bank: card click elsewhere on the front | Insert into the first available slot; a full Bank leaves the held card alone |
| Bank: card click on any other face | No change |
| Bank: empty-hand click on an occupied front card | Return that specific card to the player's inventory |
| Bank: any other empty-hand click | No change |
| Relay: right-click with a registered catalyst | Install one catalyst if the socket is empty |
| Relay: empty-hand right-click | Return the installed catalyst |
| Relay: sneak + empty-hand right-click | Toggle the Relay on or off without removing its catalyst |
| Either block: click with an unsupported item | No configuration change |

Automatic insertion from a front click outside the slots uses top left, top right, middle left, middle right, bottom left, bottom right, skipping occupied slots. Direct slot clicks never replace a card or fall back to another slot. Survival insertion consumes one held card; creative insertion keeps the held card. Returned components go into the player's inventory, or drop nearby if it is full. Breaking the Bank drops its cards and disables its saved channel binding. Breaking the Relay drops its catalyst and removes its network record; the separate Bank and its cards remain in place.

In 0.2.7, each card is three model units tall. Its front-face click band includes the full card width and one additional model unit above it, for both inserting and removing. There is no padding below the card. The three vertical bands are Y=11–15, 6–10 and 1–5. A unit-wide gap between row bands prevents overlap; the code does not choose a nearest card outside these bounds. Existing background-click and non-front-face rules still apply.

One Bank belongs to the Relay immediately below it, and a Relay uses only the Bank immediately above it. Normal placement rejects other positions and copies that Relay's facing. Side and bottom connections are not supported. Removing a Relay leaves the Bank and cards intact but disconnected. A valid saved top binding is retained while entity data is pending; a saved reference to any other position is rejected before world lookup. The Bank and its Relay share the same terrain chunk because their X/Z coordinates are identical.

The screen shows `ANT`, a status code and `LINK`. `ANT` is the native antenna structure score, not a range in blocks. `LINK` counts distinct outgoing links to other eligible towers across the selected channels at relay count 1, using the installed catalyst. Later arrivals may reach fewer neighbors as departure power falls. It does not count players, handhelds or every tower reachable through a chain. Links are directed; one outgoing link does not establish a working return path.

| Status | Meaning |
| --- | --- |
| `READY` | Verification finished; the Relay is configured and has a positive antenna score |
| `NO ANT` | A completed, confirmed scan found no connected antenna |
| `NO CAT` | The required catalyst is missing |
| `NO CARD` | The connected Bank contains no tuned channel |
| `OFF` | The Relay is disabled |
| `SCAN` | Antenna verification is pending without an eligible saved snapshot |
| `CHECK` | The saved Relay remains available while its antenna is rechecked |
| `NO BANK` | No Bank is assigned |
| `WAIT BANK` | The assigned Bank's chunk or entity data is unavailable |

The green Relay LED represents routing eligibility. It can remain green during `CHECK`, because the last verified antenna remains available. The Bank's indicator reports its current loaded connection. Text on occupied card backs shows the stored frequency and modulation; an untuned card reads `BLANK --`.

## Range and audio quality

The addon uses Simple Radio's configured transmission power, reception power, reception floor and attenuation. A handheld must reach an entry tower using its own transmitter strength. Hearing a tower's stronger broadcast does not guarantee that the handheld can transmit back to it.

Departure power is `nominal tower power × current catalyst efficiency ^ relay count`. Count 1 is the first relay, so a 90% catalyst begins at 90%, then 81%, 72.9%, and so on; amethyst begins at 60%, then 36%, 21.6%. Nominal tower power is native transmitter base power plus antenna score times transmitter aptitude.

| Total relay count | 90% catalyst | 60% catalyst |
| ---: | ---: | ---: |
| 1 | 90% | 60% |
| 2 | 81% | 36% |
| 3 | 72.9% | 21.6% |
| 10 | 34.87% | 0.6047% |

The exponent is shared along that signal branch, but each tower uses its own efficiency and maximum. A gilded → amethyst → gilded path emits at 90% → 36% → 72.9% of the respective towers' maxima. This is not multiplication of a carried catalyst-efficiency product.

The router checks actual reach and native attenuation at every reduced-power leg. Some paths stop before reaching their destination. There is no 1% cutoff: a very weak departure can still serve a nearby receiver. The same reduced departure is used for other relays and local receivers. A native receiver/wire/transmitter bridge preserves the count even when its frequency changes; entering another relay consumes the next visit. Fresh microphone/handheld sources begin with zero previous visits. The configured count budget applies across re-entries.

The audio retains the worst interference encountered on its chosen path. Regeneration increases RF reach relative to the received carrier without repairing audio. Final delivery evaluates all viable node/count alternatives, finds the lowest worst interference, admits candidates at most one point above that minimum, and chooses the strongest remaining carrier. Equal power prefers cleaner audio, then fewer visits. The selected source retains its actual interference.
For additive attenuation, the native calculation first rejects a leg if transmission power plus reception power cannot cover distance times the loss factor. It then subtracts the effective distance from transmission power, accounting for the receiver's reception power and floor. Remaining power is compared with the sender's degradation threshold to calculate interference. A leg with no remaining power or complete interference is unavailable.

The towers use native transmitter/receiver RF settings. Catalyst efficiency affects departure power through the formula above; it does not multiply receiver reception gain or directly change audio volume. Server acceptance and efficiency come from Simple Radio's catalyst registry, including data-pack values.

Relays repeat matching frequency/modulation pairs. They do not translate between channels. A receiver on one channel wired through a transmitter on another channel remains a native station that can bridge those channels. The existing A → B → C → B → A arrangement therefore works when C performs that wired bridge and the relevant Banks select both channels.

## A/B/C bridge behavior and delivery selection

A Relay is a routing node, rather than a physical receiver that intercepts every nearby packet first. C's native receiver can be fed by B directly if that is the chosen exit. In 0.2.0, equal audio quality favored fewer hops, so a nearly exhausted B-to-receiver carrier could win over an equally degraded but regenerated carrier from C. A subsequent native wire or the short entry leg into C's return Relay could then exhaust that remaining power. C's received-packet counter rose even though no return packet reached A.

Version 0.2.1 keeps audio quality first, then chooses the stronger remaining carrier. This lets C's local Relay provide RF power for the wired bridge when that delivery sounds equally good. The inherited degradation remains unchanged. A regression reproduces the old failure near the RF cutoff, using real native receiver/transmitter block entities and a copper wire, then verifies successful return without improving the deliberately weak audio.

Version 0.2.4 extends the carrier preference to near-ties. A receiver on C's near side can be slightly closer to B than C's Relay on the far side. Its direct carrier may therefore have marginally less interference but too little remaining RF power to survive the wire and return-network entry. A real native bridge regression reproduces this on 0.2.3, with roughly 2.37 power arriving at the receiver and no return packet. The one-point tolerance admits the slightly noisier local Relay route and verifies successful return without reducing that route's interference.

The quality band is anchored to the cleanest valid delivery across all candidates. For example, if severities are 10, 10.8 and 11.6, the 11.6 route is excluded even though it is within one point of 10.8. This keeps the maximum tradeoff at one point per delivery and makes selection independent of tower iteration order. The constant is `RelayDeliverySelection.QUALITY_TOLERANCE`; it is not a server configuration field.

The native transmitter on the second channel passes through the addon routing code again. It can enter through the local matching Relay at C or through another tower it can reach, such as B. An ordinary wireless C-to-B entry leg is possible; it does not prevent B from amplifying and continuing the relay route. Native receiver-to-transmitter wiring alone does not automatically reset an already-frequenced source to the new antenna's full departure power.

A separate regression covers the normal two-channel A/B/C arrangement with a held transceiver, native C receiver/wire/transmitter and an owner-backed receiving radio. Removing C's cards provides a negative control for the weaker native bridge. Restoring the cards and recreating C's Relay entity confirms that packets still return while its screen reports `CHECK`.

The global received counter records managed receiver-handler arrivals. A rising total may reflect only C's incoming receiver; it is not proof that the second channel returned to A or that a speaker played the packet.

## Persistence and chunk restoration

The server saves verified stationary Relay records independently of loaded block entities. A record includes the Relay identity, dimension, position, selected channels, enabled state, catalyst item identity, antenna score, verification state, antenna dependency footprint, and the Bank's position and identity. It can continue forwarding through unloaded towers when `persistentUnloadedTowers` is enabled. Routing and validation do not request missing chunks.

On reload, a Relay restores the authoritative saved antenna score and adopts any Bank changes already observed by the server. Its local cache stores card configuration, not physical card items. The actual ItemStacks exist only in the Bank. A loaded Bank can update the saved channels even while the Relay's entity is unloaded. An older Relay NBT cache cannot resurrect cards that were removed from the Bank.

Antenna verification preserves the wired-core restoration behavior from 0.1.1. It waits for the previous dependency chunks and their entity data, checks that insulator routers and wire endpoints have restored, and gives the native network time to settle. Loaded, settled Relays also scan on the same `gameTime % 200 == 0` boundary used by native receivers and transmitters, recovering antenna changes whose notification was missed. Pending verification retries every 200 ticks, approximately ten seconds at normal tick rate. Replacing a verified antenna with zero strength requires two complete zero scans at least 200 ticks apart. A temporary zero or incomplete scan leaves the previous snapshot available. Repeated dirty notifications are coalesced while a check is already pending, so building does not keep restarting the 20-tick settling window. A real disconnection is eventually confirmed without an enable toggle.

Ghost checks operate independently of antenna scans. At chunk load, startup and manual refresh, the manager checks saved component positions in already-loaded chunks. A missing Relay block or changed Relay identity removes its record. A missing Bank or changed Bank identity clears the Relay's channels and Bank binding while retaining the antenna metadata. Both results are persisted and invalidate cached routes. A matching block awaiting its entity data is retried rather than treated as absent.

The Bank is directly above the Relay, so both positions share one terrain chunk. The manager still validates each component independently: a pending Relay entity does not prevent checking a missing/replaced Bank in that chunk. Unloaded antenna geometry remains a verified snapshot until its dependencies become available for a new scan.

The 0.3 network is the overworld's `data/simpleradio_repeaters_relays_v3.dat`, schema 3. Earlier network files are not imported. New records store `CatalystItem` as an item identifier instead of a boolean. Relay NBT retains its installed ItemStack; the Bank owns the physical card ItemStacks.

## Commands and configuration

| Command | Purpose |
| --- | --- |
| `/radiorepeaters status` | Show configured, ready, loaded and saved Relay counts and packet counters |
| `/radiorepeaters list` | Show positions, antenna scores and channels; requires operator level 2 |
| `/radiorepeaters refresh` | Check saved components in loaded chunks and recheck loaded antennas; requires operator level 2 |

The first server launch creates `config/simpleradio-repeaters.json`:

```json
{
  "maxRelayHops": 64,
  "persistentUnloadedTowers": true,
  "requireCatalyst": true
}
```

Restart after editing these settings. Disabling persistence makes unloaded towers unavailable. Disabling the catalyst requirement permits a Relay to operate without an installed catalyst. Native Simple Radio settings continue to control radio reach and degradation. Relay paths stay within one dimension; existing direct cross-dimension behavior remains governed by the base mod.

For a voice check, start with two players who cannot reach each other's handhelds directly but can both reach one Relay. Test both directions. Add a second tower and repeat with the middle tower's chunk unloaded. For a wired core, unload and reload it and allow the verification retry interval before changing its configuration.

## Implementation: the split hardware

### RelayBlock and RelayBlockEntity

`RelayBlock` handles placement against the antenna core, facing, direct catalyst clicks, the sneak toggle, and component drops. It creates a `RelayBlockEntity` and has no menu factory. Its `onRemove` retires the network record; normal chunk unload leaves the verified record intact.

`RelayBlockEntity` owns the Relay UUID, enabled flag, direct native-catalyst ItemStack, native antenna score, verification state, assigned `CardBankRef`, cached card configuration and display values. It implements native `Frequencing`, allowing antenna dirty notifications, but does not implement `Container` or a menu provider. Catalyst installation validates the native registry, copies one held item and consumes one in survival. Settings snapshots include item identity so switching between two valid catalysts is observable. An installed item remains removable/drop-safe if a data pack later stops recognizing it. Removal returns that same installed component.

`refreshBank` rejects any saved binding outside the position immediately above the Relay before checking availability. It preserves a valid top binding while its entity data is pending, otherwise reads only that top position and copies its card configuration. Relay NBT loading also discards invalid bindings and their cached channels. `setChanged` compares settings snapshots before requesting antenna verification: actual card/configuration changes, catalyst insertion and enabling matter; equivalent writes do not. Removing a catalyst or disabling a Relay immediately updates routing eligibility. Reload adopts the saved controller/channel snapshot before publishing local settings, preventing stale local NBT from restoring a removed component or card.

The tick registers the loaded instance and resolves a copied UUID before adopting authoritative saved channels or publishing settings. Settings updates also require the saved dimension and position to match, protecting the original from pre-tick placement notifications. The tick restores the verified antenna cache, performs pending scans, schedules the 200-tick periodic fallback and refreshes display values. `markDirty` starts a settling window only when moving from a settled state to a pending check. A burst of antenna notifications shares that window. Periodic checks do not reset an ongoing zero-strength confirmation. Screen state and link count are checked once per second. A client update is sent when display values or installed components change, rather than every tick. Standard block-entity update packets and chunk update tags carry the same render data.

### ChannelBankBlock and ChannelBankBlockEntity

`ChannelBankBlock` contains the horizontal facing and connected indicator properties. Its selection shape follows the shallow rack. Placement requires a Relay directly below and copies that Relay's facing. Its item tooltip explains the top plug connection. Its `use` handler rejects non-front hits before considering an item transfer. Front hits are transformed into the model's north-facing coordinates. With a card, a slot hit targets that slot; only hits outside the slot rectangles use `firstEmptySlot`. Occupied targets reject insertion without replacing the card or selecting another slot. With an empty hand, only an occupied slot hit removes a card. Unsupported items, empty slots and front background clicks leave configuration unchanged. Client prediction checks the same face and occupied/free conditions as the server, while the server owns every item transfer.

`ChannelBankBlockEntity` is the only card container. It has exactly six one-item slots and a Bank UUID. Its owner position is computed directly below the Bank; obsolete saved owner-position hints are ignored. `insert(slot, held, creative)` validates the requested slot and card before copying one item and consuming it in survival. The Container interface supports ordinary item handling and drops; it does not create a GUI. `CardState` snapshots distinguish physical slot presence and channel tuning from no-op writes. A real card change saves and syncs the Bank, then informs the loaded Relay or updates its saved record if the Relay is unloaded.

The Bank rechecks attachment every ten ticks or after a relevant neighbor change. It never searches side or upper positions for another Relay. Its update tag includes the actual card ItemStacks, so the client renderer can show occupied slots and the correct labels after insertion, retuning or chunk reload.

### BankConnection, CardState and CardBankRef

`BankConnection` provides loaded-chunk-only block and entity lookups. It uses `getChunkNow` and `EntityCreationType.CHECK`, and waits for entity data rather than forcing a chunk or manufacturing a new component identity. Its `isMounted` predicate requires the Bank to be exactly one block above the Relay, and `localHit` transforms world hit positions back into the model's NORTH-facing coordinate system. `slotX`, `slotY` and `cardAt` define the shared six-slot layout used by interaction and text placement.

`CardState` is an immutable value containing card presence, frequency and modulation. Blank cards remain present with no active channel. It serializes the Relay's configuration cache without storing item inventory. `CardBankRef` pairs an immutable block position with the Bank UUID, allowing a replacement Bank at the same coordinates to be distinguished from the original.

### TowerManager, TowerRecord and TowerSavedData

`TowerRecord` is the immutable routing snapshot. The catalyst item identifier and optional Bank reference are persisted alongside the RF fields. `efficiency()` resolves the native registry without reading terrain; a catalyst-free tower uses factor 1 only when the config makes catalysts optional. The record constructor clears a non-top Bank reference and its channels. This invariant applies to saved-data restoration, publication, reconciliation and controller edits without querying chunks. Changing cards or catalyst/settings creates a new record while retaining its verified antenna score and footprint.

`TowerManager` owns the per-server loaded-instance map, saved records and per-channel graph cache. Updating a record marks saved data dirty, maintains the component-position index and clears cached graphs. Network construction caches distances and numeric directed-link rows by sender and relay count. Radio/catalyst/config fingerprints are checked before reusing a network, and successful data-pack reloads invalidate it explicitly. Pending chunk reconciliation checks Relay and Bank block types and UUIDs only in chunks already available. A pending Relay entity does not prevent checking the Bank block or identity in that same chunk. `controllerChanged` lets a loaded Bank update its unloaded Relay's channel snapshot. `controllerRemovedAt` clears the binding during an ordinary Bank break; the same clearing logic handles a chunk edit that bypasses block callbacks. Removing a loaded instance only publishes settings if that instance is still the registered owner, so an obsolete entity cannot overwrite a newer saved state.

`TowerSavedData` stores the deterministic list of records in `simpleradio_repeaters_relays_v3`, with schema 3. It does not migrate earlier saved networks. Clearing an invalid saved binding marks the data dirty so the correction persists. Damaged individual records are skipped with a logged warning.

## Implementation: models and client rendering

All supplied block textures are 16×16, using the original placeholder palette. The imported main cubes retain the supplied whole-unit geometry and UVs. The dark strip is a flat overlay with a small depth offset to prevent flickering against the card face. The base Relay includes its housing, display bezel and recessed top sockets. The base Bank includes its rack, slot lips and connector pins. Inserted cards and the catalyst are absent from these base JSON models.

`RepeatersClient` registers both block-entity renderers and the six conditional card models. `RelayRenderer` renders the installed native block into the original catalyst volume, X/Y=2–6 and Z=−2–1. Registered non-block items use the item renderer. Gilded blackstone and amethyst therefore use their actual native textures and honor resource packs. The obsolete fixed catalyst model/bitmap are removed. The renderer also writes the three live screen lines. `ChannelBankRenderer` draws occupied card models and one combined frequency/modulation label per card. The card geometry is based on the supplied Blockbench JSON, with its height restored to three units and its vertical UVs extended to match. The base export excludes all cards. Each conditional card model includes a four-unit dark strip centered in its middle row, using an existing dark texture pixel. Font fitting uses a 3.75-by-.8-unit area centered at X=12/4 and Y=12.5/7.5/2.5, keeping light text inside the strip even for long frequencies. Neither renderer performs server routing or antenna lookups.

In 0.2.5, `ChannelBankBlockEntity.displayCards()` lazily builds an immutable six-slot display snapshot containing presence and the finished labels. Version 0.2.6 also caches the combined single-line label, avoiding per-frame string concatenation. The renderer reuses it on later frames without reading ItemStack tags, creating temporary tuning tags or parsing channel validity again. Actual inventory changes and incoming chunk/update NBT invalidate it; no-op inventory notifications retain it. No-update extraction also invalidates local display data without changing its existing notification semantics. The cache is transient, belongs to the block entity and contains no font or model resources. It is not saved or sent over the network. Its plain-string labels need no special resource-reload invalidation; Minecraft still supplies the current baked models and font at draw time.

`DeviceRendering` rotates the NORTH-facing partial models around the block center and places text on their front surfaces. Text uses Minecraft's font. The screen retains its existing placement. `centeredText` fits Bank labels to both width and height and centers them on both axes after scaling. Each label's width is calculated once per draw and reused for fitting and centering. A small depth offset keeps labels from flickering against their surfaces. Text is full bright; physical components receive normal world lighting. Inactive/active and disconnected/connected variants share geometry and change their indicator pixels.

The Blockbench master files retain `base`, `inserted_catalyst` and separate `inserted_card_1` … `inserted_card_6` groups. Export the base and each card group into its runtime model without recentering it. The catalyst group is now a socket placement reference; catalyst rendering uses Java transforms and the actual installed item. The [model editing guide](../art/Model-Editing-Guide.md) lists the exact coordinates, file mappings, hit regions and Java text positions to update when modifying the artwork. `art/make_assets.py` reproducibly generates the original supplied assets and will overwrite hand edits if rerun.

The optimization removes repeated parsing and allocation. Version 0.2.6 uses six model submissions and six text lines for a full visible Bank; the label strips are part of those baked card models. Dense-room FPS and the resulting performance benefit have not been benchmarked; the patch does not claim a particular FPS gain.

## Implementation: existing radio and antenna modules

The antenna scanner and settling policy retain their existing behavior. Version 0.3 changes RF regeneration and traversal while preserving stream ownership, worst audio degradation and one-copy delivery.

| Module | Responsibility |
| --- | --- |
| `SimpleRadioRepeaters` | Register the Relay, six-card Bank, Frequency Card, creative tab, server lifecycle hooks and commands |
| `RepeatersConfig` | Read and validate hop limit, persistence and catalyst settings |
| `FrequencyCardItem` | Participate in native radiosmithing, preserve frequency/modulation tags and show tuning tooltips |
| `ChannelKey` | Represent an exact frequency/modulation pair and keep AM/FM distinct |
| `AntennaScanner` | Run native antenna/extension geometry through a guarded accessor, track dependency positions and detect pending wire/router data |
| `AntennaVerification` | Enforce settling time, retry intervals and confirmation before replacing a verified antenna with zero |
| `NativeRadioAdapter` | Read native power/reception settings, calculate counted departure power and evaluate each physical leg |
| `RelayPower` | Compute nominal output times efficiency raised to total relay count, with no percentage cutoff |
| `RelayCatalysts` | Resolve native registered catalysts, efficiencies, configuration fingerprints and the server-supplied client catalog |
| `RadioLinkMath` | Compute wireless eligibility, remaining power and interference using the target native rules |
| `RelayGraph` | Propagate the lowest worst interference for each tower/count state, using count-dependent links; retain all count alternatives for final delivery |
| `RelayRouter` | Gather eligible direct and repeated deliveries, preserve wired fan-out/custom criteria, and pass one selected source to each receiver |
| `RelayDeliverySelection` | Anchor the one-point quality band to the cleanest candidate and select the strongest carrier without altering the signal |
| `RelayFrame` | Share emitter/receiver deduplication sets across copies of one radio frame |
| `RelaySourceState` | Expose frame identity, inherited worst interference and the per-copy relay count |
| `RadioRouterMixin` | Enter addon routing from native transmitter dispatch when a usable relay network exists |
| `RadioSourceMixin` | Carry frame, quality and independent count fields through source copies; include inherited interference in severity calculations |
| `RadioReceiverMixin` | After native acceptance and power checks, mark successful delivery and reject duplicates before activity compilation and downstream routing |

Encoded voice bytes, source ownership, original ownership and activity survive forwarding. The addon does not decode and re-encode each relay hop. The live receiver and speaker still use the native receiving pipeline. A rejected receiver copy does not reserve the frame or increment delivery statistics. The acceptance predicate is evaluated once by native code before the dedupe hook, which runs immediately before `compileActivity`. Frame deduplication prevents loops and repeated playback; directed graph edges handle antennas with unequal outgoing and incoming reach.

## Implementation: catalyst state and counted routing

`RelayCatalysts` resolves an installed item through the native `CatalystRegistry`. It rejects unregistered, negative or non-finite efficiency data. The default registry entries are `minecraft:gilded_blackstone` at .9 and `minecraft:amethyst_block` at .6. The server sends accepted item identifiers at join and after data-pack reload; clients use that list for right-click prediction. Disconnect clears the client list. Numeric efficiencies remain server-owned.

`RelayPower.departure(maximum, efficiency, count)` is pure numeric code. It uses count 1 immediately, retains positive results below 1%, and returns no usable power for invalid numeric inputs. The formula uses the current tower's efficiency at the total count. No extra call to native catalyst power adjustment is inserted into this regeneration step.

`RelayGraph.alternatives(entrances, previousRelays)` starts all viable entrances at `previousRelays + 1`. A dynamic-programming table stores the lowest worst interference for each tower/count pair. Expanding a state uses the sender's counted departure power to obtain its directed link row. It updates a destination at the next count only if native reach/attenuation succeeds. Counts are bounded by `maxRelayHops`, so cycles terminate. The table is O(V × H); propagation is O(H × (V + E)) after cached numeric link rows are available. It merges entrances in one pass instead of caching a separate path history for each entry.

The graph returns all viable count alternatives for each exit. Collapsing them to one cleanest path would discard shorter routes that retain enough power for a distant final receiver. `RelayRouter` evaluates each alternative's final leg with the same reduced output used for internal links. The existing global-minimum quality band then selects one candidate within that transmitter dispatch.

A chosen repeated source gets its total count before entering the native receiver. `RadioSourceMixin.copy` carries the primitive count alongside shared frame identity and inherited quality. Wires and frequency changes keep it. Branch copies can diverge in count without changing one another. Shared frame deduplication remains responsible for loop/replay suppression; the count is not stored in that shared object.

## Build and validation

Use JDK 21 for Gradle/Loom; output bytecode targets Java 17. On Windows use `gradlew.bat`.

```sh
./gradlew build
./gradlew runGameTest
```

The installable JAR is under `build/libs`; a `-sources.jar` is source code. Version 0.3.0 passes 30 unit tests and 34 addon Minecraft tests. Reports are under `validation/0.3.0/`, with a current summary at `validation/build-verification.json`.

New coverage checks first-relay efficiency, gilded/amethyst sequences, mixed catalysts, live sub-1% reception, a connected-looking chain that becomes unreachable at a later count, shorter/noisier final-leg alternatives, a shared count budget after re-entry, actual native receiver output, branch count isolation, amethyst socket/save/client tags, valid catalyst swaps, optional-catalyst serialization, registry changes with unloaded towers, and client catalog filtering. Existing regressions cover top mounting, copy isolation, missing/replaced components, card controls, antenna restoration and native wire bridges.

The near-tie carrier fixtures use an explicit neutral test catalyst so the comparison remains independent of RF decay. The counted-loss real copper-wire bridge uses gilded blackstone and a larger A mast: its old one-piece A mast no longer had enough return reception under the new budget. The bridge asserts that the actual wire and channel change preserve count and subsequent relays increase it.

The combined run also contains seven native Simple Radio tests. The same four failures seen in the earlier addon-disabled headless control remain: `shortCircuit`, `garbageCollection`, `openSystem`, `closedSystem`. Their cause is unresolved and they are not established live-server bugs. Addon/native XML reports are separated. Game tests use synthetic encoded bytes, not captured microphones.

The native client review renders both catalysts and mounted Banks with Minecraft's actual models, textures, fonts and production renderers. Its temporary QA mod is separate from the released addon. The previous card geometry remains three units tall with one extra model unit of click padding above only. Real multiplayer voice playback and a dedicated-client join/reload session have not been exercised in this run.
