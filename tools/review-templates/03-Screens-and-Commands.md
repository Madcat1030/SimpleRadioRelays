# Screens and commands — 0.9r1

These strings are currently assembled in code. Edit the wording here and retain the source names; no Java editing is needed. Braces show dynamic values, not text printed literally. Exact source fragments, including technical diagnostics, are also in `Code-Literals.json`.

## Relay front screen

Source: `core/.../client/RelayRenderer.java`.

```text
ANT {raw mast score, rounded}
{status}
LINK {peer count}
```

Statuses, in internal ID order:

| ID | Label | Meaning |
| --- | --- | --- |
| 0 | READY | Hardware is ready |
| 1 | NO ANT | No verified antenna |
| 2 | NO CAT | No valid catalyst |
| 3 | NO CARD | No usable forwarding card pair/channel |
| 4 | OFF | Relay is disabled |
| 5 | SCAN | Verification/mapping pending without an available working result |
| 6 | CHECK | Verification/mapping pending while an earlier result is kept |
| 7 | NO BANK | No top module |
| 8 | WAIT BANK | Waiting for top-module block entity |
| 9 | MONITOR | A passive monitor is attached |
| fallback | WAIT | Unrecognized display status |

The screen now uses raw ANT. Amplifier and elevation bonuses appear as effective strength on the monitor.

## Card backs

Source: `core/.../block/ChannelBankBlockEntity.java`.

- Tuned: `{frequency} {AM/FM}`, for example `000.01 AM`.
- Untuned/invalid: `BLANK --`.

The value is shared by the Bank, Transponder and Monitor renderers. Card-item tooltip tuning is also generated from its stored frequency and modulation.

## Transponder mode panel

Source: `core/.../client/TransponderRenderer.java`.

- Simplex: `→`.
- Duplex: `→` and `←`, on separate lines.
- Multicast: `*`.

Mode names and explanatory prose are in the language/manual review.

## Relay Monitor

Source: `core/.../block/RelayMonitorBlockEntity.java`; direction labels are in `monitor/RelaySurvey.java`.

No tuned card:

```text
Insert tuned card
for Relay diagnostics
```

No Relay:

```text
RELAY MONITOR
Attach atop a Relay
```

Hardware not ready:

```text
RELAY MONITOR
{frequency} {AM/FM}
{RELAY OFF / INSERT CATALYST / NO ANTENNA / CHECK ANTENNA}
```

Overview, eight lines:

```text
TX OVERVIEW  {frequency} {AM/FM}
{dimension}
X {x} Y {y} Z {z}
ANT {raw score, one decimal} (mast)
Effective {adjusted score, one decimal}
TX {count}   RX {count}
Other nodes {count}
{Nominal direct links / Surveying {count} paths}
```

Heard target:

```text
{frequency} {AM/FM}  {TX/RX or RX only}
{dimension}
X {x} Y {y} Z {z}
Radio distance {rounded radio blocks} blocks
Terrain +{extra distance, one decimal}%
Screen {page}/{total}
```

TX-only target:

```text
{frequency} {AM/FM}  TX only
{dimension}
{16-point bearing}
~{rounded radio blocks} blocks
Screen {page}/{total}
```

Dimension names: `Overworld`, `Nether`, `The End`; unknown dimensions use their registry ID. Bearings: `N, NNE, NE, ENE, E, ESE, SE, SSE, S, SSW, SW, WSW, W, WNW, NW, NNW`; coincident horizontal positions show `LOCAL`.

Coordinates are the antenna base. Radio distance includes propagation/dimension scaling and excludes the separately shown terrain penalty. Compass feedback messages are translated keys in `01-Names-and-Tooltips.md`.

## Handbook graphics

Source: `core/.../client/HandbookScreen.java`.

Recipe arrow: `→`. Item names/tooltips come from the recipe ingredients' owning mods. The recipe-unavailable message, navigation labels, title and prose are in the language/manual review.

## Core commands

Sources: `SimpleRadioRepeaters.java`, `network/TowerManager.java`, `radio/RelayPerformance.java`, `terrain/TerrainCommands.java` under the core package.

`/radiorepeaters status`:

```text
Repeater network is starting.
Repeaters: {configured} configured; {ready} ready ({loaded} loaded, {saved} saved). Forwarded {frames} frames; delivered {packets} receiver packets.
```

`/radiorepeaters list` is operator-only. It prints the summary, then each node's dimension/box coordinates, loaded/saved/inactive state, adjusted antenna value and antenna base, tip or `unverified`, catalyst and efficiency, attachment, Bank/Transponder mode and channels. The precise fragments are in the literal inventory; UUIDs/coordinates/channels are generated data.

`/radiorepeaters diagnose`:

```text
Relay diagnostics written to logs/latest.log. No radios or relay settings were changed.
```

Its detailed RF diagnostic text goes to the log; it is inventoried with source locations in `Code-Literals.json`.

`/radiorepeaters refresh`:

```text
Checking saved relays and card banks in loaded chunks and refreshing loaded antennas.
```

`/radiorepeaters test start`, `start {seconds}`, `stop`:

```text
Radio performance test started; use /radiorepeaters test stop to save it.
Radio performance test started for {seconds} seconds; it will save automatically.
Radio performance test saved to {path}
Radio performance test complete: {path}
No radio performance test is running.
A radio performance test is already running. Use /radiorepeaters test stop first.
Capture stopped, but the report could not be written. See logs/latest.log.
```

`/radiorepeaters terrain`, `off`, `relay_mapping`, `full`, `reload`, `clear`:

```text
Overworld terrain mode={mode}; {live statistics}
Could not reload simpleradio-repeaters.json. Current settings were kept; see latest.log for details.
Remapping relay terrain. Existing links keep their previous result until replacement samples are ready.
Could not save terrain settings: {error}
```

The invalid-curve fallback warning is a normal translation key in the text review. A malformed JSON load still fails; a recovered curve warns while other valid settings load.

## Foci command

Source: `foci/.../DimensionalFoci.java`.

`/radiofoci config netherTweaks [true|false]`:

```text
netherTweaks: {true/false}; dimensional access is always focus-only while this addon is installed.
netherTweaks: {true/false}. Saved.
Could not save foci settings; previous value retained.
```

## Directional resource-pack labels

Source: `directional/.../client/DirectionalClient.java`.

- Simple Radio: Stepped Mesh Receiver
- Simple Radio: Refined Parabolic Receiver
- Simple Radio: Octagonal Parabolic Receiver

The alternate packs' descriptions and model resources are in `editable/directional/resources/resourcepacks/`. Aiming feedback is translated in `01-Names-and-Tooltips.md`.

Proposed wording / notes:

