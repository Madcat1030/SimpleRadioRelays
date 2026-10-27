# Simple Radio Addons

Build radio infrastructure that carries conversations between distant settlements.

| Mod | Adds | Requires |
| --- | --- | --- |
| Repeaters 0.7.5 | Relays, Repeater Banks, Transponders, Relay Monitors, Frequency Cards and amplifiers | Simple Radio |
| Dimensional Foci 0.7.3 | Focus-controlled links between dimensions and optional Nether reception tweaks | Repeaters with matching Foci API |
| Directional Antenna 0.2.9 | Aimable parabolic receivers and wide-angle arrays | Simple Radio; Repeaters is optional |

## Install

Use Fabric Minecraft **1.20.1**, Java **17+**, Fabric Loader **0.19.0+**, Fabric API, Simple Radio **1.20.1-4.5.7.9** and its dependencies (Lexiconfig and Simple Voice Chat). Install the same chosen addons on server and clients. Replace older copies of those JARs.

Directional works without either other addon. When combined, use Repeaters 0.5.7 or newer. Foci replaces Simple Radio's dimensional behavior, so only install it if you want focus-controlled crossings. Foci 0.7.3+ can stay installed across routine core updates that retain its Foci API number.

## Your first relay

1. Build a Simple Radio core with an attached antenna.
2. Attach a Relay to the core and insert a catalyst.
3. Place a Repeater Bank directly on top of the Relay.
4. Tune a Frequency Card at the Radiosmithing Station and right-click a front slot to insert it.
5. Check for **READY** and a positive **ANT** value, then test nearby radios on that channel.

A bank repeats up to six separate channels. Replace it with a Transponder to translate between channels in Simplex, Duplex or Multicast mode. Remove a card with an empty-hand click on that card; remove catalysts and upgrades by clicking their sockets.

Each relay gives the signal fresh transmit power, limited by its catalyst and accumulated relay decay. Amplifiers improve the antenna contribution; the Ender Amplifier also resets power decay. They do not repair audio already damaged along the way. Hearing a tower does not guarantee your handheld can reach it in reply.

By default, block Transmitters, block Receivers and Relays start at 50% of their base power. Physical antenna strength restores it linearly: 75% at ANT 8 and 100% at ANT 16, plus the normal antenna bonuses throughout. Handhelds are unchanged. Set `antennaDependentBasePower` to `false` to disable this [balance rule](docs/Antenna-Power-Balance.md).

Overworld relay links use coarse, saved terrain profiles. Taller masts can clear intervening hills. Terrain checks for ordinary device links are off unless the server enables Full mode. See [terrain settings](docs/Terrain-and-Elevation.md).

Plug a [Relay Monitor](docs/Relay-Monitor.md) on top instead to survey one channel without forwarding it. Cycle its screen with an empty hand, or use a compass to mark the selected tower.

Craft a book with Simple Radio copper wire to get **Simple Radio Relays**, the in-game guide. Installed addons contribute their own chapters. Saved relay towers continue working while unloaded without force-loading chunks.

See [Directional setup](directional/README.md), [release notes](docs/Release-Notes-0.7.5.md), or [building and testing](docs/Development.md).
