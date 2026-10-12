# Simple Radio Repeaters 0.1.2

A Fabric 1.20.1 addon for Simple Radio, adding a nine-channel Repeater Bank and Frequency Cards. Configured stationary towers continue relaying through saved records when their chunks unload. The addon does not keep tower chunks loaded.

## Full documentation

Read the [gameplay and implementation guide](docs/Simple-Radio-Repeaters-Documentation.md) for setup, radio-power and audio-quality rules, persistence, and a detailed reference to every code module. A [PDF edition](docs/Simple-Radio-Repeaters-0.1.2-Documentation.pdf) is included for reading and printing.

## Installation

Replace the previous addon jar with `simple-radio-repeaters-fabric-1.20.1-0.1.2.jar` in the `mods` folder on the server and every client. Keep only one version of the addon installed. Existing banks, cards and saved network records are retained.

Version 0.1.2 prevents empty menu clicks, rejected items, identical inventory writes, and unchanged enable settings from requesting antenna verification. Actual card additions/removals or tuning changes, catalyst insertion, and switching on request verification. Removing the catalyst or switching off updates eligibility and the display immediately without starting a new antenna scan. Antenna changes, chunk restoration, and explicit refresh still request verification. The bank continues to use the existing nine-slot GUI; the separate six-card model starter is a design project for a future in-world interface.

Required environment:

- Minecraft **1.20.1**, Fabric Loader **0.19.0 or later**, Java **17 or later**.
- **Simple Radio 1.20.1-4.5.7.9**, exactly. This build does not support Simple Radio 3.4.6.
- Simple Voice Chat **1.20.1-2.6.0 or later**. Development checks use 2.6.18.
- Fabric API, and Simple Radio's Lexiconfig dependency **1.4.x**. Development checks use Fabric API 0.92.5+1.20.1 and Lexiconfig 1.4.21-epic.

Keep the main mods installed. No additional voice-chat port, plugin, resource pack or chunk loader is required.

[Simple Radio reference release](https://modrinth.com/mod/simple-radio/version/f9TIjf7X)

## First tower

1. Build the usual Simple Radio antenna on a solid core block.
2. Place the Repeater Bank against a horizontal side of that core. Clicking the core's side makes the bank face outward, with the core behind it.
3. Craft a Frequency Card and put it in the existing radiosmithing station. Select the same frequency and AM/FM modulation as your handhelds, then press Apply.
4. Open the bank and put the card in one of the nine channel slots.
5. Put one **gilded blackstone** in the catalyst slot. It is not consumed while relaying.
6. Leave the bank Enabled. The panel should say **Ready to relay**, show a positive antenna score and light the bank's front indicator green.

Blank cards stay inactive until Apply is pressed. Matching numeric frequencies on AM and FM are separate channels. Duplicate matching cards count as one channel and do not add range or extra copies of the voice.

The repeater needs no microphone, speaker, receiver or transmitter block attached separately. Existing microphone/transmitter and receiver/speaker stations can participate through their normal radios.

## More towers

Put matching cards in the other banks. Each tower only needs to reach its neighbours under Simple Radio's radio rules; the first and last towers do not need to reach one another. Chains, branches and rings are supported. The addon chooses one direct or repeated delivery per receiver and prevents circulating copies of the same frame.

Use a second card in each relevant bank for another channel. A single antenna can serve up to nine distinct frequency/modulation pairs in one bank.

A handheld must successfully transmit to an entry tower. Hearing the tower's stronger outgoing broadcast does not guarantee a return link. Antenna score is a structure-strength value, not a universal block-range display. The addon uses the main mod's configured power, attenuation and reception settings.

Weak incoming audio stays weak after amplification; a repeater refreshes RF power while retaining the worst interference on the selected path.

## Unloaded towers

A verified bank is saved with its position, channels, enabled state, catalyst presence and antenna score. It remains part of the network when nobody is nearby and its chunk unloads. The records survive a server restart without loading every tower.

Loading the bank again starts verification while retaining its last verified configuration. The addon gives native insulators and copper-wire connections time to restore, and waits for the previous antenna dependencies and their entity data to become available. A temporary zero-strength result cannot replace a verified antenna immediately. Pending verification retries automatically every 200 ticks (about ten seconds); a confirmed zero requires two completed scans at least 200 ticks apart. A valid restored connection refreshes the saved strength without an enable toggle.

Destroying or disabling the bank removes it from routing. While the bank is unloaded, its verified antenna snapshot remains authoritative. Once loaded, native antenna dirty notifications and manual refresh request verification. A genuinely missing antenna or extension stops relaying after confirmation. The addon does not force-load chunks and does not change the base mod's wire-cut or antenna-base dirty-notification behaviour.

The broad neighbouring-block invalidation watcher has been removed. Nearby door, crop and redstone changes no longer invalidate unloaded tower records.

Actual microphones, physical speakers and player radios still use the main mod's live endpoints. This release supports stationary installations and keeps relay networks within one dimension. Existing direct cross-dimension behavior is left to the main mod's settings.

## Useful checks

- `/radiorepeaters status` — configured, ready, loaded and saved tower counts; network packet counters.
- `/radiorepeaters list` — tower positions, saved/live status, antenna scores and channels. Requires operator level 2.
- `/radiorepeaters refresh` — recheck currently loaded banks without loading remote ones. Requires operator level 2.

For the first voice test, use two players too far apart to hear each other's handhelds directly, with both able to reach one tower. Check both directions. Then test a tower chain and repeat after leaving the middle towers' chunks unloaded.

If the bank says **No connected antenna**, check the core behind the bank and the antenna above that core or its copper-wire extension. **Relaying; checking antenna** means the verified snapshot remains active during restoration. **Waiting for antenna connection** means verification is pending and no eligible snapshot is currently available. If voice passes directly but fails through towers, check both frequency and modulation, catalyst, Enabled state and handheld uplink distance.

## Server configuration

The first launch creates `config/simpleradio-repeaters.json`:

```json
{
  "maxRelayHops": 64,
  "persistentUnloadedTowers": true,
  "requireCatalyst": true
}
```

Restart after changing these settings. `persistentUnloadedTowers: false` makes unloaded towers unavailable. Turning off the catalyst requirement permits banks without gilded blackstone. Main-mod power settings still determine radio reach and quality. The old `antennaRefreshTicks` setting is replaced by the fixed pending-verification interval of 200 ticks.

Saved network data is part of the world save, in the overworld's `data/simpleradio_repeaters_towers.dat`. Normal bank inventory/settings are also saved in their block entities.

## Build from source

Use JDK **21** for the Gradle/Loom toolchain. The addon compiles to Java **17** bytecode.

```sh
./gradlew build
./gradlew runGameTest
```

On Windows, use `gradlew.bat`. Output jars are under `build/libs`; the file ending in `-sources.jar` is source code, not the installable addon. The included game tests use Fabric's headless test environment and synthetic packet data; they do not capture microphones.

## Verification

- Verification results for this revision are recorded in `validation/build-verification.json`.
- Routing tests cover range, directed links, branches, rings, hop limits, interference and duplicate-frame handling. Verification-policy tests cover settling, transient zero results, retry timing, restoration and confirmed removal.
- Minecraft game tests cover native card tuning, native antenna scanning and bank inventory, saved-record serialization, saved-tower packet routing, a real copper-wire extension that restores after bank reload, confirmed wire removal, preservation of snapshots during partial loading without forcing chunks, and real menu clicks that leave configuration unchanged. The menu regression also checks real card/catalyst/power changes and in-place card retuning.
- The seven included Simple Radio game tests were also run with the addon disabled in the preceding controls. The same three passed and the same four failed with and without the addon: `shortCircuit`, `garbageCollection`, `openSystem` and `closedSystem`. The same four fail in this revision. The combined game-test command therefore exits with a failure even though all seven addon tests pass. Reports are under `run/gametest/reports` and `run/baseline/reports`; `./gradlew runBaseGameTest` runs the comparison.

The user's in-game voice tests established the working A/B/C relay arrangement and confirmed the 0.1.1 wired-core reload correction. Version 0.1.2 changes when inventory/settings changes request antenna verification; it does not change radio power, interference, channel bridging or playback. The wired-core reload correction remains covered by its Minecraft regression test. `/radiorepeaters status` shows whether the bank's record remains eligible.

Licensed GPL-3.0-only. The source project includes its license; required third-party mods are distributed separately by their authors.
